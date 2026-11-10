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
        if (!plugin.settings().activityXp || !src.enabled() || amount <= 0) return;
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
        // 0.26.0: on a full breakthrough level the EXP is kept past full (carried over after the breakthrough)
        if (needsBreakthrough(d)) {
            d.xp += amount;
            d.dirty = true;
            return true;
        }
        int before = d.level;
        d.xp += amount;
        climb(d, max);
        d.dirty = true;
        if (d.level > before) levelUp(owner, d, before);
        if (needsBreakthrough(d)) {
            plugin.messages().send(owner, "breakthrough.ready", "pet", plugin.pets().displayName(d),
                    "level", String.valueOf(d.level));
        }
        return true;
    }

    /**
     * Levels up while the EXP reaches the next level. On a breakthrough level it stops; the EXP past full is kept and
     * goes to the next levels once the breakthrough succeeds.
     */
    private void climb(PetData d, int max) {
        while (d.level < max) {
            double need = plugin.levels().need(d.level);
            if (d.xp < need || gateLevel(d.level, max)) break;
            d.xp -= need;
            d.level++;
        }
        if (d.level >= max) d.xp = 0;
    }

    /** EXP stored past full on a breakthrough level (carried over after the breakthrough). */
    public double extraXp(PetData d) {
        return needsBreakthrough(d) ? Math.max(0, d.xp - plugin.levels().need(d.level)) : 0;
    }

    private boolean gateLevel(int level, int max) {
        return level < max && plugin.settings().breakthroughLevels.contains(level);
    }

    /** The pet is on a breakthrough level (full EXP or not). */
    public boolean onBreakthroughLevel(PetData d) {
        return gateLevel(d.level, plugin.pets().rarity(d).maxLevel());
    }

    /** Full EXP on a breakthrough level: no more EXP until a breakthrough succeeds. */
    public boolean needsBreakthrough(PetData d) {
        return onBreakthroughLevel(d) && d.xp >= plugin.levels().need(d.level) - 1e-6;
    }

    /**
     * Rolls a breakthrough with {@code chance}% (the stone is already taken). Success: next level with 0 EXP;
     * failure: nothing changes.
     */
    public boolean breakthrough(Player owner, PetData d, int chance) {
        boolean ok = java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < chance;
        String name = plugin.pets().displayName(d);
        ActivePet p = plugin.pets().active(owner.getUniqueId());
        boolean out = p != null && p.data == d;
        if (!ok) {
            plugin.messages().send(owner, "breakthrough.failed", "pet", name, "chance", String.valueOf(chance));
            Effects.sound(out ? p.pos : owner.getLocation(), "entity.item.break", 0.8f, 0.8f);
            return false;
        }
        int before = d.level;
        // the EXP past full is kept: it can level up further, up to the next breakthrough level
        d.xp = Math.max(0, d.xp - plugin.levels().need(d.level));
        d.level++;
        climb(d, plugin.pets().rarity(d).maxLevel());
        d.dirty = true;
        plugin.messages().send(owner, "breakthrough.success", "pet", name, "level", String.valueOf(d.level));
        Effects.sound(owner.getLocation(), "ui.toast.challenge_complete", 0.7f, 1.2f);
        levelUp(owner, d, before);
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
