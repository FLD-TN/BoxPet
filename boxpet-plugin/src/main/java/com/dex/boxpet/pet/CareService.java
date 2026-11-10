package com.dex.boxpet.pet;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.ItemsConfig;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.config.Settings;
import com.dex.boxpet.text.Format;
import java.time.LocalDate;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/** Fullness, bond, petting and feeding (spec 11). Main thread only. */
public final class CareService {

    public enum FeedResult {
        /** OK_NO_XP: candies eaten by a pet at its max level, fullness only (0.26.0). */
        OK, OK_NO_XP, FULL, MAX_LEVEL, BREAKTHROUGH, NOTHING
    }

    private final BoxPetPlugin plugin;

    public CareService(BoxPetPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings s() {
        return plugin.settings();
    }

    // ------------------------------------------------------------------ every second

    /** Time stamps below this are the pre-0.27.0 meaning of PetData#drainMs (banked ms, always far smaller). */
    public static final long EPOCH_MIN = 1_000_000_000_000L;

    /**
     * 0.27.0: brings a pet's fullness up to {@code now}. Every pet loses 1 point per drain-minutes-per-point in real
     * time: out, resting in storage, owner offline, server off. Computed from a time stamp, so offline pets cost nothing
     * until they are read again.
     *
     * @return points lost
     */
    public int settle(PetData d, long now) {
        long per = s().drainMinutesPerPoint * 60_000L;
        if (d.drainMs < EPOCH_MIN) {
            // pets from before 0.27.0 (or brand new): count from now, keeping the part of a point already banked
            d.drainMs = now - Math.max(0, Math.min(d.drainMs, per - 1));
            d.dirty = true;
        }
        if (now <= d.drainMs) return 0;
        long points = (now - d.drainMs) / per;
        if (points <= 0) return 0;
        int before = d.fullness;
        if (points >= d.fullness) {
            d.fullness = 0;
            d.drainMs = now;
        } else {
            d.fullness -= (int) points;
            d.drainMs += points * per;
        }
        d.dirty = true;
        return before - d.fullness;
    }

    void second(ActivePet p, long now) {
        int before = p.data.fullness;
        if (settle(p.data, now) > 0) {
            int after = p.data.fullness;
            if (before >= s().littleHungry && after < s().littleHungry && after > 0) plugin.chat().say(p, "little-hungry");
            if (after == 0 && before > 0) {
                p.lastHungryChat = now;
                plugin.chat().say(p, "hungry");
                plugin.pets().refreshBonus(p);
                plugin.pets().refreshStats(p);
            }
        }
        boolean changed = p.data.fullness != before;
        autoFeed(p);
        if (changed) plugin.pets().refreshNametag(p);
        if (p.hungry() && now - p.lastHungryChat > s().hungryRepeatSeconds * 1000L) {
            p.lastHungryChat = now;
            plugin.chat().say(p, "hungry");
        }
        // owner low HP (spec 11.4)
        AttributeInstance max = p.owner.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (max != null && !p.owner.isDead() && p.owner.getHealth() / max.getValue() * 100 < s().lowHpPercent
                && now - p.lastLowHpChat > s().lowHpCooldownSeconds * 1000L) {
            p.lastLowHpChat = now;
            plugin.chat().say(p, "owner-low-hp");
        }
        // birthday (spec 11.5), once per summon/session
        if (!p.birthdayChecked && p.shown()) {
            p.birthdayChecked = true;
            long age = p.data.ageDays(now);
            if (s().birthdays.contains((int) age)) plugin.chat().say(p, "birthday", "age", String.valueOf(age));
        }
    }

    // ------------------------------------------------------------------ auto feed (0.6.0)

    /**
     * At or under the chosen fullness, the pet out eats from the auto feed box: the chosen kind first (candies or
     * foods), each kind slot by slot from left to right, as much of a slot as still fits, then the next slot.
     */
    public static final String AUTOFEED_PERMISSION = "boxpet.autofeed";

    private void autoFeed(ActivePet p) {
        com.dex.boxpet.data.PlayerData pd = p.player;
        if (!pd.autoFeed || p.deadHidden) return;
        if (p.data.fullness > pd.autoFeedAt) {
            pd.autoFeedWarned = false;
            return;
        }
        if (!p.owner.hasPermission(AUTOFEED_PERMISSION)) return;
        int eaten = 0;
        FeedResult[] result = new FeedResult[1];
        for (int pass = 0; pass < 2; pass++) {
            boolean candies = (pass == 0) == pd.autoFeedCandyFirst;
            for (int i = 0; i < com.dex.boxpet.data.PlayerData.PANTRY_SIZE; i++) {
                String id = pd.pantryIds[i];
                if (id == null || pd.pantryAmounts[i] <= 0) continue;
                ItemsConfig.Consumable c = plugin.items().get(id);
                if (c == null || c.candy() != candies) continue;
                // 0.26.0: auto-feed never spends candies on a max-level pet (no EXP to gain); feeding by hand still can
                if (c.candy() && p.data.level >= plugin.pets().rarity(p.data).maxLevel()) continue;
                int n = feed(p.owner, p.data, c, pd.pantryAmounts[i], result);
                if (n <= 0) continue;
                pd.pantryAmounts[i] -= n;
                if (pd.pantryAmounts[i] <= 0) pd.pantryIds[i] = null;
                pd.dirty = true;
                eaten += n;
            }
        }
        if (eaten > 0) {
            pd.autoFeedWarned = false;
            plugin.messages().actionBar(p.owner, "care.auto-fed", "pet", plugin.pets().displayName(p.data),
                    "amount", String.valueOf(eaten), "fullness", String.valueOf(p.data.fullness));
        } else if (!pd.autoFeedWarned) {
            pd.autoFeedWarned = true;
            plugin.messages().send(p.owner, "care.auto-empty", "pet", plugin.pets().displayName(p.data));
        }
    }

    // ------------------------------------------------------------------ petting (spec 11.2)

    public void pet(Player player, ActivePet p) {
        long now = System.currentTimeMillis();
        // once per cooldown; nothing happens in between (no spam)
        if (now < p.data.bondGainAt) {
            plugin.messages().actionBar(player, "care.petted-wait", "time",
                    Format.duration((p.data.bondGainAt - now + 999) / 1000));
            return;
        }
        String anim = p.type.animations.containsKey("pet") ? "pet" : "interact";
        plugin.pets().playAnimation(p, anim);
        Effects.hearts(p.pos.clone().add(0, p.visual == null ? 1 : p.visual.height(), 0));
        Effects.sound(p.pos, "entity.cat.purr", 0.8f, 1.2f);
        // petting always resets the neglect clock
        p.data.neglectMs = 0;
        p.data.neglectSteps = 0;
        p.data.activeSince = now;
        p.data.dirty = true;
        p.data.bondGainAt = now + s().bondPetCooldownMillis;
        if (s().bondPetGain > 0) {
            int tierBefore = s().bondTier(p.data.bond).min();
            int before = p.data.bond;
            p.data.bond = Math.min(100, p.data.bond + s().bondPetGain);
            plugin.messages().send(player, "care.petted-gain", "pet", plugin.pets().displayName(p.data),
                    "amount", String.valueOf(p.data.bond - before), "bond", String.valueOf(p.data.bond));
            if (s().bondTier(p.data.bond).min() != tierBefore) plugin.pets().refreshStats(p);
        }
        plugin.chat().say(p, "petted");
    }

    // ------------------------------------------------------------------ feeding (spec 9.1, 11.3)

    /**
     * Feeds the active pet with up to {@code max} items of one kind. Candies give XP, foods give bond; both fill the
     * stomach, and nothing is eaten past 100.
     *
     * @return how many were eaten (0 with the reason in {@code result[0]})
     */
    public int feed(Player player, PetData d, ItemsConfig.Consumable c, int max, FeedResult[] result) {
        settle(d, System.currentTimeMillis());
        int eaten = 0;
        FeedResult why = FeedResult.NOTHING;
        boolean wasHungry = d.fullness <= 0;
        int maxLevel = plugin.pets().rarity(d).maxLevel();
        // 0.26.0: candies are always edible (the owner may have nothing else): at max level they only fill the stomach,
        // on a full breakthrough level the EXP is kept past full and carried over after the breakthrough
        boolean noXp = c.candy() && d.level >= maxLevel;
        for (int i = 0; i < max; i++) {
            if (d.fullness + c.fullness() > 100) {
                why = FeedResult.FULL;
                break;
            }
            d.fullness += c.fullness();
            if (c.candy()) plugin.xp().addXp(player, d, c.xp());
            else addFoodBond(d, c.bond());
            eaten++;
        }
        // effects only when this pet is out
        ActivePet p = plugin.pets().active(player.getUniqueId());
        boolean out = p != null && p.data == d;
        if (eaten > 0) {
            d.dirty = true;
            why = noXp ? FeedResult.OK_NO_XP : FeedResult.OK;
            if (out) {
                plugin.pets().playAnimation(p, "interact");
                Effects.sound(p.pos, "entity.generic.eat", 0.8f, 1.1f);
                if (wasHungry && !p.hungry()) {
                    plugin.pets().refreshBonus(p);
                    plugin.pets().refreshStats(p);
                }
                plugin.pets().refreshNametag(p);
                plugin.chat().say(p, "fed");
            } else {
                player.playSound(player.getLocation(), "entity.generic.eat", 0.7f, 1.1f);
            }
        } else if (why == FeedResult.FULL && out) {
            plugin.chat().say(p, "full");
        }
        result[0] = why;
        return eaten;
    }

    /** Vanilla food from items.yml {@code vanilla-food}: fullness only. */
    public boolean feedVanilla(ActivePet p, int fullness) {
        if (p.data.fullness + fullness > 100) {
            plugin.chat().say(p, "full");
            return false;
        }
        boolean wasHungry = p.hungry();
        p.data.fullness += fullness;
        p.data.dirty = true;
        plugin.pets().playAnimation(p, "interact");
        Effects.sound(p.pos, "entity.generic.eat", 0.8f, 1.1f);
        if (wasHungry) {
            plugin.pets().refreshBonus(p);
            plugin.pets().refreshStats(p);
        }
        plugin.pets().refreshNametag(p);
        plugin.chat().say(p, "fed");
        return true;
    }

    private void addFoodBond(PetData d, int bond) {
        if (bond <= 0) return;
        long today = LocalDate.now().toEpochDay();
        if (d.foodBondDay != today) {
            d.foodBondDay = today;
            d.foodBond = 0;
        }
        int room = Math.max(0, s().bondFoodMaxPerDay - d.foodBond);
        int gain = Math.min(room, bond);
        if (gain <= 0) return;
        int tierBefore = s().bondTier(d.bond).min();
        d.foodBond += gain;
        d.bond = Math.min(100, d.bond + gain);
        ActivePet p = plugin.pets().active(d.owner);
        if (p != null && p.data == d && s().bondTier(d.bond).min() != tierBefore) plugin.pets().refreshStats(p);
    }
}
