package com.dex.boxpet.hook;

import com.dex.boxclans.api.ClanService;
import java.util.UUID;
import org.bukkit.Bukkit;

/**
 * BoxClans: pets never hit members of their owner's clan. BoxClans has no alliances (relation is SELF, SAME_CLAN or
 * NONE), so "allies" means the same clan. Only loaded when BoxClans is enabled.
 */
public final class ClanHook {

    public boolean sameClan(UUID a, UUID b) {
        ClanService s = Bukkit.getServicesManager().load(ClanService.class);
        if (s == null) return false;
        try {
            return s.sameClan(a, b);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
