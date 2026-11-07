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
    /**
     * 0.20.2: owner id -> when the skill of the pet they last put away is ready (epoch ms). A pet brought out
     * inherits it, so swapping pets never gives a fresh skill (A casts, swap to B, B still waits for A's cooldown).
     */
    private final Map<UUID, Long> ownerReady = new HashMap<>();
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
        ownerReady.clear();
    }

    // ------------------------------------------------------------------ cooldowns kept per pet

    /** The pet leaves (dismiss, logout): remember when its ability and shield are ready again. */
    public void remember(ActivePet p) {
        long now = System.currentTimeMillis();
        long ability = now + Math.max(0, p.cooldown) * 50L;
        long shield = Math.max(p.shieldReadyAt, 0);
        long ability2 = now + Math.max(0, p.cooldown2) * 50L;
        long shield2 = Math.max(p.shieldReadyAt2, 0);
        if (ability <= now && shield <= now && ability2 <= now && shield2 <= now) cooldowns.remove(p.data.id);
        else cooldowns.put(p.data.id, new long[]{ability, shield, ability2, shield2});
        long ready = Math.max(ability, shield);
        if (ready > now) ownerReady.merge(p.ownerId, ready, Math::max);
    }

    /** The pet comes back: same cooldowns as when it left. */
    public void restore(ActivePet p) {
        long[] c = cooldowns.remove(p.data.id);
        long now = System.currentTimeMillis();
        // 0.20.2: whatever pet comes out waits at least for the skill of the pet put away last
        Long owner = ownerReady.get(p.ownerId);
        if (owner != null && owner > now) {
            p.cooldown = (int) Math.max(p.cooldown, (owner - now) / 50L);
            p.shieldReadyAt = Math.max(p.shieldReadyAt, owner);
        }
        if (c == null) return;
        p.cooldown = (int) Math.max(p.cooldown, (c[0] - now) / 50L);
        p.shieldReadyAt = Math.max(p.shieldReadyAt, c[1]);
        if (c.length >= 4) {
            p.cooldown2 = (int) Math.max(0, (c[2] - now) / 50L);
            p.shieldReadyAt2 = c[3];
        }
    }

    // ------------------------------------------------------------------ per pet tick

    public void tick(ActivePet p) {
        slotNow = 0;
        tickSkill(p);
        if (!p.hasSecond(plugin.settings().secondSkillLevel)) return;
        // 0.9.0: the second skill runs with its own cooldowns
        p.swapSkills();
        slotNow = 1;
        try {
            tickSkill(p);
        } finally {
            p.swapSkills();
            slotNow = 0;
        }
    }

    /** 0.9.1: ticks between two skills of the same pet. */
    private static final int SKILL_GAP_TICKS = 40;
    /** Slot of the skill being run (0 or 1); its fields are in the first slot while it runs. */
    private int slotNow;

    /**
     * A pet with two skills uses them in turn, never both at once: at least SKILL_GAP_TICKS between them, and when both
     * cast skills are ready the one whose turn it is goes first.
     */
    private boolean myTurn(ActivePet p) {
        // while slot 1 runs, the fields of slot 0 are in the second slot: both mean "the pet has 2 working skills"
        if (p.ability2 == null || p.data.level < plugin.settings().secondSkillLevel) return true;
        if (ticks - p.lastSkillTick < SKILL_GAP_TICKS) return false;
        if (p.skillTurn == slotNow) return true;
        // the other skill (now in the second slot) only waits for its turn when it is a ready cast skill
        Ability other = p.ability2;
        boolean otherCast = other != null && (other.type.combat() || other.type == Ability.Type.ORBIT
                || other.type == Ability.Type.HEAL || other.type == Ability.Type.BUFF && other.trigger.equals("combat"));
        return !otherCast || p.cooldown2 > 0;
    }

    private void used(ActivePet p) {
        p.lastSkillTick = ticks;
        p.skillTurn = 1 - slotNow;
    }

    private void tickSkill(ActivePet p) {
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
                if (o.getHealth() >= max.getValue() || pct > a.healBelowPercent || !myTurn(p)) return;
                used(p);
                double amount = a.heal.at(p.data.level, p.rarity.statMultiplier());
                if (a.healPercent) amount = max.getValue() * amount / 100.0;
                if (a.healPulses > 1) {
                    // 0.16.0: hồi máu dần, the total split over the pulses
                    double step = amount / a.healPulses;
                    amount = step;
                    for (int i = 1; i < a.healPulses; i++) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (plugin.pets().active(p.ownerId) != p || !o.isOnline() || o.isDead()) return;
                            AttributeInstance mx = o.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                            if (mx == null) return;
                            o.setHealth(Math.min(mx.getValue(), o.getHealth() + step));
                            Effects.particle(o.getLocation().add(0, 1, 0), "HEART", 1, 0.3, 0);
                        }, (long) i * a.healInterval);
                    }
                }
                o.setHealth(Math.min(max.getValue(), o.getHealth() + amount));
                // 0.8.0: a heal may also give a shield (Thần Ra)
                double hearts = a.shieldHearts.at(p.data.level, p.rarity.statMultiplier());
                if (hearts > 0) {
                    int amp = Math.max(0, (int) Math.ceil(hearts / 2.0) - 1);
                    o.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, a.shieldTicks, amp, true, false, true));
                }
                fx(p, a, o);
                if (a.vfx != null) onOwner(p, a, a.vfx.life());
                announce(p, a);
                p.cooldown = a.fullCooldownTicks();
                if (a.areaRadius > 0 && (a.damage.base() > 0 || !a.enemyStat.isEmpty())) domain(p, a);
                // 0.16.1: a heal with a buff section also gives the owner that stat
                if (!a.buffStat.isEmpty()) {
                    plugin.stats().tempStat(plugin, o, BUFF_KEY, a.buffStat, a.buffAmount.at(p.data.level, p.rarity.statMultiplier()),
                            a.buffRelative, a.buffTicks);
                }
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
                    p.cooldown = a.fullCooldownTicks();
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
                if (!myTurn(p)) return;
                used(p);
                p.cooldown = a.fullCooldownTicks();
                buff(p, a, target);
            }
            case PROJECTILE, STRIKE, MARK, METEOR, ORBIT, ZONE, TETHER, NOVA -> {
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
                if (!myTurn(p)) return;
                used(p);
                p.cooldown = a.fullCooldownTicks();
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
        // same look as the pet's chat lines (dialogues.yml format), no plugin prefix
        plugin.messages().sendPlain(p.owner, com.dex.boxpet.config.Messages.apply(plugin.dialogues().format,
                "pet", plugin.pets().displayName(p.data), "line", com.dex.boxpet.config.PetType.paint(a.color, a.name + "!")));
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
        // 0.15.0: an attack skill with a buff section also gives the owner that stat for buff.seconds
        if (!a.buffStat.isEmpty()) {
            plugin.stats().tempStat(plugin, p.owner, BUFF_KEY, a.buffStat, a.buffAmount.at(p.data.level, p.rarity.statMultiplier()),
                    a.buffRelative, a.buffTicks);
        }
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
            if (a.type == Ability.Type.ORBIT) {
                orbit(p, a);
                return;
            }
            if (a.type == Ability.Type.ZONE) {
                zone(p, a, target);
                return;
            }
            if (a.type == Ability.Type.TETHER) {
                tether(p, a, target);
                return;
            }
            if (a.type == Ability.Type.NOVA) {
                nova(p, a, target);
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

    /** Counts the hits of one cast so each can show its own vfx animation. */
    private final Map<UUID, Integer> hitCount = new HashMap<>();

    private int hitIndex(ActivePet p, Ability a, boolean first) {
        int i = first ? 0 : hitCount.getOrDefault(p.ownerId, 0) + 1;
        hitCount.put(p.ownerId, i);
        return i;
    }

    private void act(ActivePet p, Ability a, LivingEntity target, boolean first) {
        switch (a.type) {
            case PROJECTILE -> {
                launch(p, a, target);
                // 0.9.7: one projectile each for more enemies around the target
                if (a.multiTargets > 1) {
                    int n = 1;
                    for (LivingEntity e : around(p.owner, target.getLocation(), 8)) {
                        if (n >= a.multiTargets) break;
                        if (e.equals(target)) continue;
                        launch(p, a, e);
                        n++;
                    }
                }
            }
            case STRIKE -> {
                showAt(p, a, target, hitIndex(p, a, first));
                strike(p, a, target);
            }
            case MARK -> {
                if (first) {
                    double bonus = a.markBonus.at(p.data.level, p.rarity.statMultiplier());
                    long until = System.currentTimeMillis() + a.markTicks * 50L;
                    // 0.16.0: an area mark tags every enemy around the target (Quét Mục Tiêu)
                    List<LivingEntity> tagged = new ArrayList<>();
                    tagged.add(target);
                    if (a.areaRadius > 0) {
                        for (LivingEntity e : around(p.owner, target.getLocation(), a.areaRadius)) if (!e.equals(target)) tagged.add(e);
                    }
                    for (LivingEntity e : tagged) {
                        marks.put(e.getUniqueId(), new Mark(p.ownerId, bonus, until));
                        showAt(p, a, e);
                        // a support mark (no damage) still brings its enemy-stat (Lệnh Truy Nã)
                        if (a.damage.base() <= 0) applyDebuff(p, a, e, 0);
                    }
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
        String trail = a.particles.stream().filter(x -> x.at().equalsIgnoreCase("path")).map(Ability.ParticleFx::particle)
                .findFirst().orElse("CRIT");
        for (double d = 0; d <= len; d += 0.6) {
            Effects.particle(from.clone().add(dir.clone().multiply(d)), trail, 2, 0.05, 0);
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

    /** A model that follows {@code target} for {@code ticks} (bleed mark). */
    private void attachVfx(LivingEntity target, String model, String animation, int ticks, double yOffset) {
        Location at = target.getLocation().add(0, yOffset, 0);
        PetVisual visual = PetVisual.create(at, model, 1.0, plugin.settings().viewDistance, null, null, false, plugin.getLogger());
        if (visual == null) return;
        if (!animation.isEmpty()) visual.play(animation);
        vfx.add(new Vfx(visual, at, target, 0, ticks, false, yOffset));
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
            // 0.9.1: the meteor itself, the skill's vfx model falling from the sky onto the ring
            if (a.vfx != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> fall(a, at, a.meteorWarn), start);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (plugin.pets().active(p.ownerId) != p || p.abilityPaused()) return;
                Effects.particle(at.clone().add(0, 0.5, 0), "EXPLOSION", 2, 0.5, 0);
                Effects.particle(at.clone().add(0, 0.5, 0), "LAVA", 12, radius * 0.4, 0.05);
                burst(a, at, radius);
                Effects.sound(at, "entity.generic.explode", 0.7f, 1.2f);
                double base = a.damage.at(p.data.level, p.rarity.statMultiplier());
                for (LivingEntity e : around(p.owner, at, radius)) {
                    if (damage(p, a, e, base, false)) applyDebuff(p, a, e, base);
                }
            }, start + a.meteorWarn);
        }
    }

    private void fall(Ability a, Location at, int ticks) {
        Location top = at.clone().add(0, 12, 0);
        top.setPitch(90);
        PetVisual visual = PetVisual.create(top, a.vfx.model(), a.vfx.scale(), plugin.settings().viewDistance, null, null,
                false, plugin.getLogger());
        if (visual == null) return;
        if (!a.vfx.animation().isEmpty()) visual.play(a.vfx.animation());
        Vfx v = new Vfx(visual, top, null, 0, ticks, false, 0);
        double step = 12.0 / Math.max(1, ticks);
        v.mover = loc -> loc.add(0, -step, 0);
        vfx.add(v);
    }

    /**
     * 0.9.1 orbit (Vòng Lửa Rồng): the skill's vfx model circles the owner {@code meteors} times over; an orb that
     * touches an enemy bursts on it (damage + debuff) and is gone.
     */
    private void orbit(ActivePet p, Ability a) {
        int count = a.meteors;
        double radius = a.areaRadius > 0 ? a.areaRadius : 2;
        double base = a.damage.at(p.data.level, p.rarity.statMultiplier());
        List<PetVisual> orbs = new ArrayList<>();
        boolean[] gone = new boolean[count];
        for (int i = 0; i < count; i++) {
            PetVisual visual = a.vfx == null ? null : PetVisual.create(p.owner.getLocation(), a.vfx.model(), a.vfx.scale(),
                    plugin.settings().viewDistance, null, null, false, plugin.getLogger());
            if (visual != null && !a.vfx.animation().isEmpty()) visual.play(a.vfx.animation());
            orbs.add(visual);
        }
        String trail = a.particles.stream().filter(x -> x.at().equalsIgnoreCase("path")).map(Ability.ParticleFx::particle)
                .findFirst().orElse("FLAME");
        int[] t = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean alive = plugin.pets().active(p.ownerId) == p && !p.abilityPaused() && p.owner.isOnline();
            if (!alive || ++t[0] > a.orbitTicks) {
                for (PetVisual v : orbs) if (v != null) v.remove();
                task[0].cancel();
                return;
            }
            Location c = p.owner.getLocation().add(0, 1.1, 0);
            for (int i = 0; i < count; i++) {
                if (gone[i]) continue;
                double ang = t[0] * 0.18 + Math.PI * 2 * i / count;
                Location at = c.clone().add(Math.cos(ang) * radius, 0, Math.sin(ang) * radius);
                float yaw = (float) Math.toDegrees(ang) + 180;
                at.setYaw(yaw);
                if (orbs.get(i) != null) orbs.get(i).move(at, yaw, yaw);
                Effects.particle(at, trail, 1, 0.05, 0);
                for (LivingEntity e : at.getWorld().getNearbyLivingEntities(at, 1.3)) {
                    if (!validTarget(p.owner, e)) continue;
                    gone[i] = true;
                    if (orbs.get(i) != null) orbs.get(i).remove();
                    Effects.particle(at, "EXPLOSION", 1, 0, 0);
                    Effects.sound(at, "entity.generic.explode", 0.5f, 1.4f);
                    burst(a, e.getLocation(), 1.3);
                    if (damage(p, a, e, base, false)) applyDebuff(p, a, e, base);
                    break;
                }
            }
        }, 1L, 1L);
    }

    /** The skill's explosion model ({@code fx.area-vfx}) on the ground, 1 block radius at scale 1. */
    private void burst(Ability a, Location at, double radius) {
        if (a.areaVfx.isEmpty() || at.getWorld() == null) return;
        Location l = at.clone();
        l.setYaw(0);
        PetVisual visual = PetVisual.create(l, a.areaVfx, Math.max(0.3, radius), plugin.settings().viewDistance, null, null,
                false, plugin.getLogger());
        if (visual == null) return;
        visual.play("skill");
        vfx.add(new Vfx(visual, l, null, 0, 14, false, 0));
    }

    /** 0.9.7 zone: the skill's model lies at the target; every enemy inside is hit each interval (dodge by leaving). */
    private void zone(ActivePet p, Ability a, LivingEntity target) {
        Location c = target.getLocation();
        double radius = a.areaRadius > 0 ? a.areaRadius : 3;
        double base = a.damage.at(p.data.level, p.rarity.statMultiplier());
        if (a.vfx != null) {
            Location l = c.clone();
            l.setYaw(0);
            PetVisual visual = PetVisual.create(l, a.vfx.model(), Math.max(0.3, radius * a.vfx.scale()), plugin.settings().viewDistance,
                    null, null, false, plugin.getLogger());
            if (visual != null) {
                if (!a.vfx.animation().isEmpty()) visual.play(a.vfx.animation());
                vfx.add(new Vfx(visual, l, null, 0, a.zoneTicks, false, 0));
            }
        }
        for (int t = a.zoneInterval / 2; t <= a.zoneTicks; t += a.zoneInterval) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (plugin.pets().active(p.ownerId) != p || p.abilityPaused()) return;
                if (a.vfx == null) ring(c, radius, "WITCH");
                for (LivingEntity e : around(p.owner, c, radius)) {
                    if (damage(p, a, e, base, false)) applyDebuff(p, a, e, base);
                }
            }, t);
        }
    }

    /**
     * 0.9.8 tether (Xích Linh Hồn): a chain of soul fire from the pet to one target; every interval it hits (as damage over
     * time) and applies the debuff. It breaks when the target gets out of range + 4 blocks, out of sight or invalid.
     */
    private void tether(ActivePet p, Ability a, LivingEntity target) {
        double base = a.damage.at(p.data.level, p.rarity.statMultiplier());
        String part = a.particles.stream().filter(x -> x.at().equalsIgnoreCase("path")).map(Ability.ParticleFx::particle)
                .findFirst().orElse("SOUL_FIRE_FLAME");
        Vfx bound = null;
        if (a.vfx != null) {
            Location at = target.getLocation().add(0, a.vfx.yOffset(), 0);
            PetVisual visual = PetVisual.create(at, a.vfx.model(), a.vfx.scale(), plugin.settings().viewDistance, null, null, false,
                    plugin.getLogger());
            if (visual != null) {
                if (!a.vfx.animation().isEmpty()) visual.play(a.vfx.animation());
                bound = new Vfx(visual, at, target, 0, a.tetherTicks, false, a.vfx.yOffset());
                vfx.add(bound);
            }
        }
        final Vfx chainVfx = bound;
        double maxRange = a.range + 4;
        int[] t = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean ok = plugin.pets().active(p.ownerId) == p && !p.abilityPaused() && target.isValid() && !target.isDead()
                    && target.getWorld() == p.pos.getWorld() && validTarget(p.owner, target)
                    && origin(p).distanceSquared(target.getLocation()) <= maxRange * maxRange && p.owner.hasLineOfSight(target);
            if (!ok || ++t[0] > a.tetherTicks) {
                if (chainVfx != null) chainVfx.life = 0;
                task[0].cancel();
                return;
            }
            if (t[0] % 2 == 0) {
                Location from = origin(p);
                Vector d = target.getLocation().add(0, target.getHeight() * 0.55, 0).toVector().subtract(from.toVector());
                double len = d.length();
                Vector step = d.clone().normalize().multiply(0.45);
                Location cur = from.clone();
                for (double s = 0; s < len; s += 0.45) {
                    // a slight wave so it reads as a chain, not a laser
                    double wave = Math.sin(s * 2.2 + t[0] * 0.4) * 0.12;
                    Effects.particle(cur.clone().add(0, wave, 0), part, 1, 0.01, 0);
                    cur.add(step);
                }
            }
            if (t[0] % a.tetherInterval == 0 && damage(p, a, target, base, true)) applyDebuff(p, a, target, base);
        }, 1L, 1L);
    }

    /**
     * 0.11.0 nova (Rồng Lửa, Rồng Bóng Tối, Tinh Linh Thiên Hà): the pack's own skill effect plays on the ground at
     * the target and follows it; at each hit tick (the explosion frames) every enemy within the radius is hit.
     */
    private void nova(ActivePet p, Ability a, LivingEntity target) {
        double radius = a.areaRadius > 0 ? a.areaRadius : 3;
        double base = a.damage.at(p.data.level, p.rarity.statMultiplier());
        Location[] center = {target.getLocation()};
        Vfx shown = null;
        boolean atPet = a.novaAt.equals("pet");
        // 0.17.2: a line nova starts at the pet (or close enough that its far end reaches the target), facing it
        boolean line = a.lineTo > 0;
        double sc = a.vfx == null ? 1 : a.vfx.scale();
        Vector lineDir = center[0].toVector().subtract(p.pos.toVector()).setY(0);
        double lineDist = lineDir.length();
        lineDir = lineDist < 0.01 ? new Vector(0, 0, 1) : lineDir.multiply(1 / lineDist);
        Location lineStart = p.pos.clone();
        if (line && lineDist > a.lineTo * sc * 0.9) {
            lineStart = center[0].clone().subtract(lineDir.clone().multiply(a.lineTo * sc * 0.85));
            lineStart.setY(center[0].getY());
        }
        lineStart.setYaw(yawTo(lineStart, center[0]));
        lineStart.setPitch(0);
        Location beamFrom = null;
        if (a.vfx != null) {
            boolean travel = !atPet && a.novaTravel > 0;
            Location at = atPet || travel ? p.pos.clone().add(0, a.vfx.yOffset(), 0) : center[0].clone().add(0, a.vfx.yOffset(), 0);
            at.setYaw(yawTo(atPet || travel ? at : p.pos, center[0]));
            at.setPitch(0);
            if (line) at = lineStart.clone().add(0, a.vfx.yOffset(), 0);
            if (atPet && !a.beamParticle.isEmpty()) {
                // 0.13.1: the model faces the target, so "back" is away from it (the flower stands behind the pet)
                Vector away = at.toVector().subtract(center[0].toVector()).setY(0);
                if (away.lengthSquared() < 0.01) away = new Vector(0, 0, 1);
                beamFrom = at.clone().add(away.normalize().multiply(a.beamBack * a.vfx.scale())).add(0, a.beamUp * a.vfx.scale(), 0);
            }
            // 0.11.5: no flash of the rest pose: the nova models keep it far under the ground (vfx/tools/extract.js)
            PetVisual visual = PetVisual.create(at, a.vfx.model(), a.vfx.scale(), plugin.settings().viewDistance, null, null, false,
                    plugin.getLogger());
            if (visual != null) {
                if (!a.vfx.animation().isEmpty()) visual.play(a.vfx.animation());
                shown = new Vfx(visual, at, atPet || line ? null : target, 0, a.vfx.life(), false, a.vfx.yOffset());
                if (travel) {
                    // 0.14.0: blown from the pet to the target, then it stays on the target
                    shown.travelFrom = at.clone();
                    shown.travelTicks = a.novaTravel;
                }
                vfx.add(shown);
            }
        }
        int firstHit = a.novaHits.get(0);
        final Location lineOrigin = lineStart;
        final Vector lineAxis = lineDir;
        final Location beam = beamFrom;
        for (int hit = 0; hit < a.novaHits.size(); hit++) {
            int t = a.novaHits.get(hit);
            double dmg = base * a.novaWeight(hit);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (plugin.pets().active(p.ownerId) != p || p.abilityPaused()) return;
                // follows the target while it lives, else the last place it was
                if (target.isValid() && !target.isDead() && target.getWorld() == center[0].getWorld()) center[0] = target.getLocation();
                Location c = center[0];
                List<LivingEntity> victims;
                if (line) {
                    // the corridor of the fissure, fixed where it opened
                    double from = a.lineFrom * sc, to = a.lineTo * sc;
                    Location mid = lineOrigin.clone().add(lineAxis.clone().multiply((from + to) / 2));
                    victims = new ArrayList<>();
                    for (LivingEntity e : around(p.owner, mid, (to - from) / 2 + a.lineWidth + 1)) {
                        Vector rel = e.getLocation().toVector().subtract(lineOrigin.toVector()).setY(0);
                        double along = rel.dot(lineAxis);
                        if (along < from - 0.5 || along > to + 0.5) continue;
                        if (rel.subtract(lineAxis.clone().multiply(along)).length() <= a.lineWidth) victims.add(e);
                    }
                } else {
                    victims = around(p.owner, c, radius);
                    if (!victims.contains(target) && validTarget(p.owner, target)
                            && target.getLocation().distanceSquared(c) <= radius * radius + 1) victims.add(0, target);
                }
                for (LivingEntity e : victims) {
                    if (beam != null && beam.getWorld() == e.getWorld()) line(beam, e.getLocation().add(0, e.getHeight() * 0.55, 0), a.beamParticle);
                    if (damage(p, a, e, dmg, false)) {
                        applyDebuff(p, a, e, dmg);
                        particles(p, a, e, true);
                        // 0.13.0: a nova with a mark section marks whoever its first hit catches (Hiệp Sĩ Thánh)
                        if (t == firstHit && a.markBonus.base() > 0) {
                            marks.put(e.getUniqueId(), new Mark(p.ownerId, a.markBonus.at(p.data.level, p.rarity.statMultiplier()),
                                    System.currentTimeMillis() + a.markTicks * 50L));
                        }
                    }
                }
            }, t);
        }
    }

    /**
     * 0.14.0 domain: after a heal, enemies inside the radius around the owner are hit at each nova hit tick (the
     * pillars of Lãnh Địa Phượng Hoàng). The area moves with the owner.
     */
    private void domain(ActivePet p, Ability a) {
        double base = a.damage.at(p.data.level, p.rarity.statMultiplier());
        for (int hit = 0; hit < a.novaHits.size(); hit++) {
            double dmg = base * a.novaWeight(hit);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (plugin.pets().active(p.ownerId) != p || p.abilityPaused() || !p.owner.isOnline()) return;
                for (LivingEntity e : around(p.owner, p.owner.getLocation(), a.areaRadius)) {
                    // 0.14.1: an enemy player inside loses the stat for a short while, renewed every wave: it lasts
                    // as long as they stay in the domain
                    if (!a.enemyStat.isEmpty() && e instanceof Player ep) {
                        plugin.stats().tempStat(plugin, ep, DEBUFF_KEY, a.enemyStat,
                                -a.enemyAmount.at(p.data.level, p.rarity.statMultiplier()), false, a.enemyTicks);
                        particles(p, a, e, true);
                    }
                    if (dmg > 0 && damage(p, a, e, dmg, false)) particles(p, a, e, true);
                }
            }, a.novaHits.get(hit));
        }
    }

    /** A straight particle line (the light beam from the flower to an enemy). */
    private static void line(Location from, Location to, String particle) {
        Vector d = to.toVector().subtract(from.toVector());
        double len = d.length();
        if (len < 0.1 || len > 40) return;
        Vector step = d.multiply(0.35 / len);
        Location cur = from.clone();
        for (double s = 0; s < len; s += 0.35) {
            Effects.particle(cur, particle, 1, 0.02, 0);
            cur.add(step);
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
        boolean firstStack = true;
        if (!a.buffChoices.isEmpty()) {
            // 0.16.0: Hộp Quà, one stat at random
            Ability.Choice c = a.buffChoices.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(a.buffChoices.size()));
            plugin.stats().tempStat(plugin, p.owner, BUFF_KEY, c.stat(), c.amount().at(lvl, mul), false, a.buffTicks);
        } else if (!a.buffStat.isEmpty() && a.buffStacks > 1) {
            // 0.16.0: Tích Giáp, one more step per trigger, all steps renewed
            long now = System.currentTimeMillis();
            if (now > p.buffStackUntil) {
                p.buffStack = 0;
                p.buffStackUntil = now + a.buffTicks * 50L; // the window opens with the first hit, not renewed
            }
            firstStack = p.buffStack == 0;
            p.buffStack = Math.min(a.buffStacks, p.buffStack + 1);
            int left = (int) Math.max(1, (p.buffStackUntil - now) / 50L);
            plugin.stats().tempStat(plugin, p.owner, BUFF_KEY, a.buffStat, a.buffAmount.at(lvl, mul) * p.buffStack, a.buffRelative, left);
        } else if (!a.buffStat.isEmpty()) {
            plugin.stats().tempStat(plugin, p.owner, BUFF_KEY, a.buffStat, a.buffAmount.at(lvl, mul), a.buffRelative, a.buffTicks);
        }
        if (!a.buff2Stat.isEmpty()) {
            plugin.stats().tempStat(plugin, p.owner, BUFF_KEY, a.buff2Stat, a.buff2Amount.at(lvl, mul), false, a.buffTicks);
        }
        if (!a.enemyStat.isEmpty()) {
            double v = -a.enemyAmount.at(lvl, mul);
            if (a.enemyRadius > 0 && a.enemyDomain) {
                // 0.16.0: lãnh địa, enemies inside lose the stat as long as they stay (renewed every half second)
                for (int t = 0; t < a.buffTicks; t += 10) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (plugin.pets().active(p.ownerId) != p || !p.owner.isOnline()) return;
                        for (LivingEntity e : around(p.owner, p.owner.getLocation(), a.enemyRadius)) {
                            if (e instanceof Player ep) plugin.stats().tempStat(plugin, ep, DEBUFF_KEY, a.enemyStat, v, a.buffRelative, 12);
                        }
                    }, t);
                }
            } else if (a.enemyRadius > 0) {
                for (LivingEntity e : around(p.owner, p.owner.getLocation(), a.enemyRadius)) {
                    if (e instanceof Player ep) plugin.stats().tempStat(plugin, ep, DEBUFF_KEY, a.enemyStat, v, a.buffRelative, a.enemyTicks);
                }
                ring(p.owner.getLocation(), a.enemyRadius, "END_ROD");
            } else if (enemy instanceof Player ep && validTarget(p.owner, ep)) {
                plugin.stats().tempStat(plugin, ep, DEBUFF_KEY, a.enemyStat, v, a.buffRelative, a.enemyTicks);
            }
        }
        if (enemy != null && a.debuff != Ability.Debuff.NONE && validTarget(p.owner, enemy)) {
            applyDebuff(p, a, enemy, a.damage.at(lvl, mul));
        }
        fx(p, a, p.owner);
        if (a.vfx != null && firstStack) {
            onOwner(p, a, a.buffTicks);
            // 0.15.0: the same model under the robbed enemy too (Kỳ Lân U Minh)
            if (a.vfx.onEnemy() && enemy != null && validTarget(p.owner, enemy)) onEntity(a, enemy, a.buffTicks);
        }
        announce(p, a);
    }

    /** The skill's model under {@code e} for {@code ticks}, tinted like on the owner, not turning with it. */
    private void onEntity(Ability a, LivingEntity e, int ticks) {
        Location at = e.getLocation().add(0, a.vfx.yOffset(), 0);
        at.setPitch(0);
        PetVisual visual = PetVisual.create(at, a.vfx.model(), a.vfx.scale(), plugin.settings().viewDistance, null, null, false,
                plugin.getLogger());
        if (visual == null) return;
        if (a.vfx.tint() != null) visual.tint(color(a.vfx.tint()));
        if (a.vfx.glow()) visual.fullBright();
        if (!a.vfx.animation().isEmpty()) visual.play(a.vfx.animation());
        vfx.add(new Vfx(visual, at, e, 0, ticks, false, a.vfx.yOffset()));
    }

    /** 0.9.3: the skill's model stays on the owner for {@code ticks}, turning with them (wings, halo, rune ring). */
    private void onOwner(ActivePet p, Ability a, int ticks) {
        Location at = p.owner.getLocation().add(0, a.vfx.yOffset(), 0);
        at.setYaw(p.owner.getBodyYaw());
        PetVisual visual = PetVisual.create(at, a.vfx.model(), a.vfx.scale(), plugin.settings().viewDistance, null, null, false,
                plugin.getLogger());
        if (visual == null) return;
        if (a.vfx.tint() != null) visual.tint(color(a.vfx.tint()));
        if (a.vfx.glow()) visual.fullBright();
        if (!a.vfx.animation().isEmpty()) visual.play(a.vfx.animation(), a.vfx.animSpeed());
        Vfx v = new Vfx(visual, at, p.owner, 0, ticks, false, a.vfx.yOffset());
        v.turn = a.vfx.turn();
        vfx.add(v);
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
        showAt(p, a, target, 0);
    }

    private void showAt(ActivePet p, Ability a, LivingEntity target, int hit) {
        if (a.vfx == null) return;
        Location at = target.getLocation().add(0, a.vfx.yOffset(), 0);
        at.setYaw(yawTo(p.pos, target.getLocation()));
        if (a.vfx.center() > 0) at = centeredOn(p, target, a.vfx);
        PetVisual visual = PetVisual.create(at, a.vfx.model(), a.vfx.scale(), plugin.settings().viewDistance, null, null, false,
                plugin.getLogger());
        if (visual == null) return;
        if (a.vfx.tint() != null) visual.tint(color(a.vfx.tint()));
        if (a.vfx.glow()) visual.fullBright();
        if (a.vfx.flipbook()) flipbook(visual, a.vfx);
        String anim = a.vfxAnimations.isEmpty() ? a.vfx.animation() : a.vfxAnimations.get(hit % a.vfxAnimations.size());
        if (!anim.isEmpty()) visual.play(anim);
        // a mark keeps its model on the target; a flipbook (thrust / slash) plays once where it started
        boolean stick = a.type == Ability.Type.MARK && !a.vfx.flipbook();
        int life = stick ? a.markTicks : a.vfx.life();
        Vfx v = new Vfx(visual, at, stick ? target : null, 0, life, false, a.vfx.yOffset());
        vfx.add(v);
    }

    /**
     * 0.12.3: the model's visible middle lands on the target: its origin goes {@code center × scale} blocks back towards
     * the pet, facing the target (the thrust's glow is ~1.75 blocks ahead of its origin, the slash's arc ~1.86).
     */
    private Location centeredOn(ActivePet p, LivingEntity target, Ability.VfxFx fx) {
        Location to = target.getLocation();
        Vector d = to.toVector().subtract(p.pos.toVector()).setY(0);
        double len = d.length();
        Vector dir = len < 0.01 ? new Vector(0, 0, 1) : d.multiply(1 / len);
        Location at = to.clone().subtract(dir.clone().multiply(fx.center() * fx.scale()));
        at.add(0, fx.yOffset(), 0);
        at.setYaw(yawTo(at, to));
        at.setPitch(0);
        return at;
    }

    /** Frame models swapped into the effect's parts, one per tick from tick 2 (pack MythicMobs changepart delays). */
    private void flipbook(PetVisual visual, Ability.VfxFx fx) {
        for (int i = 0; i < fx.frames().size(); i++) {
            String frame = fx.frames().get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (visual.removed()) return;
                for (String part : fx.parts()) visual.swapPart(part, frame, part);
            }, i + 2L);
        }
    }

    private static @Nullable org.bukkit.Color color(String hex) {
        try {
            return org.bukkit.Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Deals the ability damage once, plus its debuff. */
    private void hit(ActivePet p, Ability a, LivingEntity target) {
        double base = a.damage.at(p.data.level, p.rarity.statMultiplier());
        if (base <= 0) return;
        if (a.areaRadius > 0) {
            // 0.8.0: area, every enemy around the impact
            Location c = target.getLocation();
            Effects.particle(c.clone().add(0, 0.6, 0), "EXPLOSION", 1, 0, 0);
            burst(a, c, a.areaRadius);
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
        // 0.9.9: a hit can lower an MMOItems stat of an enemy player for a while (instead of potion effects)
        if (a.type != Ability.Type.BUFF && !a.enemyStat.isEmpty() && target instanceof Player tp) {
            plugin.stats().tempStat(plugin, tp, DEBUFF_KEY, a.enemyStat, -a.enemyAmount.at(p.data.level, p.rarity.statMultiplier()),
                    false, a.enemyTicks);
        }
        switch (a.debuff) {
            case BURN -> target.setFireTicks(Math.max(target.getFireTicks(), a.debuffTicks));
            case SLOW -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, a.debuffTicks, a.debuffAmplifier, false, true, true));
            case WEAKNESS -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, a.debuffTicks, a.debuffAmplifier, false, true, true));
            case BLEED -> {
                if (!a.debuffVfx.isEmpty()) attachVfx(target, a.debuffVfx, "loop", a.debuffTicks, 0.2);
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
        slotNow = 0;
        ownerDamaged(p, healthAfter, attacker);
        if (!p.hasSecond(plugin.settings().secondSkillLevel)) return;
        p.swapSkills();
        slotNow = 1;
        try {
            ownerDamaged(p, healthAfter, attacker);
        } finally {
            p.swapSkills();
            slotNow = 0;
        }
    }

    private void ownerDamaged(ActivePet p, double healthAfter, @Nullable LivingEntity attacker) {
        Ability a = p.ability;
        if (a == null || p.abilityPaused()) return;
        long now = System.currentTimeMillis();
        // 0.16.0: while the shield is up, whoever hits the owner loses the enemy-stat (Giáp Bánh Răng)
        if (a.type == Ability.Type.SHIELD && !a.enemyStat.isEmpty() && now < p.shieldDebuffUntil
                && attacker instanceof Player ap && validTarget(p.owner, ap)) {
            plugin.stats().tempStat(plugin, ap, DEBUFF_KEY, a.enemyStat, -a.enemyAmount.at(p.data.level, p.rarity.statMultiplier()),
                    false, a.enemyTicks);
            Effects.particle(ap.getLocation().add(0, 1, 0), "CRIT", 6, 0.3, 0.1);
        }
        if (a.type == Ability.Type.BUFF && !a.trigger.equals("combat")) {
            if (healthAfter <= 0) return;
            // 0.19.0: a stacking buff keeps stacking inside its window, the cooldown only gates opening a window
            if (a.buffStacks > 1 && now < p.buffStackUntil) {
                if (p.buffStack < a.buffStacks) buff(p, a, attacker);
                return;
            }
            if (now < p.shieldReadyAt) return;
            if (a.trigger.equals("low-hp")) {
                AttributeInstance mx = p.owner.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (mx == null || healthAfter / mx.getValue() * 100 > a.buffBelowPercent) return;
            }
            if (!myTurn(p)) return;
            used(p);
            p.shieldReadyAt = now + a.fullCooldownTicks() * 50L;
            buff(p, a, attacker);
            return;
        }
        if (a.type != Ability.Type.SHIELD) return;
        if (now < p.shieldReadyAt) return;
        AttributeInstance max = p.owner.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (max == null || healthAfter <= 0 || healthAfter / max.getValue() * 100 > a.shieldBelowPercent) return;
        if (!myTurn(p)) return;
        used(p);
        double hearts = a.shieldHearts.at(p.data.level, p.rarity.statMultiplier());
        int amp = Math.max(0, (int) Math.ceil(hearts / 2.0) - 1);
        p.owner.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, a.shieldTicks, amp, true, false, true));
        p.shieldReadyAt = now + a.fullCooldownTicks() * 50L;
        p.shieldDebuffUntil = now + a.shieldTicks * 50L;
        fx(p, a, p.owner);
        announce(p, a);
        if (a.blastRadius > 0) {
            // 0.8.0: the shield comes up with a shockwave that pushes enemies away (Sculk)
            Location o = p.owner.getLocation();
            Effects.particle(o.clone().add(0, 1, 0), "SONIC_BOOM", 1, 0, 0);
            burst(a, o, a.blastRadius);
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
                vfx.add(new Vfx(visual, at, p.owner, 0, Math.min(a.shieldTicks, a.vfx.life()), false, a.vfx.yOffset()));
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
        if (++ticks % 1200 == 0 && (!cooldowns.isEmpty() || !ownerReady.isEmpty())) {
            long now = System.currentTimeMillis();
            cooldowns.values().removeIf(c -> { for (long x : c) if (x > now) return false; return true; });
            ownerReady.values().removeIf(t -> t <= now);
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
        /** Follows the target's body rotation (wings stay behind the owner). */
        boolean turn;
        /** 0.14.0: starts here and slides to the followed entity over travelTicks. */
        Location travelFrom;
        int travelTicks;
        int age;
        /** Moves a free (not following) vfx each tick: the falling meteor. */
        java.util.function.UnaryOperator<Location> mover;

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
            if (mover != null) {
                loc = mover.apply(loc);
                if (visual != null) visual.move(loc, loc.getYaw(), loc.getYaw());
                return true;
            }
            if (follow != null) {
                if (!follow.isValid() || follow.isDead()) return false;
                Location at = follow.getLocation().add(0, yOffset, 0);
                if (travelFrom != null && age < travelTicks && travelFrom.getWorld() == at.getWorld()) {
                    double k = (double) ++age / travelTicks;
                    at = travelFrom.clone().add(at.toVector().subtract(travelFrom.toVector()).multiply(k));
                }
                at.setYaw(turn ? follow.getBodyYaw() : loc.getYaw());
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
