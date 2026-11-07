package com.dex.boxpet.pet;

import com.dex.boxpet.config.Ability;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.config.Rarity;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.Nullable;

/** The runtime side of a player's active pet. Main thread only. */
public final class ActivePet {

    /** What the pet is doing (spec 4.1, 4.2, 4.5). */
    public enum Mode {
        REST, FOLLOW, COMBAT, SIT, TRAVEL, GONE
    }

    public final UUID ownerId;
    public Player owner;
    public final PlayerData player;
    public final PetData data;
    public PetType type;
    public Rarity rarity;
    public @Nullable Ability ability;

    public @Nullable PetVisual visual;
    public @Nullable TextDisplay nametag;
    public @Nullable TextDisplay bubble;
    public int bubbleTicks;
    /** 0.9.7: the show-off hologram (everyone sees it) and its ticks left. */
    public @Nullable TextDisplay showcase;
    public int showcaseTicks;

    public Location pos;
    public float yaw;
    public Mode mode = Mode.GONE;
    public boolean sitting;
    public boolean travelHidden;
    public boolean deadHidden;
    public boolean teleporting;
    /** Viewers this pet is force-hidden for (hide others, visible cap). */
    public final java.util.Set<UUID> hiddenViewers = new java.util.HashSet<>();
    public int statRetries;
    /** Block reads of the follower, reused while the owner stays in the same block. */
    final GroundCache ground = new GroundCache();
    /** Spreads name tag updates of different pets over different ticks. */
    public final int phase;

    // follow state
    public @Nullable Location lastOwner;
    public double ownerSpeed;
    public float moveYaw;
    public boolean moving;
    public boolean followingToSlot;
    public @Nullable Location anchor;
    public @Nullable Location wanderTarget;
    public int restTicks;
    public int nextWander;
    public int nextInteract;
    public int lookTicks;
    public long lastLookReact;
    public int fastTicks;
    public int slowTicks;
    public int combatTicksLeft;
    public boolean risen;

    // abilities
    public int cooldown;
    public int potionRefresh;
    public long shieldReadyAt;
    /** 0.16.0: stacks of a stacking buff and when they run out. */
    public int buffStack;
    public long buffStackUntil;
    /** 0.16.0: until when attackers of the owner are weakened by the shield. */
    public long shieldDebuffUntil;
    /** 0.9.0: the second skill and its own cooldowns (AbilityService swaps them in while it runs that skill). */
    public @Nullable Ability ability2;
    public int cooldown2;
    public long shieldReadyAt2;
    public int potionRefresh2;
    /** 0.9.1: which skill goes next (0 / 1) and when the last one went off (AbilityService ticks). */
    public int skillTurn;
    public int lastSkillTick = -10_000;

    /** 0.9.2: the second skill exists and the pet's level has unlocked it. */
    public boolean hasSecond(int unlockLevel) {
        return ability2 != null && data.level >= unlockLevel;
    }

    /** Puts the second skill in the first slot and back (main thread only, always called in pairs). */
    public void swapSkills() {
        Ability a = ability;
        ability = ability2;
        ability2 = a;
        int c = cooldown;
        cooldown = cooldown2;
        cooldown2 = c;
        long s = shieldReadyAt;
        shieldReadyAt = shieldReadyAt2;
        shieldReadyAt2 = s;
        int r = potionRefresh;
        potionRefresh = potionRefresh2;
        potionRefresh2 = r;
    }
    public @Nullable Float faceYaw;
    public int faceTicks;

    // chat timers (epoch ms)
    public long nextIdleChat;
    public long lastHungryChat;
    public long lastLowHpChat;
    public long lastChat;
    public boolean birthdayChecked;

    // fullness drain bookkeeping
    public long lastDrainCheck;

    public ActivePet(Player owner, PlayerData player, PetData data, PetType type, Rarity rarity, @Nullable Ability ability) {
        this.ownerId = owner.getUniqueId();
        this.owner = owner;
        this.player = player;
        this.data = data;
        this.type = type;
        this.rarity = rarity;
        this.ability = ability;
        this.pos = owner.getLocation();
        this.phase = Math.floorMod(data.id.hashCode(), 20);
    }

    public boolean shown() {
        return visual != null && !visual.removed() && !travelHidden && !deadHidden;
    }

    public boolean hungry() {
        return data.fullness <= 0;
    }

    /** The ability is paused: hungry, travelling hidden, owner dead. */
    public boolean abilityPaused() {
        return hungry() || travelHidden || deadHidden || visual == null;
    }
}
