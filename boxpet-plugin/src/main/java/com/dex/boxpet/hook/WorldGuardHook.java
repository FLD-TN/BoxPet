package com.dex.boxpet.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import java.util.Collection;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * WorldGuard 7: the {@code pvp} flag and BoxPet's own flag {@code boxpet-ability} (deny = pet skills do nothing
 * there). The flag must be registered in {@code onLoad}, before WorldGuard loads its regions.
 */
public final class WorldGuardHook {

    private static StateFlag abilityFlag;

    /** Called from onLoad. Safe to call when WorldGuard is missing (the caller checks the class first). */
    public static void registerFlag(Logger log) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag flag = new StateFlag("boxpet-ability", true);
            registry.register(flag);
            abilityFlag = flag;
        } catch (FlagConflictException | IllegalStateException ex) {
            Flag<?> existing = registry.get("boxpet-ability");
            if (existing instanceof StateFlag sf) abilityFlag = sf;
            else log.warning("WorldGuard flag boxpet-ability could not be registered: " + ex.getMessage());
        }
    }

    private RegionQuery query() {
        return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
    }

    /** Pet skills may hit {@code target} (a player) at its location: pvp allowed and boxpet-ability not denied. */
    public boolean pvpAllowed(Player owner, Location at) {
        LocalPlayer lp = WorldGuardPlugin.inst().wrapPlayer(owner);
        ApplicableRegionSet set = query().getApplicableRegions(BukkitAdapter.adapt(at));
        if (!set.testState(lp, Flags.PVP)) return false;
        return abilityFlag == null || set.testState(lp, abilityFlag);
    }

    /** {@code boxpet-ability} not denied at the location (any target). */
    public boolean abilityAllowed(Player owner, Location at) {
        if (abilityFlag == null) return true;
        LocalPlayer lp = WorldGuardPlugin.inst().wrapPlayer(owner);
        return query().getApplicableRegions(BukkitAdapter.adapt(at)).testState(lp, abilityFlag);
    }

    /** True if the location is inside one of the named regions (lower case ids). */
    public boolean inRegion(Location at, Collection<String> regionIds) {
        if (regionIds.isEmpty()) return false;
        for (ProtectedRegion r : query().getApplicableRegions(BukkitAdapter.adapt(at))) {
            if (regionIds.contains(r.getId().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
