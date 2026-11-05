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
        REST, FOLLOW, COMBAT, SIT, CARRIED, TRAVEL, GONE
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

    public Location pos;
    public float yaw;
    public Mode mode = Mode.GONE;
    public boolean sitting;
    public boolean carried;
    public boolean travelHidden;
    public boolean deadHidden;
    public boolean teleporting;
    /** Viewers this pet is force-hidden for (hide others, visible cap). */
    public final java.util.Set<UUID> hiddenViewers = new java.util.HashSet<>();
    public int statRetries;

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
    }

    public boolean shown() {
        return visual != null && !visual.removed() && !travelHidden && !deadHidden;
    }

    public boolean hungry() {
        return data.fullness <= 0;
    }

    /** The ability is paused: hungry, carried, travelling hidden, owner dead. */
    public boolean abilityPaused() {
        return hungry() || carried || travelHidden || deadHidden || visual == null;
    }
}
