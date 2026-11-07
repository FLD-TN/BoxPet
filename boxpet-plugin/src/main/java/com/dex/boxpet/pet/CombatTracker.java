package com.dex.boxpet.pet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Combat tags (spec 4.1, 11.1): a player is "in combat" for {@code combat-tag-seconds} after hitting or being hit by
 * a living entity. Also remembers who the player hit last and who hit them last, for pet targeting (spec 6.1).
 */
public final class CombatTracker {

    private static final class State {
        long taggedUntil;
        UUID lastTarget;
        long lastTargetAt;
        UUID lastAttacker;
        long lastAttackerAt;
    }

    private final Map<UUID, State> states = new HashMap<>();
    private int tagMillis;

    public CombatTracker(int tagSeconds) {
        setTagSeconds(tagSeconds);
    }

    public void setTagSeconds(int seconds) {
        this.tagMillis = seconds * 1000;
    }

    private State state(UUID id) {
        return states.computeIfAbsent(id, k -> new State());
    }

    /** {@code attacker} hit {@code victim}; either may be a non-player. */
    public void hit(@Nullable Player attacker, Entity victim, @Nullable LivingEntity attackerEntity) {
        long now = System.currentTimeMillis();
        if (attacker != null) {
            State s = state(attacker.getUniqueId());
            s.taggedUntil = now + tagMillis;
            s.lastTarget = victim.getUniqueId();
            s.lastTargetAt = now;
        }
        if (victim instanceof Player p && attackerEntity != null && attackerEntity != p) {
            State s = state(p.getUniqueId());
            s.taggedUntil = now + tagMillis;
            s.lastAttacker = attackerEntity.getUniqueId();
            s.lastAttackerAt = now;
        }
    }

    /** 0.13.3: in combat (menus locked) without becoming anyone's target or attacker. */
    public void tagOnly(Player player) {
        state(player.getUniqueId()).taggedUntil = System.currentTimeMillis() + tagMillis;
    }

    public boolean tagged(UUID player) {
        State s = states.get(player);
        return s != null && s.taggedUntil > System.currentTimeMillis();
    }

    /** Remaining tag in ms (0 when not tagged). */
    public long remaining(UUID player) {
        State s = states.get(player);
        return s == null ? 0 : Math.max(0, s.taggedUntil - System.currentTimeMillis());
    }

    /**
     * The pet's target: whoever the owner hit last, else whoever hit the owner last, both within
     * {@code windowMillis}. Returns the entity only when it is still alive and loaded.
     */
    public @Nullable LivingEntity target(UUID owner, long windowMillis) {
        State s = states.get(owner);
        if (s == null) return null;
        long now = System.currentTimeMillis();
        LivingEntity t = null;
        if (s.lastTarget != null && now - s.lastTargetAt <= windowMillis) t = living(s.lastTarget);
        if (t == null && s.lastAttacker != null && now - s.lastAttackerAt <= windowMillis) t = living(s.lastAttacker);
        return t;
    }

    /** 0.21.0: who the owner hit last (within the window), alive, or null. */
    public @Nullable LivingEntity hitByOwner(UUID owner, long windowMillis) {
        State s = states.get(owner);
        if (s == null || s.lastTarget == null || System.currentTimeMillis() - s.lastTargetAt > windowMillis) return null;
        return living(s.lastTarget);
    }

    /** 0.21.0: who hit the owner last (within the window), alive, or null. */
    public @Nullable LivingEntity attackerOf(UUID owner, long windowMillis) {
        State s = states.get(owner);
        if (s == null || s.lastAttacker == null || System.currentTimeMillis() - s.lastAttackerAt > windowMillis) return null;
        return living(s.lastAttacker);
    }

    private static @Nullable LivingEntity living(UUID id) {
        Entity e = Bukkit.getEntity(id);
        return e instanceof LivingEntity le && le.isValid() && !le.isDead() ? le : null;
    }

    public void forget(UUID player) {
        states.remove(player);
    }
}
