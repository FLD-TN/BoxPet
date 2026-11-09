package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

/** One chat line of input (rename). "huy" cancels; times out after menu.input-timeout-seconds. */
public final class ChatInput implements Listener {

    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    private record Pending(Consumer<String> onInput, Runnable onCancel, BukkitTask timeout) {
    }

    private final BoxPetPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInput(BoxPetPlugin plugin) {
        this.plugin = plugin;
    }

    public void ask(Player player, Consumer<String> onInput, Runnable onCancel) {
        cancel(player);
        BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending p = pending.remove(player.getUniqueId());
            if (p != null && player.isOnline()) {
                plugin.messages().send(player, "input.timeout");
                p.onCancel().run();
            }
        }, plugin.settings().inputTimeoutSeconds * 20L);
        pending.put(player.getUniqueId(), new Pending(onInput, onCancel, t));
    }

    public void cancel(Player player) {
        Pending p = pending.remove(player.getUniqueId());
        if (p != null) p.timeout().cancel();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Pending p = pending.remove(e.getPlayer().getUniqueId());
        if (p == null) return;
        e.setCancelled(true);
        p.timeout().cancel();
        // 0.25.3: a chat plugin may already have turned &d / &#RRGGBB into real colours before this runs; write them
        // back as & codes so the typed colours survive (same result as /pet rename). Text without colours is unchanged.
        String text = AMPERSAND.serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!e.getPlayer().isOnline()) return;
            if (text.equalsIgnoreCase("huy") || text.equalsIgnoreCase("hủy") || text.equalsIgnoreCase("cancel")) {
                plugin.messages().send(e.getPlayer(), "input.cancelled");
                p.onCancel().run();
            } else {
                p.onInput().accept(text);
            }
        });
    }
}
