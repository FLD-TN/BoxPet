package com.dex.boxpet.ability;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.api.event.PetAbilityDamageEvent;
import com.dex.boxpet.config.Ability;
import com.dex.boxpet.pet.ActivePet;
import com.dex.boxpet.pet.Effects;
import com.dex.boxpet.pet.PetVisual;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.damage.DamageType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Pet abilities (spec 6): combat skills on the owner's target, support buffs on the owner. Damage is dealt as the
 * owner through MythicLib ({@code SKILL} + {@code MAGIC}), never by a real entity.
 */
public final class AbilityService {

    /** Wait before trying again when the target was out of range, hidden or protected. */
    private static final int RETRY_TICKS = 10;

    /** True while BoxPet deals damage: its own listeners ignore those events. */
    private static boolean dealing;

    private record Mark(UUID owner, double bonus, long until) {
    }

    private final BoxPetPlugin plugin;
    private final List<Vfx> vfx = new ArrayList<>();
    private final Map<UUID, Mark> marks = new HashMap<>();
    /** Pet id -> {ability ready at, shield ready at} (epoch ms): kept when the pet is put away (no reset by re-summon). */
    private final Map<UUID, long[]> cooldowns = new HashMap<>();
    private int ticks;
    private BukkitTask task;

    public AbilityService(BoxPetPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean dealing() {
        return dealing;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickVfx, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Vfx v : vfx) v.remove();
        vfx.clear();
        marks.clear();
        for (Player pl : Bukkit.getOnlinePlayers()) clearBuffs(pl);
        cooldowns.clear();
    }

    // ------------------------------------------------------------------ cooldowns kept per pet

    /** The pet leaves (dismiss, logout): remember when its ability and shield are ready again. */
    public void remember(ActivePet p) {
        long now = System.currentTimeMillis();
        long ability = now + Math.max(0, p.cooldown) * 50L;
        long shield = Math.max(p.shieldReadyAt, 0);
        if (ability <= now && shield <= now) cooldowns.remove(p.data.id);
        else cooldowns.put(p.data.id, new long[]{ability, shield});
    }

    /** The pet comes back: same cooldowns as when it left. */
    public void restore(ActivePet p) {
        long[] c = cooldowns.remove(p.data.id);
        if (c == null) return;
        long now = System.currentTimeMillis();
        p.cooldown = (int) Math.max(0, (c[0] - now) / 50L);
        p.shieldReadyAt = c[1];
    }

    // ------------------------------------------------------------------ per pet tick

    public void tick(ActivePet p) {
        Ability a = p.ability;
        if (a == null || a.type == Ability.Type.NONE || a.type == Ability.Type.BOOST) return;
        if (p.cooldown > 0) p.cooldown--;
        if (p.abilityPaused()) return;
        Player o = p.owner;
        switch (a.type) {
            case HEAL -> {
                if (p.cooldown > 0) return;
                AttributeInstance max = o.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (max == null || o.isDead()) return;
                double pct = o.getHealth() / max.getValue() * 100;
                if (o.getHealth() >= max.getValue() || pct > a.healBelowPercent) return;
                double amount = a.heal.at(p.data.level, p.rarity.statMultiplier());
                if (a.healPercent) amount = max.getValue() * amount / 100.0;
                o.setHealth(Math.min(max.getValue(), o.getHealth() + amount));
                // 0.8.0: a heal may also give a shield (Thần Ra)
                double hearts = a.shieldHearts.at(p.data.level, p.rarity.statMultiplier());
                if (hearts > 0) {
                    int amp = Math.max(0, (int) Math.ceil(hearts / 2.0) - 1);
                    o.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, a.shieldTicks, amp, true, false, true));
                }
                fx(p, a, o);
                announce(p, a);
                p.cooldown = a.cooldownTicks;
            }
            case POTION -> {
                if (--p.potionRefresh > 0) return;
                p.potionRefresh = 40;
                PotionEffectType type = potion(a.potion);
                if (type == null || o.isDead()) return;
                int amp = a.potionAmplifier(p.rarity.index());
                PotionEffect cur = o.getPotionEffect(type);
                if (cur != null && (cur.getAmplifier() > amp || cur.getAmplifier() == amp && cur.getDuration() > 100)) return;
                o.addPotionEffect(new PotionEffect(type, 120, amp, true, false, true));
                if (p.cooldown <= 0) {
                    fx(p, a, o);
                    p.cooldown = a.cooldownTicks;
                }
            }
            case BUFF -> {
                // hurt / low-hp buffs go off in onOwnerDamaged
                if (!a.trigger.equals("combat")) return;
                if (p.cooldown > 0 || !plugin.pets().combat().tagged(o.getUniqueId())) return;
                LivingEntity target = plugin.pets().combat().target(o.getUniqueId(), 5000);
                if (target == null || target.getWorld() != o.getWorld()
                        || origin(p).distanceSquared(target.getLocation()) > a.range * a.range || !validTarget(o, target)) {
                    p.cooldown = RETRY_TICKS;
                    return;
                }
                p.cooldown = a.cooldownTicks;
                buff(p, a, target);
            }
            case PROJECTILE, STRIKE, MARK, METEOR -> {
                if (p.cooldown > 0 || !plugin.pets().combat().tagged(o.getUniqueId())) return;
                LivingEntity target = plugin.pets().combat().target(o.getUniqueId(), 5000);
                // cheap checks first, WorldGuard last; a failed try waits RETRY_TICKS instead of retrying every tick
                if (target == null) {
                    p.cooldown = RETRY_TICKS;
                    return;
                }
                Location from = origin(p);
                if (from.getWorld() != target.getWorld() || from.distanceSquared(target.getLocation()) > a.range * a.range
                        || !o.hasLineOfSight(target) || !validTarget(o, target)) {
                    p.cooldown = RETRY_TICKS;
                    return;
                }
                p.cooldown = a.cooldownTicks;
                announce(p, a);
                cast(p, a, target);
            }
            default -> {
            }
        }
    }

    /** Tells the owner which skill just went off (toggle: /pet thongbao). */
    private void announce(ActivePet p, Ability a) {
        if (!p.player.castChat) return;
        plugin.messages().send(p.owner, "ability.cast", "pet", plugin.pets().displayName(p.data),
                "ability", com.dex.boxpet.config.PetType.paint(a.color, a.name + "!"));
    }

    private static @Nullable PotionEffectType potion(String name) {
        if (name == null || name.isEmpty()) return null;
        PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(name.toLowerCase()));
        return t;
    }

    private Location origin(ActivePet p) {
        double h = p.visual == null ? 1 : p.visual.height();
        return p.pos.clone().add(0, h * 0.6, 0);
    }

    // ------------------------------------------------------------------ combat

    private void cast(ActivePet p, Ability a, LivingEntity target) {
        p.faceYaw = yawTo(p.pos, target.getLocation());
        p.faceTicks = a.castDelay + 20;
        plugin.pets().playAnimation(p, a.animation);
        sounds(p, a);
        particles(p, a, target, false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!stillOn(p, target)) return;
            if (a.type == Ability.Type.METEOR) {
                meteor(p, a, target);
                return;
            }
            // 0.8.0: some skills hit several times (Samurai, Cyborg)
            for (int i = 0; i < a.hits; i++) {
                boolean first = i == 0;
                if (first) act(p, a, target, true);
                else Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (stillOn(p, target)) act(p, a, target, false);
                }, (long) i * a.hitInterval);
            }
        }, a.castDelay);
    }

    private boolean stillOn(ActivePet p, LivingEntity target) {
        return plugin.pets().active(p.ownerId) == p && !p.abilityPaused() && target.isValid() && !target.isDead()
                && validTarget(p.owner, target);
    }

    private void act(ActivePet p, Ability a, LivingEntity target, boolean first) {
        switch (a.type) {
            case PROJECTILE -> launch(p, a, target);
            case STRIKE -> {
                showAt(p, a, target);
                strike(p, a, target);
            }
            case MARK -> {
                if (first) {
                    double bonus = a.markBonus.at(p.data.level, p.rarity.statMultiplier());
                    marks.put(target.getUniqueId(), new Mark(p.ownerId, bonus, System.currentTimeMillis() + a.markTicks * 50L));
                    showAt(p, a, target);
                } else {
                    sounds(p, a);
                }
                if (a.damage.base() > 0) strike(p, a, target);
                particles(p, a, target, true);
            }
            default -> {
            }
        }
    }

    /** Strike / mark damage: the target, or every target on the line from the pet with {@code pierce}. */
    private void strike(ActivePet p, Ability a, LivingEntity target) {
        if (!a.pierce) {
            hit(p, a, target);
            return;
        }
        Location from = origin(p);
        Vector seg = target.getLocation().add(0, target.getHeight() * 0.5, 0).toVector().subtract(from.toVector());
        double len = seg.length();
        if (len < 0.01) {
            hit(p, a, target);
            return;
        }
        Vector dir = seg.clone().normalize();
        for (double d = 0; d <= len; d += 0.6) {
            Effects.particle(from.clone().add(dir.clone().multiply(d)), "CRIT", 1, 0.02, 0);
        }
        Location mid = from.clone().add(dir.clone().multiply(len / 2));
        int n = 0;
        for (LivingEntity e : from.getWorld().getNearbyLivingEntities(mid, len / 2 + 1.5)) {
            if (n >= 8) break;
            if (!e.equals(target) && !validTarget(p.owner, e)) continue;
            Vector rel = e.getLocation().add(0, e.getHeight() * 0.5, 0).toVector().subtract(from.toVector());
            double along = rel.dot(dir);
            if (along < 0 || along > len + 0.5) continue;
            if (rel.clone().subtract(dir.clone().multiply(along)).lengthSquared() > 1.6 * 1.6 && !e.equals(target)) continue;
            hit(p, a, e);
            n++;
        }
    }

    /** Valid enemies of {@code owner} within {@code radius} of {@code at}, at most 8. */
    private List<LivingEntity> around(Player owner, Location at, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        if (at.getWorld() == null) return out;
        for (LivingEntity e : at.getWorld().getNearbyLivingEntities(at, radius)) {
            if (out.size() >= 8) break;
            if (e.getLocation().distanceSquared(at) > radius * radius + 1) continue;
            if (validTarget(owner, e)) out.add(e);
        }
        return out;
    }

    /** Chain: bounces from {@code from} to the nearest enemy not hit yet, weaker each time. */
    private void chain(ActivePet p, Ability a, LivingEntity from, double dmg, int left, java.util.Set<UUID> done) {
        if (left <= 0 || dmg <= 0) return;
        LivingEntity next = null;
        double best = a.chainRange * a.chainRange;
        for (LivingEntity e : from.getWorld().getNearbyLivingEntities(from.getLocation(), a.chainRange)) {
            if (done.contains(e.getUniqueId()) || !validTarget(p.owner, e)) continue;
            double d = e.getLocation().distanceSquared(from.getLocation());
            if (d < best) {
                best = d;
                next = e;
            }
        }
        if (next == null) return;
        LivingEntity to = next;
        done.add(to.getUniqueId());
        Location a1 = from.getLocation().add(0, from.getHeight() * 0.6, 0);
        Vector d = to.getLocation().add(0, to.getHeight() * 0.6, 0).toVector().subtract(a1.toVector());
        String part = a.particles.stream().filter(x -> x.at().equalsIgnoreCase("path")).map(Ability.ParticleFx::particle)
                .findFirst().orElse("CRIT");
        double len = d.length();
        Vector step = d.clone().normalize().multiply(0.5);
        Location cur = a1.clone();
        for (double t = 0; t < len; t += 0.5) {
            Effects.particle(cur, part, 1, 0.02, 0);
            cur.add(step);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.pets().active(p.ownerId) != p || !to.isValid() || to.isDead()) return;
            if (damage(p, a, to, dmg, false)) {
                applyDebuff(p, a, to, dmg);
                particles(p, a, to, true);
            }
            chain(p, a, to, dmg * a.chainRatio, left - 1, done);
        }, 4L);
    }

    /** Meteors fall around the target after a warning ring: dodgeable area damage. */
    private void meteor(ActivePet p, Ability a, LivingEntity target) {
        Location center = target.getLocation();
        double radius = a.areaRadius > 0 ? a.areaRadius : 2.5;
        var rnd = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < a.meteors; i++) {
            Location at = center.clone();
            if (i > 0) at.add(rnd.nextDouble(-a.meteorSpread, a.meteorSpread), 0, rnd.nextDouble(-a.meteorSpread, a.meteorSpread));
            long start = i * 6L;
            for (long w = 0; w < a.meteorWarn; w += 5) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> ring(at, radius, "FLAME"), start + w);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (plugin.pets().active(p.ownerId) != p || p.abilityPaused()) return;
                Effects.particle(at.clone().add(0, 0.5, 0), "EXPLOSION", 2, 0.5, 0);
                Effects.particle(at.clone().add(0, 0.5, 0), "LAVA", 12, radius * 0.4, 0.05);
                Effects.sound(at, "entity.generic.explode", 0.7f, 1.2f);
                double base = a.damage.at(p.data.level, p.rarity.statMultiplier());
                for (LivingEntity e : around(p.owner, at, radius)) {
                    if (damage(p, a, e, base, false)) applyDebuff(p, a, e, base);
                }
            }, start + a.meteorWarn);
        }
    }

    private static void ring(Location at, double radius, String particle) {
        for (int k = 0; k < 16; k++) {
            double ang = Math.PI * 2 * k / 16;
            Effects.particle(at.clone().add(Math.cos(ang) * radius, 0.15, Math.sin(ang) * radius), particle, 1, 0, 0);
        }
    }

    // ------------------------------------------------------------------ owner buffs (0.8.0)

    public static final String BUFF_KEY = "boxpet_buff";
    public static final String DEBUFF_KEY = "boxpet_debuff";

    /**
     * Owner-only stat buff for a while, plus at most one thing against the enemy: a stat taken from the enemy player(s)
     * or the ability's debuff on {@code enemy} (the target, or whoever hit the owner).
     */
    private void buff(ActivePet p, Ability a, @Nullable LivingEntity enemy) {
        int lvl = p.data.level;
        double mul = p.rarity.statMultiplier();
        if (!a.buffStat.isEmpty()) {
            plugin.stats().tempStat(plugin, p.owner, BUFF_KEY, a.buffStat, a.buffAmount.at(lvl, mul), a.buffRelative, a.buffTicks);
        }
        if (!a.enemyStat.isEmpty()) {
            double v = -a.enemyAmount.at(lvl, mul);
            if (a.enemyRadius > 0) {
                for (LivingEntity e : around(p.owner, p.owner.getLocation(), a.enemyRadius)) {
                    if (e instanceof Player ep) plugin.stats().tempStat(plugin, ep, DEBUFF_KEY, a.enemyStat, v, a.buffRelative, a.buffTicks);
                }
                ring(p.owner.getLocation(), a.enemyRadius, "END_ROD");
            } else if (enemy instanceof Player ep && validTarget(p.owner, ep)) {
                plugin.stats().tempStat(plugin, ep, DEBUFF_KEY, a.enemyStat, v, a.buffRelative, a.buffTicks);
            }
        }
        if (enemy != null && a.debuff != Ability.Debuff.NONE && validTarget(p.owner, enemy)) {
            applyDebuff(p, a, enemy, a.damage.at(lvl, mul));
        }
        fx(p, a, p.owner);
        announce(p, a);
    }

    /** The pet was put away / the plugin stops: no buff or stolen stat stays behind. */
    public void clearBuffs(Player player) {
        plugin.stats().clearTemp(player);
    }

    private void launch(ActivePet p, Ability a, LivingEntity target) {
        Location from = origin(p);
        PetVisual visual = null;
        if (a.vfx != null) {
            Location at = from.clone();
            at.setYaw(yawTo(from, target.getLocation()));
            visual = PetVisual.create(at, a.vfx.model(), a.vfx.scale(), plugin.settings().viewDistance, null, null, false,
                    plugin.getLogger());
            if (visual != null && !a.vfx.animation().isEmpty()) visual.play(a.vfx.animation());
        }
        double speed = a.vfx == null ? 0.8 : a.vfx.speed();
        int life = a.vfx == null ? 40 : a.vfx.life();
        Vfx v = new Vfx(visual, from, target, speed, life, true, 0);
        v.onHit = () -> {
            hit(p, a, target);
            particles(p, a, target, true);
        };
        v.trail = a.particles.stream().filter(x -> x.at().equalsIgnoreCase("path")).toList();
        vfx.add(v);
    }

    private void showAt(ActivePet p, Ability a, LivingEntity target) {
        if (a.vfx == null) return;
        Location at = target.getLocation().add(0, a.vfx.yOffset(), 0);
        at.setYaw(yawTo(p.pos, target.getLocation()));
        PetVisual visual = PetVisual.create(at, a.vfx.model(), a.vfx.scale(), plugin.settings().viewDistance, null, null, false,
                plugin.getLogger());
        if (visual == null) return;
        if (!a.vfx.animation().isEmpty()) visual.play(a.vfx.animation());
        int life = a.type == Ability.Type.MARK ? a.markTicks : a.vfx.life();
        Vfx v = new Vfx(visual, at, a.type == Ability.Type.MARK ? target : null, 0, life, false, a.vfx.yOffset());
        vfx.add(v);
    }

    /** Deals the ability damage once, plus its debuff. */
    private void hit(ActivePet p, Ability a, LivingEntity target) {
        double base = a.damage.at(p.data.level, p.rarity.statMultiplier());
        if (base <= 0) return;
        if (a.areaRadius > 0) {
            // 0.8.0: area, every enemy around the impact
            Location c = target.getLocation();
            Effects.particle(c.clone().add(0, 0.6, 0), "EXPLOSION", 1, 0, 0);
            ring(c, a.areaRadius, a.particles.stream().filter(x -> x.at().equalsIgnoreCase("target"))
                    .map(Ability.ParticleFx::particle).findFirst().orElse("CRIT"));
            List<LivingEntity> victims = around(p.owner, c, a.areaRadius);
            if (!victims.contains(target) && validTarget(p.owner, target)) victims.add(0, target);
            for (LivingEntity e : victims) if (damage(p, a, e, base, false)) applyDebuff(p, a, e, base);
        } else {
            if (!damage(p, a, target, base, false)) return;
            applyDebuff(p, a, target, base);
        }
        if (a.chainTargets > 0) {
            java.util.Set<UUID> done = new java.util.HashSet<>();
            done.add(target.getUniqueId());
            chain(p, a, target, base * a.chainRatio, a.chainTargets, done);
        }
    }

    private boolean damage(ActivePet p, Ability a, LivingEntity target, double amount, boolean dot) {
        Player o = p.owner;
        if (!o.isOnline() || !validTarget(o, target)) return false;
        double mult = target instanceof Player ? a.pvp(plugin.settings().defaultPvpMultiplier) : a.pveMultiplier;
        double dmg = amount * mult;
        PetAbilityDamageEvent ev = new PetAbilityDamageEvent(o, plugin.pets().view(p.data), target, a.id, dmg);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled() || ev.getDamage() <= 0) return false;
        // Pet damage ignores the target's hurt cooldown (noDamageTicks), then puts it back as it was: otherwise a pet
        // hit right after the owner's hit is swallowed, and its own hurt cooldown would weaken the owner's next hit
        int savedTicks = target.getNoDamageTicks();
        double savedLast = target.getLastDamage();
        target.setNoDamageTicks(0);
        dealing = true;
        try {
            // the owner's stats as a skill attack: MMOItems skill damage, magic defence, PvP damage apply
            var caster = MMOPlayerData.get(o).getStatMap().cache(io.lumine.mythic.lib.api.player.EquipmentSlot.MAIN_HAND);
            // knockback off by default: a pet must not push the target out of its owner's combo
            boolean kb = plugin.settings().abilityKnockback && !dot;
            if (dot) caster.attack(target, ev.getDamage(), kb, DamageType.SKILL, DamageType.MAGIC, DamageType.DOT);
            else caster.attack(target, ev.getDamage(), kb, DamageType.SKILL, DamageType.MAGIC);
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().log(Level.WARNING, "MythicLib attack failed, using plain damage", ex);
            target.damage(ev.getDamage(), o);
        } finally {
            dealing = false;
            if (target.isValid() && !target.isDead()) {
                target.setNoDamageTicks(savedTicks);
                target.setLastDamage(savedLast);
            }
        }
        return true;
    }

    private void applyDebuff(ActivePet p, Ability a, LivingEntity target, double base) {
        switch (a.debuff) {
            case BURN -> target.setFireTicks(Math.max(target.getFireTicks(), a.debuffTicks));
            case SLOW -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, a.debuffTicks, a.debuffAmplifier, false, true, true));
            case WEAKNESS -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, a.debuffTicks, a.debuffAmplifier, false, true, true));
            case BLEED -> {
                int times = Math.max(1, a.debuffTicks / 20);
                double each = base * a.debuffDamageRatio;
                for (int i = 1; i <= times; i++) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (plugin.pets().active(p.ownerId) != p || !target.isValid() || target.isDead()) return;
                        if (damage(p, a, target, each, true)) {
                            Effects.particle(target.getLocation().add(0, 1, 0), "DAMAGE_INDICATOR", 4, 0.2, 0.05);
                        }
                    }, i * 20L);
                }
            }
            default -> {
            }
        }
    }

    /**
     * May a pet of {@code owner} hit {@code target} (spec 6.1)? Never the owner, clan members, players in a
     * pvp-deny / boxpet-ability-deny region, creative / spectator / vanished players, NPCs, armor stands, the owner's
     * own tamed animals.
     */
    public boolean validTarget(Player owner, LivingEntity target) {
        if (target.equals(owner) || !target.isValid() || target.isDead() || target.isInvulnerable()) return false;
        if (target.getWorld() != owner.getWorld()) return false;
        if (target instanceof ArmorStand || target.hasMetadata("NPC")) return false;
        if (target instanceof Tameable t && t.isTamed() && t.getOwner() != null && t.getOwner().getUniqueId().equals(owner.getUniqueId())) {
            return false;
        }
        if (target instanceof Player p) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return false;
            if (p.hasMetadata("vanished") && p.getMetadata("vanished").stream().anyMatch(m -> m.asBoolean())) return false;
            if (plugin.clans().isPresent() && plugin.clans().get().sameClan(owner.getUniqueId(), p.getUniqueId())) return false;
            if (plugin.worldGuard().isPresent()) {
                if (!plugin.worldGuard().get().pvpAllowed(owner, p.getLocation())) return false;
                if (!plugin.worldGuard().get().pvpAllowed(owner, owner.getLocation())) return false;
            }
            return true;
        }
        return plugin.worldGuard().isEmpty() || plugin.worldGuard().get().abilityAllowed(owner, target.getLocation());
    }

    // ------------------------------------------------------------------ support

    /** The owner took damage (MONITOR): the shield ability (spec 6.2). */
    public void onOwnerDamaged(ActivePet p, double healthAfter) {
        onOwnerDamaged(p, healthAfter, null);
    }

    /** {@code attacker}: whoever hit the owner, when known (buffs may burn / weaken them). */
    public void onOwnerDamaged(ActivePet p, double healthAfter, @Nullable LivingEntity attacker) {
        Ability a = p.ability;
        if (a == null || p.abilityPaused()) return;
        long now = System.currentTimeMillis();
        if (a.type == Ability.Type.BUFF && !a.trigger.equals("combat")) {
            if (now < p.shieldReadyAt || healthAfter <= 0) return;
            if (a.trigger.equals("low-hp")) {
                AttributeInstance mx = p.owner.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (mx == null || healthAfter / mx.getValue() * 100 > a.buffBelowPercent) return;
            }
            p.shieldReadyAt = now + a.cooldownTicks * 50L;
            buff(p, a, attacker);
            return;
        }
        if (a.type != Ability.Type.SHIELD) return;
        if (now < p.shieldReadyAt) return;
        AttributeInstance max = p.owner.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (max == null || healthAfter <= 0 || healthAfter / max.getValue() * 100 > a.shieldBelowPercent) return;
        double hearts = a.shieldHearts.at(p.data.level, p.rarity.statMultiplier());
        int amp = Math.max(0, (int) Math.ceil(hearts / 2.0) - 1);
        p.owner.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, a.shieldTicks, amp, true, false, true));
        p.shieldReadyAt = now + a.cooldownTicks * 50L;
        fx(p, a, p.owner);
        announce(p, a);
        if (a.blastRadius > 0) {
            // 0.8.0: the shield comes up with a shockwave that pushes enemies away (Sculk)
            Location o = p.owner.getLocation();
            Effects.particle(o.clone().add(0, 1, 0), "SONIC_BOOM", 1, 0, 0);
            Effects.sound(o, "entity.warden.sonic_boom", 0.6f, 1.4f);
            for (LivingEntity e : around(p.owner, o, a.blastRadius)) {
                Vector push = e.getLocation().toVector().subtract(o.toVector()).setY(0);
                if (push.lengthSquared() < 0.01) push = new Vector(0, 0, 0.1);
                e.setVelocity(push.normalize().multiply(a.blastPower).setY(0.35));
            }
        }
        if (a.vfx != null) {
            Location at = p.owner.getLocation().add(0, a.vfx.yOffset(), 0);
            PetVisual visual = PetVisual.create(at, a.vfx.model(), a.vfx.scale(), plugin.settings().viewDistance, null, null,
                    false, plugin.getLogger());
            if (visual != null) {
                if (!a.vfx.animation().isEmpty()) visual.play(a.vfx.animation());
                vfx.add(new Vfx(visual, at, p.owner, 0, a.shieldTicks, false, a.vfx.yOffset()));
            }
        }
    }

    /** Damage multiplier for the owner's real hits on a target they marked (spec 6.1 mark). */
    public double markMultiplier(UUID attacker, UUID target) {
        Mark m = marks.get(target);
        if (m == null) return 1;
        if (m.until() < System.currentTimeMillis()) {
            marks.remove(target);
            return 1;
        }
        return m.owner().equals(attacker) ? 1 + m.bonus() / 100.0 : 1;
    }

    // ------------------------------------------------------------------ fx

    private void fx(ActivePet p, Ability a, LivingEntity at) {
        plugin.pets().playAnimation(p, a.animation);
        sounds(p, a);
        particles(p, a, at, false);
        particles(p, a, at, true);
    }

    private void sounds(ActivePet p, Ability a) {
        for (Ability.SoundFx s : a.sounds) {
            if (s.delay() <= 0) Effects.sound(p.pos, s.sound(), s.volume(), s.pitch());
            else Bukkit.getScheduler().runTaskLater(plugin, () -> Effects.sound(p.pos, s.sound(), s.volume(), s.pitch()), s.delay());
        }
    }

    /** {@code onTarget}: the particles placed at the target / owner, else the ones at the pet. */
    private void particles(ActivePet p, Ability a, LivingEntity target, boolean onTarget) {
        for (Ability.ParticleFx f : a.particles) {
            String at = f.at().toLowerCase();
            if (at.equals("path")) continue;
            boolean targetSide = at.equals("target") || at.equals("owner");
            if (targetSide != onTarget) continue;
            Runnable r = () -> {
                Location loc = switch (at) {
                    case "owner" -> p.owner.getLocation().add(0, 1, 0);
                    case "target" -> target.getLocation().add(0, target.getHeight() * 0.6, 0);
                    default -> origin(p);
                };
                Effects.particle(loc, f.particle(), f.count(), f.spread(), f.speed());
            };
            if (f.delay() <= 0) r.run();
            else Bukkit.getScheduler().runTaskLater(plugin, r, f.delay());
        }
    }

    private static float yawTo(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    // ------------------------------------------------------------------ vfx runtime

    private void tickVfx() {
        Iterator<Vfx> it = vfx.iterator();
        while (it.hasNext()) {
            Vfx v = it.next();
            try {
                if (!v.tick()) {
                    v.remove();
                    it.remove();
                }
            } catch (RuntimeException ex) {
                v.remove();
                it.remove();
            }
        }
        if (marks.size() > 64) marks.values().removeIf(m -> m.until() < System.currentTimeMillis());
        if (++ticks % 1200 == 0 && !cooldowns.isEmpty()) {
            long now = System.currentTimeMillis();
            cooldowns.values().removeIf(c -> c[0] <= now && c[1] <= now);
        }
    }

    /** A moving or attached VFX model, or a particle-only projectile. */
    private static final class Vfx {
        final @Nullable PetVisual visual;
        Location loc;
        final @Nullable LivingEntity follow;
        final double speed;
        int life;
        final boolean projectile;
        final double yOffset;
        Runnable onHit;
        List<Ability.ParticleFx> trail = List.of();

        Vfx(@Nullable PetVisual visual, Location loc, @Nullable LivingEntity follow, double speed, int life,
            boolean projectile, double yOffset) {
            this.visual = visual;
            this.loc = loc.clone();
            this.follow = follow;
            this.speed = speed;
            this.life = life;
            this.projectile = projectile;
            this.yOffset = yOffset;
        }

        /** @return false when finished */
        boolean tick() {
            if (--life < 0) return false;
            if (projectile) {
                if (follow == null || !follow.isValid() || follow.isDead() || follow.getWorld() != loc.getWorld()) return false;
                Location aim = follow.getLocation().add(0, follow.getHeight() * 0.55, 0);
                Vector d = aim.toVector().subtract(loc.toVector());
                double dist = d.length();
                if (dist <= speed + 0.6) {
                    if (onHit != null) onHit.run();
                    return false;
                }
                Location next = loc.clone().add(d.normalize().multiply(speed));
                if (!next.getBlock().isPassable()) {
                    Effects.puff(loc);
                    return false;
                }
                float yaw = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
                next.setYaw(yaw);
                loc = next;
                if (visual != null) visual.move(loc, yaw, yaw);
                for (Ability.ParticleFx f : trail) Effects.particle(loc, f.particle(), f.count(), f.spread(), f.speed());
                return true;
            }
            if (follow != null) {
                if (!follow.isValid() || follow.isDead()) return false;
                Location at = follow.getLocation().add(0, yOffset, 0);
                at.setYaw(loc.getYaw());
                loc = at;
                if (visual != null) visual.move(loc, loc.getYaw(), loc.getYaw());
            }
            return true;
        }

        void remove() {
            if (visual != null) visual.remove();
        }
    }
}
