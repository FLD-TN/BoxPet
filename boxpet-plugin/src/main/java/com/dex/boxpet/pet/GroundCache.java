package com.dex.boxpet.pet;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Block reads of one pet (ground height under a column, free space at a block), remembered until the owner moves to
 * another block or {@link #LIFE_TICKS} pass. Without it the follower reads 10-45 blocks per pet per tick in tight
 * places; with it the same answers are reused while the owner stays in the same block. Main thread only.
 */
final class GroundCache {

    static final int LIFE_TICKS = 10;
    /** Safety cap: the cache is cleared well before this, it only bounds memory if something goes wrong. */
    private static final int MAX_ENTRIES = 256;

    private final Map<Long, Double> ground = new HashMap<>();
    private final Map<Long, Boolean> free = new HashMap<>();
    private long ownerBlock = Long.MIN_VALUE;
    private World world;
    private int bornTick;

    /** Clears the cache when the owner changed block / world or it got old. Call once per tick before use. */
    void validate(Location owner) {
        long key = pack(owner.getBlockX(), owner.getBlockY(), owner.getBlockZ());
        int now = Bukkit.getCurrentTick();
        if (key != ownerBlock || owner.getWorld() != world || now - bornTick >= LIFE_TICKS
                || ground.size() + free.size() > MAX_ENTRIES) {
            ground.clear();
            free.clear();
            ownerBlock = key;
            world = owner.getWorld();
            bornTick = now;
        }
    }

    /**
     * Top of the first solid block under the column, scanning down from just above the owner's feet; NaN means
     * "use the owner's height" (water, nothing solid near, chunk not loaded).
     */
    double groundTop(World w, double x, double ownerY, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        long key = pack(bx, 0, bz);
        Double cached = ground.get(key);
        if (cached != null) return cached;
        double v = scan(w, bx, ownerY, bz);
        ground.put(key, v);
        return v;
    }

    private static double scan(World w, int bx, double y, int bz) {
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return Double.NaN;
        Block feet = w.getBlockAt(bx, (int) Math.floor(y), bz);
        if (feet.isLiquid()) return Double.NaN;
        int start = (int) Math.floor(y + 1.0);
        for (int by = start; by >= start - 6 && by > w.getMinHeight(); by--) {
            Block b = w.getBlockAt(bx, by, bz);
            if (b.isLiquid()) return Double.NaN;
            if (!b.isPassable()) {
                double top = b.getBoundingBox().getMaxY();
                return top > by ? top : by + 1;
            }
        }
        return Double.NaN;
    }

    /** Feet and head blocks free at that spot (unloaded chunks count as free). */
    boolean free(Location at) {
        World w = at.getWorld();
        int bx = at.getBlockX();
        int by = (int) Math.floor(at.getY() + 0.05);
        int bz = at.getBlockZ();
        long key = pack(bx, by, bz);
        Boolean cached = free.get(key);
        if (cached != null) return cached;
        boolean v;
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) {
            v = true;
        } else {
            Block feet = w.getBlockAt(bx, by, bz);
            v = feet.isPassable() && feet.getRelative(0, 1, 0).isPassable();
        }
        free.put(key, v);
        return v;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
