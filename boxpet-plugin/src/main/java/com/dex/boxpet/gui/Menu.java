package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.text.Text;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Every BoxPet menu. Identified by its holder, never by its title. Nothing can be moved in or out
 * ({@link GuiListener}). Player menus never open while the player is combat-tagged (spec 11.1).
 */
public abstract class Menu implements InventoryHolder {

    public interface Handler {
        void click(ClickType click);
    }

    protected final BoxPetPlugin plugin;
    protected final Player player;
    private final Inventory inventory;
    private final Map<Integer, Handler> handlers = new HashMap<>();

    protected Menu(BoxPetPlugin plugin, Player player, int rows, String title) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, rows * 9, Text.parse(title));
    }

    /** 0.24.1: another inventory shape (a 3x3 dropper for the pet view). */
    protected Menu(BoxPetPlugin plugin, Player player, org.bukkit.event.inventory.InventoryType type, String title) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, type, Text.parse(title));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    protected abstract void draw();

    /** Staff menus open in combat too. */
    protected boolean combatLocked() {
        return true;
    }

    /** 0.20.6: true when what the menu is about is gone (a released / transferred pet). */
    protected boolean stale() {
        return false;
    }

    private boolean closeIfStale() {
        if (!stale()) return false;
        player.closeInventory();
        plugin.messages().send(player, "pet-gone");
        return true;
    }

    public void open() {
        if (closeIfStale()) return;
        if (combatLocked() && plugin.interact().combatLocked(player)) {
            player.closeInventory();
            plugin.interact().lockMessage(player);
            return;
        }
        refresh();
        player.openInventory(inventory);
        onOpen();
    }

    protected void onOpen() {
    }

    /** The menu was closed (by the player or by another menu opening). */
    protected void onClose() {
    }

    void closed() {
        onClose();
    }

    public void refresh() {
        handlers.clear();
        inventory.clear();
        draw();
    }

    protected void set(int slot, ItemStack item, @Nullable Handler handler) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
        if (handler == null) handlers.remove(slot);
        else handlers.put(slot, handler);
    }

    /** Fills empty slots with glass (spec 2). */
    protected void fill() {
        ItemStack pane = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, pane);
        }
    }

    void click(int slot, ClickType click) {
        Handler h = handlers.get(slot);
        if (h == null || closeIfStale()) return;
        h.click(click);
    }

    protected void onOwnItemClick(ItemStack item, int rawSlot, ClickType click) {
    }

    void ownItemClick(ItemStack item, int rawSlot, ClickType click) {
        if (closeIfStale()) return;
        onOwnItemClick(item, rawSlot, click);
    }

    protected void sound(String key) {
        player.playSound(player.getLocation(), key, 0.6f, 1.2f);
    }

    public static ItemStack icon(Material material, String name, List<String> lore) {
        return decorate(new ItemStack(material), name, lore);
    }

    /** Sets name (not bold, no italics) and lore, hides vanilla tooltips. */
    public static ItemStack decorate(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(Text.item(name));
        meta.lore(lore.stream().map(Text::item).toList());
        hideVanillaTooltip(meta);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Hides every vanilla line (attributes such as "When on horse: +7 Armor" of horse armour icons, enchants...).
     * Since 1.20.5 HIDE_ATTRIBUTES only works when the item has its own modifier list, so an empty one is set first.
     */
    public static void hideVanillaTooltip(ItemMeta meta) {
        if (!meta.hasAttributeModifiers()) {
            meta.setAttributeModifiers(com.google.common.collect.LinkedHashMultimap.create());
        }
        meta.addItemFlags(ItemFlag.values());
    }

    protected Material material(String path, Material def) {
        Material m = Material.matchMaterial(plugin.menus().material(path, def.name()));
        return m == null || !m.isItem() ? def : m;
    }

    /** A button from menus.yml: material, name, lore with placeholders. */
    protected ItemStack button(String path, Material def, String... placeholders) {
        return icon(material(path, def), plugin.menus().name(path, placeholders), plugin.menus().lore(path, placeholders));
    }
}
