package com.dex.boxpet.hook;

import java.lang.reflect.Method;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * ItemsAdder 4.x custom items through reflection (no compile dependency, same approach as BoxCasino), used for GUI
 * icons such as {@code treasure:money_item}. Main thread only.
 */
public final class ItemsAdderHook {

    private static final String API = "dev.lone.itemsadder.api.";

    private final Plugin plugin;
    private final Method customStack;
    private final Method itemStack;
    private boolean warned;

    private ItemsAdderHook(Plugin plugin) throws ReflectiveOperationException {
        this.plugin = plugin;
        Class<?> stack = Class.forName(API + "CustomStack");
        this.customStack = stack.getMethod("getInstance", String.class);
        this.itemStack = stack.getMethod("getItemStack");
    }

    /** {@code null} when ItemsAdder is not installed. */
    public static ItemsAdderHook create(Plugin plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return null;
        try {
            return new ItemsAdderHook(plugin);
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().log(Level.WARNING, "ItemsAdder API not found: custom GUI icons disabled", ex);
            return null;
        }
    }

    /** A copy of the ItemsAdder item, {@code null} when it does not exist. */
    public ItemStack item(String id) {
        if (id == null || id.isEmpty()) return null;
        try {
            Object stack = customStack.invoke(null, id);
            return stack == null ? null : ((ItemStack) itemStack.invoke(stack)).clone();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (!warned) {
                warned = true;
                plugin.getLogger().log(Level.WARNING, "ItemsAdder API call failed (reported once)", ex);
            }
            return null;
        }
    }
}
