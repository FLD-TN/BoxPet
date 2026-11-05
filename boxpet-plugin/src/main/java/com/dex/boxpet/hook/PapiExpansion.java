package com.dex.boxpet.hook;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import com.dex.boxpet.text.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/** {@code %boxpet_...%} (spec 15). Main thread values of online players; "" when unknown. */
public final class PapiExpansion extends PlaceholderExpansion {

    private final BoxPetPlugin plugin;

    public PapiExpansion(BoxPetPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "boxpet";
    }

    @Override
    public @NotNull String getAuthor() {
        return "dex";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        PlayerData pd = plugin.pets().data(player.getUniqueId());
        if (pd == null) return "";
        PetData a = pd.active();
        PetType t = a == null ? null : plugin.pets().type(a);
        return switch (params.toLowerCase()) {
            case "active_name" -> a == null ? "" : Text.legacy(plugin.pets().displayName(a));
            case "active_name_plain" -> a == null ? "" : Text.plain(plugin.pets().displayName(a));
            case "active_type" -> a == null ? "" : a.type;
            case "active_level" -> a == null ? "" : String.valueOf(a.level);
            case "active_rarity" -> a == null ? "" : Text.legacy(plugin.pets().rarity(a).coloured());
            case "active_role" -> t == null ? "" : Text.legacy(plugin.menus().text("roles." + t.role.id));
            case "active_bond" -> a == null ? "" : String.valueOf(a.bond);
            case "active_fullness" -> a == null ? "" : String.valueOf(a.fullness);
            case "active_age" -> a == null ? "" : String.valueOf(a.ageDays(System.currentTimeMillis()));
            case "has_active" -> a == null ? "false" : "true";
            case "collection_score" -> String.valueOf(plugin.collectionService().score(pd));
            case "storage_used" -> String.valueOf(pd.pets.size());
            case "storage_max" -> player.getPlayer() == null ? "" : String.valueOf(plugin.pets().capacity(player.getPlayer()));
            default -> null;
        };
    }
}
