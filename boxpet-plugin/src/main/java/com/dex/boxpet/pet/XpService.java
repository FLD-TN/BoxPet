package com.dex.boxpet.pet;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.api.event.PetLevelUpEvent;
import com.dex.boxpet.config.XpSources;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Pet XP and levels (spec 9.1). Only the active pet gains XP. */
public final class XpService {

    private final BoxPetPlugin plugin;

    public XpService(BoxPetPlugin plugin) {
        this.plugin = plugin;
    }

    /** Activity XP from a source, limited by its daily cap. */
    public void give(Player player, String sourceId, double amount) {
        XpSources.Source src = plugin.xpSources().get(sourceId);
        if (!src.enabled() || amount <= 0) return;
        ActivePet p = plugin.pets().active(player.getUniqueId());
        if (p == null || p.deadHidden) return;
        PlayerData pd = p.player;
        plugin.pets().resetDay(pd);
        double allowed = amount;
        if (src.dailyCap() > 0) {
            double used = pd.dailyXp.getOrDefault(sourceId, 0.0);
            allowed = Math.min(amount, src.dailyCap() - used);
            if (allowed <= 0) return;
            pd.dailyXp.put(sourceId, used + allowed);
            pd.dirty = true;
        }
        addXp(p, allowed);
    }

    /** One minute online with the pet out. */
    void online(ActivePet p) {
        XpSources src = plugin.xpSources();
        if (!src.onlineSkipRegions.isEmpty() && plugin.worldGuard().isPresent()
                && plugin.worldGuard().get().inRegion(p.owner.getLocation(), src.onlineSkipRegions)) return;
        give(p.owner, XpSources.ONLINE, src.get(XpSources.ONLINE).amount());
    }

    /**
     * Adds XP and levels up as long as it reaches the next level. XP past the rarity's max level is lost.
     *
     * @return false when the pet was already at max level
     */
    public boolean addXp(ActivePet p, double amount) {
        return addXp(p.owner, p.data, amount);
    }

    /**
     * Adds XP to any pet of an online owner (active or resting in storage: candies work on both) and levels up as
     * long as it reaches the next level. XP past the rarity's max level is lost. Effects only for the active pet.
     *
     * @return false when the pet was already at max level
     */
    public boolean addXp(Player owner, PetData d, double amount) {
        int max = plugin.pets().rarity(d).maxLevel();
        if (d.level >= max) {
            d.xp = 0;
            return false;
        }
        int before = d.level;
        d.xp += amount;
        while (d.level < max) {
            double need = plugin.levels().need(d.level);
            if (d.xp < need) break;
            d.xp -= need;
            d.level++;
        }
        if (d.level >= max) d.xp = 0;
        d.dirty = true;
        if (d.level > before) levelUp(owner, d, before);
        return true;
    }

    private void levelUp(Player owner, PetData d, int before) {
        Bukkit.getPluginManager().callEvent(new PetLevelUpEvent(owner, plugin.pets().view(d), before));
        plugin.messages().title(owner, "level-up", "pet", plugin.pets().displayName(d), "level", String.valueOf(d.level));
        plugin.messages().send(owner, "level-up.chat", "pet", plugin.pets().displayName(d), "level", String.valueOf(d.level));
        Effects.sound(owner.getLocation(), "entity.player.levelup", 0.8f, 1.3f);
        ActivePet p = plugin.pets().active(owner.getUniqueId());
        if (p == null || p.data != d) return;
        plugin.pets().playAnimation(p, "skill");
        plugin.chat().say(p, "level-up");
        plugin.pets().refreshNametag(p);
        plugin.pets().refreshStats(p);
        plugin.pets().refreshBonus(p);
    }
}
