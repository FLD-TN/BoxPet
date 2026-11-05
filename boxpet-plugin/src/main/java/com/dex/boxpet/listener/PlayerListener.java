package com.dex.boxpet.listener;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.pet.ActivePet;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Join, quit, death, respawn, teleport and world change (spec 4.5). */
public final class PlayerListener implements Listener {

    private final BoxPetPlugin plugin;

    public PlayerListener(BoxPetPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        plugin.pets().join(e.getPlayer());
        // other players' pets the new player must not see
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) plugin.pets().applyHiddenFor(e.getPlayer());
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        plugin.input().cancel(e.getPlayer());
        plugin.pets().quit(e.getPlayer());
        plugin.interact().forget(e.getPlayer().getUniqueId());
        plugin.actions().forget(e.getPlayer().getUniqueId());
        plugin.xpListener().forget(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        ActivePet p = plugin.pets().active(e.getEntity().getUniqueId());
        if (p != null) plugin.pets().onOwnerDeath(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        ActivePet p = plugin.pets().active(e.getPlayer().getUniqueId());
        if (p != null) plugin.pets().onOwnerRespawn(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        ActivePet p = plugin.pets().active(e.getPlayer().getUniqueId());
        if (p == null || e.getTo() == null) return;
        if (e.getFrom().getWorld() != e.getTo().getWorld()) return; // handled by the world change event
        if (e.getFrom().distanceSquared(e.getTo()) > plugin.settings().teleportDistance * plugin.settings().teleportDistance) {
            if (p.carried) plugin.pets().toggleCarry(p);
            if (p.sitting) p.sitting = false;
            plugin.pets().onOwnerTeleport(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        ActivePet p = plugin.pets().active(e.getPlayer().getUniqueId());
        if (p == null) return;
        if (p.carried) plugin.pets().toggleCarry(p);
        p.sitting = false;
        plugin.pets().onWorldChange(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        ActivePet p = plugin.pets().active(e.getPlayer().getUniqueId());
        if (p != null) Bukkit.getScheduler().runTask(plugin, () -> plugin.pets().updateHiddenState(p));
    }
}
