package com.dex.boxpet.listener;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.ItemsConfig;
import com.dex.boxpet.item.ItemFactory;
import com.dex.boxpet.pet.ActivePet;
import com.dex.boxpet.pet.CareService;
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Clicks on a pet's model (ModelEngine {@link BaseEntityInteractEvent}, spec 4.4), using candies / food in the air,
 * and keeping BoxPet items from being placed, eaten, thrown or crafted.
 */
public final class InteractListener implements Listener {

    private final BoxPetPlugin plugin;
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Map<UUID, Long> lastLockMessage = new HashMap<>();

    public InteractListener(BoxPetPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean debounce(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastClick.get(p.getUniqueId());
        if (last != null && now - last < 250) return true;
        lastClick.put(p.getUniqueId(), now);
        return false;
    }

    /** Logout: forget the click / message cooldowns of this player. */
    public void forget(UUID player) {
        lastClick.remove(player);
        lastLockMessage.remove(player);
    }

    /** 0.13.2: staff with boxpet.bypass skip combat locks and cooldowns (menus, pet clicks, summon, rename, show-off). */
    public static boolean bypass(Player p) {
        return p != null && p.hasPermission("boxpet.bypass");
    }

    /** Combat-tagged and not allowed to bypass it. */
    public boolean combatLocked(Player p) {
        return !bypass(p) && plugin.pets().combat().tagged(p.getUniqueId());
    }

    /** Seconds left of the combat tag, at least 1. */
    public long combatWait(Player p) {
        return Math.max(1, (plugin.pets().combat().remaining(p.getUniqueId()) + 999) / 1000);
    }

    /** Combat lock message at most once every 3 seconds (spec 11.1). */
    public void lockMessage(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastLockMessage.get(p.getUniqueId());
        if (last != null && now - last < 3000) return;
        lastLockMessage.put(p.getUniqueId(), now);
        plugin.messages().send(p, "combat-locked", "seconds", String.valueOf(combatWait(p)));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onModelClick(BaseEntityInteractEvent e) {
        // ModelEngine may fire this off the main thread: only read the event here, do everything on the main thread
        if (e.getAction() == BaseEntityInteractEvent.Action.ATTACK || e.getSlot() != EquipmentSlot.HAND) return;
        UUID base = e.getBaseEntity().getUUID();
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        Bukkit.getScheduler().runTask(plugin, () -> {
            ActivePet pet = plugin.pets().byBase(base);
            if (pet == null || !p.getUniqueId().equals(pet.ownerId)) return;
            clickPet(p, pet, item);
        });
    }

    private void clickPet(Player p, ActivePet pet, ItemStack hand) {
        if (!p.isOnline() || plugin.pets().active(p.getUniqueId()) != pet || debounce(p)) return;
        if (combatLocked(p)) {
            lockMessage(p);
            return;
        }
        int slot = p.getInventory().getHeldItemSlot();
        ItemStack held = p.getInventory().getItem(slot);
        if (held == null) held = new ItemStack(Material.AIR);
        ItemFactory.Info info = plugin.itemFactory().read(held);
        ItemsConfig.Consumable c = plugin.itemFactory().consumable(info);
        if (c != null) {
            useConsumable(p, pet, slot, c, p.isSneaking());
            return;
        }
        if (plugin.itemFactory().breakChance(info) > 0) {
            useBreakthrough(p, pet.data, slot);
            return;
        }
        Integer vanilla = held.getType().isAir() ? null : plugin.items().vanillaFood.get(held.getType());
        if (vanilla != null && info == null) {
            if (plugin.care().feedVanilla(pet, vanilla)) take(p, slot, held, 1);
            return;
        }
        new com.dex.boxpet.gui.ManageMenu(plugin, p, pet.player, pet.data, false).open();
    }

    /**
     * Eats one (or as many as fit with {@code all}) from the stack in inventory slot {@code slot}. The slot is read
     * again and written back with the new amount: never relies on an ItemStack reference staying linked to the
     * inventory.
     */
    public void useConsumable(Player p, ActivePet pet, int slot, ItemsConfig.Consumable c, boolean all) {
        useConsumable(p, pet.data, slot, c, all);
    }

    /** Feeds any pet of the player (active or in storage). */
    public void useConsumable(Player p, com.dex.boxpet.data.PetData pet, int slot, ItemsConfig.Consumable c, boolean all) {
        ItemStack held = p.getInventory().getItem(slot);
        ItemsConfig.Consumable check = plugin.itemFactory().consumable(plugin.itemFactory().read(held));
        if (held == null || check == null || !check.id().equals(c.id())) return;
        int max = all && c.candy() ? held.getAmount() : 1;
        CareService.FeedResult[] why = new CareService.FeedResult[1];
        int eaten = plugin.care().feed(p, pet, c, max, why);
        String petName = plugin.pets().displayName(pet);
        if (eaten > 0) {
            take(p, slot, held, eaten);
            String key = !c.candy() ? "care.food-eaten" : why[0] == CareService.FeedResult.OK_NO_XP ? "care.candy-eaten-no-xp" : "care.candy-eaten";
            plugin.messages().send(p, key, "pet", petName,
                    "amount", String.valueOf(eaten), "item", "<" + c.color() + ">" + c.name(),
                    "fullness", String.valueOf(pet.fullness), "xp", com.dex.boxpet.text.Format.number(Math.round(c.xp() * eaten)));
        } else if (why[0] == CareService.FeedResult.FULL) {
            plugin.messages().send(p, "care.full", "pet", petName, "fullness", String.valueOf(pet.fullness));
        } else if (why[0] == CareService.FeedResult.MAX_LEVEL) {
            plugin.messages().send(p, "care.max-level", "pet", petName);
        } else if (why[0] == CareService.FeedResult.BREAKTHROUGH) {
            plugin.messages().send(p, "breakthrough.need", "pet", petName, "level", String.valueOf(pet.level));
        }
    }

    /** Uses one breakthrough stone from inventory slot {@code slot} on any pet of the player. */
    public void useBreakthrough(Player p, com.dex.boxpet.data.PetData pet, int slot) {
        ItemStack held = p.getInventory().getItem(slot);
        int chance = plugin.itemFactory().breakChance(plugin.itemFactory().read(held));
        if (held == null || chance <= 0) return;
        String petName = plugin.pets().displayName(pet);
        if (!plugin.xp().needsBreakthrough(pet)) {
            plugin.messages().send(p, plugin.xp().onBreakthroughLevel(pet) ? "breakthrough.not-full" : "breakthrough.not-needed",
                    "pet", petName, "level", String.valueOf(pet.level));
            return;
        }
        take(p, slot, held, 1);
        plugin.xp().breakthrough(p, pet, chance);
        // stone and result are saved in the same moment
        var pd = plugin.pets().data(p.getUniqueId());
        if (pd != null) plugin.pets().save(pd);
    }

    /** Removes {@code amount} from the stack and writes the slot back. */
    private void take(Player p, int slot, ItemStack stack, int amount) {
        int left = stack.getAmount() - amount;
        if (left <= 0) {
            p.getInventory().setItem(slot, null);
        } else {
            ItemStack copy = stack.clone();
            copy.setAmount(left);
            p.getInventory().setItem(slot, copy);
        }
        var pd = plugin.pets().data(p.getUniqueId());
        if (pd != null) pd.inventoryChanged = true;
    }

    /**
     * BoxPet items are never used on air / blocks (eggs not thrown, food not eaten by the player). Feeding and
     * breakthroughs only happen by clicking the pet's model ({@link #onModelClick}).
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND && e.getHand() != EquipmentSlot.OFF_HAND) return;
        ItemStack item = e.getItem();
        ItemFactory.Info info = plugin.itemFactory().read(item);
        if (info == null) return;
        e.setUseItemInHand(Event.Result.DENY);
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        e.setUseInteractedBlock(Event.Result.DENY);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (info.kind() == ItemFactory.Kind.EGG || info.kind() == ItemFactory.Kind.RANDOM_EGG) {
            if (!debounce(p)) plugin.messages().send(p, "egg.hint");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityUse(PlayerInteractEntityEvent e) {
        ItemStack item = e.getPlayer().getInventory().getItem(e.getHand());
        if (plugin.itemFactory().isBoxPetItem(item)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.itemFactory().isBoxPetItem(e.getItemInHand())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (plugin.itemFactory().isBoxPetItem(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack i : e.getInventory().getMatrix()) {
            if (i != null && i.getType() != Material.AIR && plugin.itemFactory().isBoxPetItem(i)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }
}
