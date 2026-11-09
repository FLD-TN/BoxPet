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

    /**
     * 0.25.4: the exact text the player typed (same approach as BoxClans). {@code e.message()} is not used: chat
     * plugins (EzChat, Essentials, CMI...) rewrite it before us, turning {@code &d} into a real colour or stripping
     * it, which made pet names lose their colours. The signed message is what the client sent; the original message
     * (written back as {@code &} codes, hex as {@code &#RRGGBB}) is the fallback for unsigned chat.
     */
    static String typed(AsyncChatEvent e) {
        try {
            if (e.signedMessage() != null) {
                String raw = e.signedMessage().message();
                if (raw != null) return raw;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // unsigned chat or older API: use the original component below
        }
        return AMPERSAND.serialize(e.originalMessage());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Pending p = pending.remove(e.getPlayer().getUniqueId());
        if (p == null) return;
        e.setCancelled(true);
        p.timeout().cancel();
        String text = typed(e).trim();
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
