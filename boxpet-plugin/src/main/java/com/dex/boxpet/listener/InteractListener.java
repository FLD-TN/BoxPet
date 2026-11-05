package com.dex.boxpet.listener;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.ItemsConfig;
import com.dex.boxpet.gui.CareMenu;
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

    /** Combat lock message at most once every 3 seconds (spec 11.1). */
    public void lockMessage(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastLockMessage.get(p.getUniqueId());
        if (last != null && now - last < 3000) return;
        lastLockMessage.put(p.getUniqueId(), now);
        plugin.messages().send(p, "combat-locked");
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
        if (plugin.pets().combat().tagged(p.getUniqueId())) {
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
        Integer vanilla = held.getType().isAir() ? null : plugin.items().vanillaFood.get(held.getType());
        if (vanilla != null && info == null) {
            if (plugin.care().feedVanilla(pet, vanilla)) take(p, slot, held, 1);
            return;
        }
        if (held.getType().isAir() && p.isSneaking()) {
            plugin.messages().send(p, plugin.pets().toggleCarry(pet), "pet", plugin.pets().displayName(pet.data));
            return;
        }
        new CareMenu(plugin, p).open();
    }

    /**
     * Eats one (or as many as fit with {@code all}) from the stack in inventory slot {@code slot}. The slot is read
     * again and written back with the new amount: never relies on an ItemStack reference staying linked to the
     * inventory.
     */
    public void useConsumable(Player p, ActivePet pet, int slot, ItemsConfig.Consumable c, boolean all) {
        ItemStack held = p.getInventory().getItem(slot);
        ItemsConfig.Consumable check = plugin.itemFactory().consumable(plugin.itemFactory().read(held));
        if (held == null || check == null || !check.id().equals(c.id())) return;
        int max = all && c.candy() ? held.getAmount() : 1;
        CareService.FeedResult[] why = new CareService.FeedResult[1];
        int eaten = plugin.care().feed(p, pet, c, max, why);
        if (eaten > 0) {
            take(p, slot, held, eaten);
            plugin.messages().send(p, c.candy() ? "care.candy-eaten" : "care.food-eaten", "pet", plugin.pets().displayName(pet.data),
                    "amount", String.valueOf(eaten), "item", "<" + c.color() + ">" + c.name(),
                    "fullness", String.valueOf(pet.data.fullness), "xp", com.dex.boxpet.text.Format.number(Math.round(c.xp() * eaten)));
        } else if (why[0] == CareService.FeedResult.FULL) {
            plugin.messages().send(p, "care.full", "pet", plugin.pets().displayName(pet.data), "fullness", String.valueOf(pet.data.fullness));
        } else if (why[0] == CareService.FeedResult.MAX_LEVEL) {
            plugin.messages().send(p, "care.max-level", "pet", plugin.pets().displayName(pet.data));
        }
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

    /** Right-click in the air / on a block with a candy or food: goes to the active pet. Eggs: never thrown. */
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
        ItemsConfig.Consumable c = plugin.itemFactory().consumable(info);
        if (c == null) {
            if (info.kind() == ItemFactory.Kind.EGG || info.kind() == ItemFactory.Kind.RANDOM_EGG) {
                if (!debounce(p)) plugin.messages().send(p, "egg.hint");
            }
            return;
        }
        if (debounce(p)) return;
        ActivePet pet = plugin.pets().active(p.getUniqueId());
        if (pet == null) {
            plugin.messages().send(p, "care.no-active");
            return;
        }
        if (plugin.pets().combat().tagged(p.getUniqueId())) {
            lockMessage(p);
            return;
        }
        useConsumable(p, pet, p.getInventory().getHeldItemSlot(), c, p.isSneaking());
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
