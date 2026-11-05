package com.dex.boxpet.pet;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.api.event.PetLevelUpEvent;
import com.dex.boxpet.config.XpSources;
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
        int max = p.rarity.maxLevel();
        if (p.data.level >= max) {
            p.data.xp = 0;
            return false;
        }
        int before = p.data.level;
        p.data.xp += amount;
        while (p.data.level < max) {
            double need = plugin.levels().need(p.data.level);
            if (p.data.xp < need) break;
            p.data.xp -= need;
            p.data.level++;
        }
        if (p.data.level >= max) p.data.xp = 0;
        p.data.dirty = true;
        if (p.data.level > before) levelUp(p, before);
        return true;
    }

    private void levelUp(ActivePet p, int before) {
        Bukkit.getPluginManager().callEvent(new PetLevelUpEvent(p.owner, plugin.pets().view(p.data), before));
        plugin.messages().title(p.owner, "level-up", "pet", plugin.pets().displayName(p.data),
                "level", String.valueOf(p.data.level));
        plugin.messages().send(p.owner, "level-up.chat", "pet", plugin.pets().displayName(p.data),
                "level", String.valueOf(p.data.level));
        Effects.sound(p.owner.getLocation(), "entity.player.levelup", 0.8f, 1.3f);
        plugin.pets().playAnimation(p, "skill");
        plugin.chat().say(p, "level-up");
        plugin.pets().refreshNametag(p);
        plugin.pets().refreshStats(p);
        plugin.pets().refreshBonus(p);
    }
}
