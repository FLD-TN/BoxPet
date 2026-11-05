package com.dex.boxpet.pet;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/** Particles and sounds by name from the config (1.21 names), with the "puff" used for appear / disappear. */
public final class Effects {

    private static final Map<String, Particle> PARTICLES = new ConcurrentHashMap<>();
    private static final Particle NONE_MARKER = Particle.CLOUD;

    private Effects() {
    }

    /** Small cloud + soft sound (spec 4.5). */
    public static void puff(Location at) {
        World w = at.getWorld();
        if (w == null) return;
        w.spawnParticle(Particle.CLOUD, at.clone().add(0, 0.4, 0), 10, 0.25, 0.25, 0.25, 0.02);
        w.spawnParticle(Particle.POOF, at.clone().add(0, 0.4, 0), 6, 0.2, 0.2, 0.2, 0.01);
        sound(at, "entity.illusioner.mirror_move", 0.4f, 1.4f);
    }

    public static void hearts(Location at) {
        World w = at.getWorld();
        if (w != null) w.spawnParticle(Particle.HEART, at, 5, 0.3, 0.3, 0.3, 0);
    }

    public static void sound(Location at, String key, float volume, float pitch) {
        World w = at.getWorld();
        if (w == null || key == null || key.isEmpty()) return;
        w.playSound(at, key.toLowerCase(Locale.ROOT), SoundCategory.NEUTRAL, volume, pitch);
    }

    public static void particle(Location at, String name, int count, double spread, double speed) {
        Particle p = particle(name);
        World w = at.getWorld();
        if (p == null || w == null) return;
        w.spawnParticle(p, at, Math.max(1, count), spread, spread, spread, speed);
    }

    /** Only particles without extra data (no DUST, BLOCK...). {@code null} for unknown names. */
    public static @Nullable Particle particle(String name) {
        if (name == null || name.isEmpty()) return null;
        Particle p = PARTICLES.computeIfAbsent(name.toUpperCase(Locale.ROOT), n -> {
            try {
                Particle found = Particle.valueOf(n);
                return found.getDataType() == Void.class ? found : NONE_MARKER;
            } catch (IllegalArgumentException ex) {
                return NONE_MARKER;
            }
        });
        if (p == NONE_MARKER && !name.equalsIgnoreCase("CLOUD")) return null;
        return p;
    }
}
