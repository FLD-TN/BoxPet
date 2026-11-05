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

    /** True while BoxPet deals damage: its own listeners ignore those events. */
    private static boolean dealing;

    private record Mark(UUID owner, double bonus, long until) {
    }

    private final BoxPetPlugin plugin;
    private final List<Vfx> vfx = new ArrayList<>();
    private final Map<UUID, Mark> marks = new HashMap<>();
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
                fx(p, a, o);
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
            case PROJECTILE, STRIKE, MARK -> {
                if (p.cooldown > 0 || !plugin.pets().combat().tagged(o.getUniqueId())) return;
                LivingEntity target = plugin.pets().combat().target(o.getUniqueId(), 5000);
                if (target == null || !validTarget(o, target)) return;
                Location from = origin(p);
                if (from.getWorld() != target.getWorld() || from.distanceSquared(target.getLocation()) > a.range * a.range) return;
                if (!o.hasLineOfSight(target)) return;
                p.cooldown = a.cooldownTicks;
                cast(p, a, target);
            }
            default -> {
            }
        }
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
            if (plugin.pets().active(p.ownerId) != p || p.abilityPaused() || !target.isValid() || target.isDead()) return;
            if (!validTarget(p.owner, target)) return;
            switch (a.type) {
                case PROJECTILE -> launch(p, a, target);
                case STRIKE -> {
                    showAt(p, a, target);
                    hit(p, a, target);
                }
                case MARK -> {
                    double bonus = a.markBonus.at(p.data.level, p.rarity.statMultiplier());
                    marks.put(target.getUniqueId(), new Mark(p.ownerId, bonus, System.currentTimeMillis() + a.markTicks * 50L));
                    showAt(p, a, target);
                    if (a.damage.base() > 0) hit(p, a, target);
                    particles(p, a, target, true);
                }
                default -> {
                }
            }
        }, a.castDelay);
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
        if (!damage(p, a, target, base, false)) return;
        applyDebuff(p, a, target, base);
    }

    private boolean damage(ActivePet p, Ability a, LivingEntity target, double amount, boolean dot) {
        Player o = p.owner;
        if (!o.isOnline() || !validTarget(o, target)) return false;
        double mult = target instanceof Player ? a.pvp(plugin.settings().defaultPvpMultiplier) : a.pveMultiplier;
        double dmg = amount * mult;
        PetAbilityDamageEvent ev = new PetAbilityDamageEvent(o, plugin.pets().view(p.data), target, a.id, dmg);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled() || ev.getDamage() <= 0) return false;
        dealing = true;
        try {
            // the owner's stats as a skill attack: MMOItems skill damage, magic defence, PvP damage apply
            var caster = MMOPlayerData.get(o).getStatMap().cache(io.lumine.mythic.lib.api.player.EquipmentSlot.MAIN_HAND);
            if (dot) caster.attack(target, ev.getDamage(), DamageType.SKILL, DamageType.MAGIC, DamageType.DOT);
            else caster.attack(target, ev.getDamage(), DamageType.SKILL, DamageType.MAGIC);
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().log(Level.WARNING, "MythicLib attack failed, using plain damage", ex);
            target.damage(ev.getDamage(), o);
        } finally {
            dealing = false;
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
        Ability a = p.ability;
        if (a == null || a.type != Ability.Type.SHIELD || p.abilityPaused()) return;
        long now = System.currentTimeMillis();
        if (now < p.shieldReadyAt) return;
        AttributeInstance max = p.owner.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (max == null || healthAfter <= 0 || healthAfter / max.getValue() * 100 > a.shieldBelowPercent) return;
        double hearts = a.shieldHearts.at(p.data.level, p.rarity.statMultiplier());
        int amp = Math.max(0, (int) Math.ceil(hearts / 2.0) - 1);
        p.owner.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, a.shieldTicks, amp, true, false, true));
        p.shieldReadyAt = now + a.cooldownTicks * 50L;
        fx(p, a, p.owner);
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
