package com.dex.boxpet.hook;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;

/**
 * CataMines (dex fork) through reflection: the bounding boxes of every mine, refreshed every minute. A block broken
 * inside one counts as mining (pet XP). Reads {@code MineManager.getInstance().getMines()} and each mine's WorldEdit
 * region; any failure only turns the feature off.
 */
public final class CataMinesHook {

    private record Box(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(Block b) {
            return b.getWorld().getName().equals(world) && b.getX() >= minX && b.getX() <= maxX
                    && b.getY() >= minY && b.getY() <= maxY && b.getZ() >= minZ && b.getZ() <= maxZ;
        }
    }

    private final Logger log;
    private volatile List<Box> boxes = List.of();
    private boolean failed;

    public CataMinesHook(Logger log) {
        this.log = log;
    }

    public static boolean present() {
        return Bukkit.getPluginManager().isPluginEnabled("CataMines");
    }

    /** Main thread. */
    public void refresh() {
        if (failed || !present()) return;
        try {
            Class<?> manager = Class.forName("de.c4t4lysm.catamines.schedulers.MineManager");
            Object instance = manager.getMethod("getInstance").invoke(null);
            List<?> mines = (List<?>) manager.getMethod("getMines").invoke(instance);
            List<Box> out = new ArrayList<>();
            for (Object mine : mines) {
                Object region = mine.getClass().getMethod("getRegion").invoke(mine);
                if (region == null) continue;
                Object world = region.getClass().getMethod("getWorld").invoke(region);
                String worldName = world == null ? null : (String) world.getClass().getMethod("getName").invoke(world);
                if (worldName == null) continue;
                Object min = region.getClass().getMethod("getMinimumPoint").invoke(region);
                Object max = region.getClass().getMethod("getMaximumPoint").invoke(region);
                out.add(new Box(worldName, coord(min, "x"), coord(min, "y"), coord(min, "z"),
                        coord(max, "x"), coord(max, "y"), coord(max, "z")));
            }
            boxes = List.copyOf(out);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            failed = true;
            log.warning("CataMines mines could not be read (" + ex + "): mining XP only counts configured WorldGuard regions");
        }
    }

    /** WorldEdit 7.3 uses x(), older versions getBlockX(). */
    private static int coord(Object vector, String axis) throws ReflectiveOperationException {
        Method m;
        try {
            m = vector.getClass().getMethod(axis);
        } catch (NoSuchMethodException ex) {
            m = vector.getClass().getMethod("getBlock" + axis.toUpperCase());
        }
        return ((Number) m.invoke(vector)).intValue();
    }

    public boolean inMine(Block block) {
        for (Box b : boxes) if (b.contains(block)) return true;
        return false;
    }
}
