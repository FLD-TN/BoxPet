package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.config.Rarity;
import com.dex.boxpet.data.PetData;
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
 * Opening an egg (spec 10.2). The pet is already created and saved; random eggs get a gacha strip that slows down
 * and stops on the result. Closing early only skips the animation.
 */
public final class HatchMenu extends Menu {

    private static final int[] STRIP = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] DELAYS = {1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 4, 4, 5, 6, 8, 10};

    private final PlayerData pd;
    private final PetData pet;
    private final boolean random;
    private final List<ItemStack> reel = new ArrayList<>();
    private int step;
    private int wait;
    private boolean done;
    private BukkitTask task;

    public HatchMenu(BoxPetPlugin plugin, Player player, PlayerData pd, PetData pet, boolean random) {
        super(plugin, player, 3, plugin.menus().title("hatch"));
        this.pd = pd;
        this.pet = pet;
        this.random = random;
        Rarity r = plugin.pets().rarity(pet);
        List<PetType> pool = random ? plugin.incubator().pool(r) : List.of();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int total = random ? DELAYS.length + STRIP.length : 0;
        for (int i = 0; i < total; i++) {
            PetType t = pool.isEmpty() ? plugin.pets().type(pet) : pool.get(rnd.nextInt(pool.size()));
            reel.add(t == null ? new ItemStack(Material.EGG) : decorate(PetIcons.icon(plugin, t), "<" + r.color() + ">" + t.name, List.of()));
        }
        // the result lands in the centre (strip index 3) on the last step
        if (random) reel.set(DELAYS.length + 3, resultIcon());
    }

    private ItemStack resultIcon() {
        PetType t = plugin.pets().type(pet);
        return t == null ? new ItemStack(Material.EGG) : decorate(PetIcons.icon(plugin, t), plugin.pets().displayName(pet), List.of());
    }

    @Override
    protected void draw() {
        Rarity r = plugin.pets().rarity(pet);
        Material pane = paneFor(r);
        for (int i = 0; i < 27; i++) set(i, icon(i == 4 || i == 22 ? Material.HOPPER : pane, " ", List.of()), null);
        if (!random || done) {
            showResult();
            return;
        }
        for (int i = 0; i < STRIP.length; i++) set(STRIP[i], reel.get(Math.min(reel.size() - 1, step + i)), null);
    }

    private Material paneFor(Rarity r) {
        return switch (r.index()) {
            case 0 -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            case 1 -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case 2 -> Material.PURPLE_STAINED_GLASS_PANE;
            default -> Material.YELLOW_STAINED_GLASS_PANE;
        };
    }

    private void showResult() {
        set(13, PetIcons.card(plugin, pet, List.of(), List.of(plugin.menus().text("actions.detail"))),
                click -> new PetDetailMenu(plugin, player, pd, pet, false).open());
        set(22, button("hatch.storage", Material.CHEST), click -> new StorageMenu(plugin, player, pd, false, 0).open());
    }

    @Override
    protected void onOpen() {
        if (!random) {
            finish();
            return;
        }
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
        announce();
    }

    private void announce() {
        Rarity r = plugin.pets().rarity(pet);
        plugin.messages().title(player, "hatched", "pet", plugin.pets().displayName(pet), "rarity", r.coloured());
        plugin.messages().send(player, "incubator.hatched", "pet", plugin.pets().displayName(pet), "rarity", r.coloured());
        player.playSound(player.getLocation(), r.index() >= 3 ? "ui.toast.challenge_complete" : "entity.player.levelup", 0.8f, 1.2f);
    }

    @Override
    protected void onClose() {
        if (!done) finish();
    }
}
