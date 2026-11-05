package com.dex.boxpet.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Vault money (evolution costs). Only loaded when Vault is enabled. */
public final class VaultHook {

    private Economy economy() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    public boolean available() {
        return economy() != null;
    }

    public double balance(OfflinePlayer p) {
        Economy e = economy();
        return e == null ? 0 : e.getBalance(p);
    }

    public boolean withdraw(OfflinePlayer p, double amount) {
        Economy e = economy();
        if (e == null) return false;
        if (amount <= 0) return true;
        return e.withdrawPlayer(p, amount).transactionSuccess();
    }
}
