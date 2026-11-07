package com.dex.boxpet.listener;

import com.dex.boxpet.BoxPetPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * 0.21.0: "[pet]" typed in chat becomes the name of the sender's active pet, with its card on hover. Runs on the chat
 * thread: it only reads the card cached on the main thread ({@code ChatService#card}).
 */
public final class ChatShowListener implements Listener {

    private static final Pattern TOKEN = Pattern.compile("(?i)\\[pet\\]");

    private final BoxPetPlugin plugin;

    public ChatShowListener(BoxPetPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!plugin.settings().chatShowToken) return;
        String plain = PlainTextComponentSerializer.plainText().serialize(e.message());
        if (!TOKEN.matcher(plain).find()) return;
        Component card = plugin.chat().card(e.getPlayer().getUniqueId());
        if (card == null) return;
        // once per message: no wall of cards
        e.message(e.message().replaceText(TextReplacementConfig.builder().match(TOKEN).once().replacement(card).build()));
    }
}
