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
            d.setBillboard(Display.Billboard.CENTER);
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
        return pet.pos.clone().add(0, h + plugin.settings().nametagOffset + 0.45, 0);
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
        if (ready != null && now < ready) return "showcase.cooldown";
        showcaseReady.put(pet.ownerId, now + plugin.settings().showcaseCooldownSeconds * 1000L);
        removeBubble(pet);
        removeShowcase(pet);
        String text = String.join("\n<reset>", com.dex.boxpet.gui.PetIcons.showcaseLines(plugin, pet.data));
        Location at = bubbleLocation(pet);
        TextDisplay td = at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
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

    void tickShowcase(ActivePet pet) {
        if (pet.showcase == null) return;
        if (--pet.showcaseTicks <= 0 || !pet.shown()) {
            removeShowcase(pet);
            return;
        }
        Location at = bubbleLocation(pet);
        if (at.getWorld() == pet.showcase.getWorld() && at.distanceSquared(pet.showcase.getLocation()) > 0.0004) pet.showcase.teleport(at);
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
