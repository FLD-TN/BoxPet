package com.dex.boxpet.pet;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.config.Rarity;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Collection book (spec 9.4): best rarity per pet type, score, milestone stats. */
public final class CollectionService {

    private final BoxPetPlugin plugin;

    public CollectionService(BoxPetPlugin plugin) {
        this.plugin = plugin;
    }

    /** A pet was hatched / given: remembers its type and rarity (kept even after releasing it). */
    public void record(PlayerData pd, PetData pet) {
        Rarity r = plugin.rarities().get(pet.rarity);
        if (r == null) return;
        Integer prev = pd.collection.get(pet.type);
        if (prev != null && prev >= r.index()) return;
        int scoreBefore = score(pd);
        pd.collection.put(pet.type, r.index());
        pd.collectionDirty = true;
        pd.dirty = true;
        Player online = Bukkit.getPlayer(pd.uuid);
        if (online != null && score(pd) != scoreBefore) apply(online, pd);
    }

    public int score(PlayerData pd) {
        int sum = 0;
        for (Map.Entry<String, Integer> e : pd.collection.entrySet()) {
            PetType t = plugin.types().get(e.getKey());
            if (t == null) continue;
            sum += plugin.rarities().byIndex(e.getValue()).collectionPoints();
        }
        return sum;
    }

    public void apply(Player player, PlayerData pd) {
        Map<String, Double> stats = plugin.collectionConfig().statsFor(score(pd));
        if (!plugin.stats().applyCollection(player, stats)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player again = Bukkit.getPlayer(pd.uuid);
                if (again != null && plugin.pets().data(pd.uuid) == pd) plugin.stats().applyCollection(again, stats);
            }, 40L);
        }
    }

    /** Best rarity index of a type for the player, -1 when never owned. */
    public int best(PlayerData pd, String type) {
        Integer v = pd.collection.get(type);
        return v == null ? -1 : v;
    }
}
