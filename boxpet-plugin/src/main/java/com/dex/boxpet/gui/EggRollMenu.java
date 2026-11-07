package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.config.Rarity;
import com.dex.boxpet.data.PlayerData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * 0.23.0: opening a random egg. The pet egg is already in the player's inventory (saved) before this opens; the strip
 * only shows which pet came out. Closing early skips the animation.
 */
public final class EggRollMenu extends Menu {

    private static final int[] STRIP = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] DELAYS = {1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 4, 4, 5, 6, 8, 10};

    private final PlayerData pd;
    private final PetType result;
    private final Rarity rarity;
    private final ItemStack egg;
    private final List<ItemStack> reel = new ArrayList<>();
    private int step;
    private int wait;
    private boolean done;
    private BukkitTask task;

    public EggRollMenu(BoxPetPlugin plugin, Player player, PlayerData pd, PetType result, Rarity rarity, ItemStack egg) {
        super(plugin, player, 3, plugin.menus().title("hatch"));
        this.pd = pd;
        this.result = result;
        this.rarity = rarity;
        this.egg = egg.clone();
        List<PetType> pool = plugin.incubator().pool(rarity);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < DELAYS.length + STRIP.length; i++) {
            PetType t = pool.isEmpty() ? result : pool.get(rnd.nextInt(pool.size()));
            reel.add(decorate(PetIcons.icon(plugin, t), t.colouredName(), List.of()));
        }
        // the result lands in the centre (strip index 3) on the last step
        reel.set(DELAYS.length + 3, decorate(PetIcons.icon(plugin, result), result.colouredName(), List.of()));
    }

    @Override
    protected void draw() {
        Material pane = switch (rarity.index()) {
            case 0 -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            case 1 -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case 2 -> Material.PURPLE_STAINED_GLASS_PANE;
            default -> Material.YELLOW_STAINED_GLASS_PANE;
        };
        for (int i = 0; i < 27; i++) set(i, icon(i == 4 || i == 22 ? Material.HOPPER : pane, " ", List.of()), null);
        if (done) {
            set(13, egg, null);
            set(22, button("common.back", Material.ARROW), click -> new IncubatorMenu(plugin, player, pd).open());
            return;
        }
        for (int i = 0; i < STRIP.length; i++) set(STRIP[i], reel.get(Math.min(reel.size() - 1, step + i)), null);
    }

    @Override
    protected void onOpen() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (done) return;
            if (wait-- > 0) return;
            if (step >= DELAYS.length) {
                finish();
                return;
            }
            wait = DELAYS[step];
            step++;
            refresh();
            player.playSound(player.getLocation(), "block.note_block.hat", 0.5f, 1.0f + step * 0.04f);
        }, 1L, 1L);
    }

    private void finish() {
        if (done) return;
        done = true;
        if (task != null) task.cancel();
        if (player.getOpenInventory().getTopInventory().getHolder(false) == this) refresh();
        String name = plugin.itemFactory().eggName(result);
        plugin.messages().title(player, "egg-opened", "egg", name, "rarity", rarity.coloured());
        plugin.messages().send(player, "egg.opened", "egg", name, "rarity", rarity.coloured());
        player.playSound(player.getLocation(), rarity.index() >= 3 ? "ui.toast.challenge_complete" : "entity.player.levelup", 0.8f, 1.2f);
    }

    @Override
    protected void onClose() {
        if (!done) finish();
    }
}
