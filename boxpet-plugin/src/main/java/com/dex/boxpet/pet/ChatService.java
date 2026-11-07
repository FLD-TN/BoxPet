package com.dex.boxpet.pet;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.Messages;
import com.dex.boxpet.text.Text;
import java.util.Set;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

/**
 * The pet talks to its owner (spec 11.4): only the owner sees it, in chat and in an optional speech bubble. A global
 * cooldown per player stops spam, except for the important events.
 */
public final class ChatService {

    private static final Set<String> NO_COOLDOWN = Set.of("level-up", "hungry", "evolve", "birthday", "owner-died",
            "full", "summon", "fed", "petted", "arrived", "talk", "looked");

    private final BoxPetPlugin plugin;

    public ChatService(BoxPetPlugin plugin) {
        this.plugin = plugin;
    }

    public void say(ActivePet pet, String event, String... placeholders) {
        if (!pet.player.petChat || !pet.owner.isOnline()) return;
        long now = System.currentTimeMillis();
        if (!NO_COOLDOWN.contains(event) && now - pet.lastChat < plugin.settings().chatCooldownSeconds * 1000L) return;
        String line = plugin.dialogues().pick(pet.type.dialogue, event);
        if (line == null || line.isEmpty()) return;
        pet.lastChat = now;
        String name = plugin.pets().displayName(pet.data);
        String[] all = new String[placeholders.length + 6];
        all[0] = "pet";
        all[1] = name;
        all[2] = "owner";
        all[3] = pet.owner.getName();
        all[4] = "level";
        all[5] = String.valueOf(pet.data.level);
        System.arraycopy(placeholders, 0, all, 6, placeholders.length);
        String text = Messages.apply(line, all);
        String full = Messages.apply(plugin.dialogues().format, "pet", name, "line", text);
        pet.owner.sendMessage(Text.parse(full, pet.owner));
        if (plugin.settings().speechBubble) bubble(pet, Messages.apply(plugin.dialogues().bubbleFormat, "line", text, "pet", name));
    }

    private void bubble(ActivePet pet, String text) {
        if (!pet.shown() || pet.showcase != null) return;
        removeBubble(pet);
        Location at = bubbleLocation(pet);
        TextDisplay td = at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.text(Text.parse(text, pet.owner));
            d.setLineWidth(160);
            d.setBackgroundColor(Color.fromARGB(150, 20, 20, 30));
            d.setShadowed(false);
            d.setTeleportDuration(2);
        });
        pet.owner.showEntity(plugin, td);
        pet.bubble = td;
        pet.bubbleTicks = plugin.settings().speechBubbleTicks;
    }

    Location bubbleLocation(ActivePet pet) {
        double h = pet.visual == null ? 1 : pet.visual.height();
        Location at = pet.pos.clone().add(0, h + plugin.settings().nametagOffset + 0.45, 0);
        // VERTICAL billboard keeps the entity's own pitch: a pet looking up / down (flying spirits) tilted the text
        at.setPitch(0);
        at.setYaw(0);
        return at;
    }

    /** Called every tick by the manager. */
    void tickBubble(ActivePet pet) {
        if (pet.bubble == null) return;
        if (--pet.bubbleTicks <= 0 || !pet.shown()) {
            removeBubble(pet);
            return;
        }
        Location at = bubbleLocation(pet);
        if (at.getWorld() == pet.bubble.getWorld() && at.distanceSquared(pet.bubble.getLocation()) > 0.0004) pet.bubble.teleport(at);
    }

    // ------------------------------------------------------------------ show-off (0.9.7)

    private final java.util.Map<java.util.UUID, Long> showcaseReady = new java.util.HashMap<>();

    /** @return message key */
    public String showcase(ActivePet pet) {
        if (!pet.shown()) return "showcase.hidden";
        long now = System.currentTimeMillis();
        Long ready = showcaseReady.get(pet.ownerId);
        if (ready != null && now < ready && !com.dex.boxpet.listener.InteractListener.bypass(pet.owner)) return "showcase.cooldown";
        // 0.20.5: expired entries go, the map never grows with every player who ever showed off
        if (showcaseReady.size() > 64) showcaseReady.values().removeIf(t -> t <= now);
        showcaseReady.put(pet.ownerId, now + plugin.settings().showcaseCooldownSeconds * 1000L);
        removeBubble(pet);
        removeShowcase(pet);
        String text = String.join("\n<reset>", com.dex.boxpet.gui.PetIcons.showcaseLines(plugin, pet.data));
        Location at = bubbleLocation(pet);
        TextDisplay td = at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            // VERTICAL: turns to face the viewer but never tilts (CENTER leaned back when looking up at it)
            d.setBillboard(Display.Billboard.VERTICAL);
            d.text(Text.parse(text, pet.owner));
            d.setLineWidth(400);
            d.setBackgroundColor(Color.fromARGB(170, 15, 15, 25));
            d.setShadowed(false);
            d.setTeleportDuration(2);
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(0.6f, 0.6f, 0.6f), new org.joml.AxisAngle4f()));
        });
        pet.showcase = td;
        pet.showcaseTicks = plugin.settings().showcaseTicks;
        return "showcase.done";
    }

    // ------------------------------------------------------------------ show off in chat (0.21.0)

    /** Owner -> "[name]" with the pet card on hover, built on the main thread, read by the async chat event. */
    private final java.util.Map<java.util.UUID, net.kyori.adventure.text.Component> chatCards = new java.util.concurrent.ConcurrentHashMap<>();

    public void cacheCard(ActivePet pet) {
        try {
            String lines = String.join("\n<reset>", com.dex.boxpet.gui.PetIcons.showcaseLines(plugin, pet.data));
            net.kyori.adventure.text.Component hover = Text.parse(lines, pet.owner);
            net.kyori.adventure.text.Component name = Text.parse(plugin.messages().raw("showcase-chat.tag",
                    "pet", plugin.pets().nameWithLevel(pet.data)), pet.owner);
            chatCards.put(pet.ownerId, name.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hover)));
        } catch (RuntimeException ex) {
            chatCards.remove(pet.ownerId);
        }
    }

    public void dropCard(java.util.UUID owner) {
        chatCards.remove(owner);
    }

    /** Thread-safe: the "[name]" component of the owner's active pet, or null. */
    public net.kyori.adventure.text.@org.jetbrains.annotations.Nullable Component card(java.util.UUID owner) {
        return chatCards.get(owner);
    }

    /** Seconds before this player may show off again (0 = now). */
    public long showcaseWait(java.util.UUID player) {
        Long ready = showcaseReady.get(player);
        return ready == null ? 0 : Math.max(0, (ready - System.currentTimeMillis() + 999) / 1000);
    }

    void tickShowcase(ActivePet pet) {
        if (pet.showcase == null) return;
        if (--pet.showcaseTicks <= 0 || !pet.shown()) {
            removeShowcase(pet);
            return;
        }
        Location at = bubbleLocation(pet);
        // stands still over the pet: ignores the small bobbing of a flying / walking pet, follows real moves only
        if (at.getWorld() != pet.showcase.getWorld() || at.distanceSquared(pet.showcase.getLocation()) > 0.36) pet.showcase.teleport(at);
    }

    void removeShowcase(ActivePet pet) {
        if (pet.showcase != null) {
            pet.showcase.remove();
            pet.showcase = null;
        }
    }

    void removeBubble(ActivePet pet) {
        if (pet.bubble != null) {
            pet.bubble.remove();
            pet.bubble = null;
        }
    }
}
