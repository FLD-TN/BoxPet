package com.dex.boxpet.hook;

import com.dex.boxcore.api.BonusProvider;
import com.dex.boxcore.api.BoostService;
import com.dex.boxcore.api.GemResult;
import com.dex.boxcore.api.GemService;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * BoxCore: gems (incubator speed-up) and the pet boost bonus (BoxCore 1.6.0). Only loaded when BoxCore is enabled;
 * every call is guarded so an older BoxCore never breaks BoxPet.
 */
public final class BoxCoreHook {

    private final Logger log;
    private boolean bonusSupported;

    public BoxCoreHook(Logger log) {
        this.log = log;
    }

    private static <T> T service(Class<T> type) {
        return Bukkit.getServicesManager().load(type);
    }

    /** Registers the pet bonus. Logs once when BoxCore is older than 1.6.0. */
    public void registerBonus(Plugin plugin, BonusProvider provider) {
        BoostService boosts = service(BoostService.class);
        if (boosts == null) return;
        try {
            BoostService.class.getMethod("registerBonusProvider", Plugin.class, BonusProvider.class);
            boosts.registerBonusProvider(plugin, provider);
            bonusSupported = true;
        } catch (NoSuchMethodException | LinkageError ex) {
            log.warning("BoxCore is older than 1.6.0: boost pets only give their stats until BoxCore is updated");
        }
    }

    public boolean bonusSupported() {
        return bonusSupported;
    }

    public void refreshBonus(UUID player) {
        if (!bonusSupported) return;
        BoostService boosts = service(BoostService.class);
        if (boosts != null) boosts.refreshBonusOf(player);
    }

    public boolean isBoostType(String type) {
        BoostService boosts = service(BoostService.class);
        return boosts != null && boosts.isType(type);
    }

    public boolean gemsAvailable() {
        return service(GemService.class) != null;
    }

    public long gems(UUID player) {
        GemService g = service(GemService.class);
        return g == null ? 0 : g.balance(player);
    }

    /** Takes gems. @return true when paid */
    public boolean withdrawGems(UUID player, long amount, String reason) {
        GemService g = service(GemService.class);
        if (g == null) return false;
        if (amount <= 0) return true;
        return g.withdraw(player, amount, reason) == GemResult.SUCCESS;
    }

    public String formatGems(long amount) {
        GemService g = service(GemService.class);
        return g == null ? String.valueOf(amount) : g.format(amount);
    }
}
