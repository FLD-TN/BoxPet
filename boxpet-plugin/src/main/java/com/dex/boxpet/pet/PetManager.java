package com.dex.boxpet.pet;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.api.PetView;
import com.dex.boxpet.api.event.PetDismissEvent;
import com.dex.boxpet.api.event.PetSummonEvent;
import com.dex.boxpet.config.Ability;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.config.Rarity;
import com.dex.boxpet.config.Settings;
import com.dex.boxpet.data.IncubatorSlot;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import com.dex.boxpet.data.Storage;
import com.dex.boxpet.text.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

/** Loaded players, active pets and their models (spec 4, 7). Main thread only unless noted. */
public final class PetManager {

    public enum SummonResult {
        OK, COMBAT, COOLDOWN, ALREADY, UNKNOWN_TYPE, NO_MODEL, CANCELLED, NOT_LOADED
    }

    private final BoxPetPlugin plugin;
    private final Follower follower;
    private final CombatTracker combat;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Map<UUID, ActivePet> active = new HashMap<>();
    private final Map<UUID, ActivePet> byBase = new HashMap<>();
    private final Map<UUID, Long> lastSwap = new HashMap<>();
    private final Set<UUID> loading = new HashSet<>();
    private final Map<UUID, Map<String, Integer>> bonus = new ConcurrentHashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private int seconds;

    public PetManager(BoxPetPlugin plugin) {
        this.plugin = plugin;
        this.follower = new Follower(this);
        this.combat = new CombatTracker(plugin.settings().combatTagSeconds);
    }

    public BoxPetPlugin plugin() {
        return plugin;
    }

    public CombatTracker combat() {
        return combat;
    }

    public Follower follower() {
        return follower;
    }

    private Settings s() {
        return plugin.settings();
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::second, 20L, 20L));
        long save = s().saveIntervalSeconds * 20L;
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::saveAll, save, save));
        for (Player p : Bukkit.getOnlinePlayers()) join(p);
    }

    /** Plugin disable: models gone, stats removed, everything saved (the storage waits for the writes). */
    public void stop() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        long now = System.currentTimeMillis();
        for (ActivePet p : new ArrayList<>(active.values())) {
            despawnVisual(p, false);
            plugin.stats().clearAll(p.owner);
        }
        for (PlayerData pd : players.values()) {
            Player online = Bukkit.getPlayer(pd.uuid);
            if (online != null) plugin.stats().clearAll(online);
            for (PetData pet : pd.pets.values()) updateNeglect(pet, now);
            save(pd);
        }
        active.clear();
        byBase.clear();
    }

    public void reloadConfig() {
        combat.setTagSeconds(s().combatTagSeconds);
        for (ActivePet p : new ArrayList<>(active.values())) {
            PetType type = plugin.types().get(p.data.type);
            Rarity rarity = type == null ? null : plugin.rarities().get(type.rarity);
            if (type == null || rarity == null) {
                dismiss(p.owner, false, "reload");
                continue;
            }
            p.type = type;
            p.rarity = rarity;
            p.ability = plugin.abilities().get(type.ability);
            p.ability2 = plugin.abilities().get(type.ability2);
            reposition(p, false);
            refreshStats(p);
            refreshBonus(p);
        }
    }

    // ------------------------------------------------------------------ players

    public @Nullable PlayerData data(UUID player) {
        return players.get(player);
    }

    public Collection<PlayerData> loaded() {
        return Collections.unmodifiableCollection(players.values());
    }

    public boolean isLoading(UUID player) {
        return loading.contains(player);
    }

    public void join(Player player) {
        UUID id = player.getUniqueId();
        if (players.containsKey(id) || !loading.add(id)) return;
        plugin.storage().loadPlayer(id).whenComplete((data, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            loading.remove(id);
            if (err != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not load BoxPet data of " + player.getName(), err);
                return;
            }
            Player online = Bukkit.getPlayer(id);
            if (online == null) {
                save(data);
                return;
            }
            players.put(id, data);
            resetDay(data);
            long now = System.currentTimeMillis();
            for (PetData pet : data.pets.values()) updateNeglect(pet, now);
            plugin.collectionService().apply(online, data);
            applyHiddenFor(online);
            PetData pet = data.active();
            if (pet != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Player again = Bukkit.getPlayer(id);
                    if (again == null || active.containsKey(id) || data.activePet == null) return;
                    ActivePet ap = activate(again, data, data.active(), false);
                    if (ap != null) plugin.chat().say(ap, "owner-joined");
                }, s().joinAppearTicks);
            }
        }));
    }

    public void quit(Player player) {
        UUID id = player.getUniqueId();
        ActivePet p = active.remove(id);
        if (p != null) {
            plugin.abilityService().remember(p);
            despawnVisual(p, false);
            if (p.visual != null) byBase.remove(p.visual.baseId());
        }
        plugin.stats().clearAll(player);
        bonus.remove(id);
        combat.forget(id);
        if (swapCooldownLeft(id) <= 0) lastSwap.remove(id);
        PlayerData pd = players.remove(id);
        if (pd != null) {
            long now = System.currentTimeMillis();
            for (PetData pet : pd.pets.values()) updateNeglect(pet, now);
            save(pd);
        }
        for (ActivePet other : active.values()) other.hiddenViewers.remove(id);
    }

    public void resetDay(PlayerData pd) {
        long day = LocalDate.now().toEpochDay();
        if (pd.xpDay != day) {
            pd.xpDay = day;
            pd.dailyXp.clear();
            pd.dirty = true;
        }
    }

    // ------------------------------------------------------------------ saving

    public void save(PlayerData pd) {
        // items given to the pet (candy, food, eggs) left the inventory: write the inventory in the same moment as the
        // pet data, so a crash can never keep both the item and its effect
        if (pd.inventoryChanged) {
            pd.inventoryChanged = false;
            Player online = Bukkit.getPlayer(pd.uuid);
            if (online != null) {
                try {
                    online.saveData();
                } catch (RuntimeException ex) {
                    plugin.getLogger().log(Level.WARNING, "Could not save the inventory of " + online.getName(), ex);
                }
            }
        }
        List<PetData> pets = new ArrayList<>();
        for (PetData p : pd.pets.values()) pets.add(p.copy());
        for (PetData p : pd.removed) pets.add(p.copy());
        pd.removed.clear();
        List<IncubatorSlot> inc = new ArrayList<>(pd.incubator.values());
        plugin.storage().save(new Storage.Batch(pd.uuid, pd.activePet, pd.hideOthers, pd.petChat, pets, inc,
                new HashMap<>(pd.collection), pd.xpDay, new HashMap<>(pd.dailyXp),
                new Storage.AutoFeed(pd.autoFeed, pd.autoFeedAt, pd.autoFeedCandyFirst, pd.pantryText()), pd.castChat));
        pd.dirty = false;
        for (PetData p : pd.pets.values()) p.dirty = false;
    }

    private void saveAll() {
        for (PlayerData pd : players.values()) {
            boolean dirty = pd.dirty || pd.inventoryChanged || !pd.removed.isEmpty();
            if (!dirty) for (PetData p : pd.pets.values()) if (p.dirty) {
                dirty = true;
                break;
            }
            if (dirty) save(pd);
        }
    }

    // ------------------------------------------------------------------ pets

    public PetData createPet(UUID owner, PetType type, String origin) {
        long now = System.currentTimeMillis();
        PetData d = new PetData(UUID.randomUUID());
        d.owner = owner;
        d.type = type.id;
        d.rarity = type.rarity;
        d.level = 1;
        d.bond = s().bondStart;
        d.fullness = s().fullnessStart;
        d.hatchedAt = now;
        d.createdAt = now;
        d.origin = origin;
        d.dirty = true;
        return d;
    }

    public void addPet(PlayerData pd, PetData pet) {
        pet.owner = pd.uuid;
        pet.state = PetData.State.COLLECTION;
        pd.pets.put(pet.id, pet);
        pet.dirty = true;
        pd.dirty = true;
        plugin.collectionService().record(pd, pet);
    }

    public int capacity(Player player) {
        int best = s().defaultStorage;
        for (int n = s().maxStorage; n > best; n--) {
            if (player.hasPermission("boxpet.storage." + n)) return n;
        }
        return best;
    }

    public @Nullable PetType type(PetData d) {
        return plugin.types().get(d.type);
    }

    /** 0.5.0: the rarity of the pet's type (one pet = one rarity); the stored value only when the type is gone. */
    public Rarity rarity(PetData d) {
        PetType t = type(d);
        Rarity r = plugin.rarities().get(t != null ? t.rarity : d.rarity);
        return r == null ? plugin.rarities().lowest() : r;
    }

    /** {@code <rarity colour>Custom name} or the type's name. */
    /**
     * The shown name: the custom name (the player's own colours win, the pet's colour fills the rest) or the default
     * name in the pet's own colour.
     */
    public String displayName(PetData d) {
        PetType t = type(d);
        if (d.customName != null && !d.customName.isEmpty()) {
            return (t == null ? "<white>" : PetType.paint(t.color.contains(":") ? t.color.split(":")[0] : t.color, "")) + d.customName;
        }
        return t == null ? "<white>" + d.type : t.colouredName();
    }

    public @Nullable ActivePet active(UUID owner) {
        return active.get(owner);
    }

    public Collection<ActivePet> activePets() {
        return Collections.unmodifiableCollection(active.values());
    }

    public @Nullable ActivePet byBase(UUID baseId) {
        return byBase.get(baseId);
    }

    public PetView view(PetData d) {
        PetType t = type(d);
        boolean act = false;
        PlayerData pd = players.get(d.owner);
        if (pd != null) act = d.id.equals(pd.activePet);
        return new PetView(d.id, d.owner, d.type, displayName(d), d.rarity, t == null ? "support" : t.role.id, d.level, d.xp,
                d.stage, d.bond, d.fullness, d.ageDays(System.currentTimeMillis()), act);
    }

    // ------------------------------------------------------------------ summon / dismiss

    public long swapCooldownLeft(UUID player) {
        Long last = lastSwap.get(player);
        if (last == null) return 0;
        return Math.max(0, last + s().swapCooldownSeconds * 1000L - System.currentTimeMillis());
    }

    public SummonResult summon(Player player, PetData pet, boolean force) {
        PlayerData pd = players.get(player.getUniqueId());
        if (pd == null) return SummonResult.NOT_LOADED;
        if (pet.id.equals(pd.activePet) && active.containsKey(player.getUniqueId())) return SummonResult.ALREADY;
        if (!force && combat.tagged(player.getUniqueId())) return SummonResult.COMBAT;
        if (!force && swapCooldownLeft(player.getUniqueId()) > 0) return SummonResult.COOLDOWN;
        PetType type = type(pet);
        if (type == null) return SummonResult.UNKNOWN_TYPE;
        if (!PetVisual.exists(type.stage(pet.stage).model())) return SummonResult.NO_MODEL;
        PetSummonEvent ev = new PetSummonEvent(player, view(pet));
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return SummonResult.CANCELLED;
        if (pd.activePet != null) dismiss(player, false, "swap");
        long now = System.currentTimeMillis();
        pet.activeSince = now;
        pet.dirty = true;
        pd.activePet = pet.id;
        pd.dirty = true;
        lastSwap.put(player.getUniqueId(), now);
        ActivePet ap = activate(player, pd, pet, true);
        if (ap != null) plugin.chat().say(ap, "summon");
        save(pd);
        return SummonResult.OK;
    }

    private @Nullable ActivePet activate(Player player, PlayerData pd, @Nullable PetData pet, boolean effects) {
        if (pet == null) return null;
        PetType type = type(pet);
        if (type == null) {
            plugin.getLogger().warning("Pet " + pet.shortId() + " of " + player.getName() + " has unknown type " + pet.type);
            return null;
        }
        if (pet.activeSince <= 0) pet.activeSince = System.currentTimeMillis();
        ActivePet ap = new ActivePet(player, pd, pet, type, rarity(pet), plugin.abilities().get(type.ability));
        ap.ability2 = plugin.abilities().get(type.ability2);
        ap.nextIdleChat = System.currentTimeMillis() + randomIdle();
        ap.nextInteract = 400;
        ap.lastDrainCheck = System.currentTimeMillis();
        plugin.abilityService().restore(ap);
        active.put(player.getUniqueId(), ap);
        if (!player.isDead()) spawnVisual(ap, follower.spawnSlot(ap), effects);
        else ap.deadHidden = true;
        refreshStats(ap);
        refreshBonus(ap);
        return ap;
    }

    private long randomIdle() {
        int min = s().idleChatMinSeconds;
        int max = s().idleChatMaxSeconds;
        return (min + (long) (Math.random() * Math.max(1, max - min))) * 1000L;
    }

    /** Puts the active pet away: no stats, no ability, no model (spec 7). */
    public void dismiss(Player player, boolean message, String reason) {
        PlayerData pd = players.get(player.getUniqueId());
        ActivePet p = active.remove(player.getUniqueId());
        PetData pet = pd == null ? null : pd.active();
        if (p != null) {
            if (message) plugin.chat().say(p, "dismiss");
            plugin.abilityService().remember(p);
            despawnVisual(p, true);
        }
        plugin.stats().clearPet(player);
        // 0.8.0: a skill buff ends with the pet (stats taken by enemies stay until they run out)
        plugin.stats().clearOwnBuff(player);
        bonus.remove(player.getUniqueId());
        plugin.boxCore().ifPresent(h -> h.refreshBonus(player.getUniqueId()));
        if (pet != null) {
            long now = System.currentTimeMillis();
            if (pet.activeSince > 0) pet.neglectMs += now - pet.activeSince;
            pet.activeSince = 0;
            pet.dirty = true;
            Bukkit.getPluginManager().callEvent(new PetDismissEvent(player, view(pet)));
        }
        if (pd != null) {
            pd.activePet = null;
            pd.dirty = true;
            if (!reason.equals("swap")) lastSwap.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    // ------------------------------------------------------------------ visuals

    void spawnVisual(ActivePet p, Location at, boolean effects) {
        despawnVisual(p, false);
        PetType.Stage stage = p.type.stage(p.data.stage);
        PetVisual v = PetVisual.create(at, stage.model(), stage.scale(),
                s().viewDistance, p.type.animations.get("idle"), p.type.animations.get("walk"), true, plugin.getLogger());
        if (v == null) {
            plugin.getLogger().warning("Model " + stage.model() + " of pet type " + p.type.id + " is not loaded in ModelEngine");
            return;
        }
        p.visual = v;
        p.pos = at.clone();
        p.yaw = at.getYaw();
        p.anchor = at.clone();
        p.followingToSlot = false;
        p.wanderTarget = null;
        p.restTicks = 0;
        p.hiddenViewers.clear();
        byBase.put(v.baseId(), p);
        spawnNametag(p);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(p.owner)) continue;
            PlayerData vd = players.get(viewer.getUniqueId());
            if (vd != null && vd.hideOthers) setHidden(p, viewer, true);
        }
        if (hiddenState(p.owner)) {
            v.visible(false);
            if (p.nametag != null) p.nametag.setVisibleByDefault(false);
        }
        if (effects) {
            Effects.puff(at);
            playAnimation(p, "spawn");
        }
    }

    private boolean hiddenState(Player owner) {
        if (owner.getGameMode() == GameMode.SPECTATOR) return true;
        if (owner.hasMetadata("vanished") && owner.getMetadata("vanished").stream().anyMatch(m -> m.asBoolean())) return true;
        return s().hideWhenInvisible && owner.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    void despawnVisual(ActivePet p, boolean puff) {
        plugin.chat().removeBubble(p);
        plugin.chat().removeShowcase(p);
        if (p.visual != null) {
            if (puff && p.shown()) Effects.puff(p.pos);
            byBase.remove(p.visual.baseId());
            p.visual.remove();
            p.visual = null;
        }
        if (p.nametag != null) {
            p.nametag.remove();
            p.nametag = null;
        }
    }

    private void spawnNametag(ActivePet p) {
        if (p.nametag != null) p.nametag.remove();
        Location at = nametagLocation(p);
        TextDisplay td = at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.text(Text.parse(nametagText(p)));
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            // moved every nametag-update-ticks with the same interpolation, so it still glides smoothly
            d.setTeleportDuration(Math.min(59, s().nametagUpdateTicks + 1));
            d.setViewRange((float) (s().viewDistance / 64.0));
        });
        p.nametag = td;
        // same visibility as the model: viewers hiding this pet, and hidden states of the owner
        for (UUID viewer : p.hiddenViewers) {
            Player v = Bukkit.getPlayer(viewer);
            if (v != null) v.hideEntity(plugin, td);
        }
        if (p.visual != null && (hiddenState(p.owner) || p.travelHidden || p.teleporting || p.deadHidden)) td.setVisibleByDefault(false);
    }

    private Location nametagLocation(ActivePet p) {
        double h = p.visual == null ? 1 : p.visual.height();
        return p.pos.clone().add(0, h + s().nametagOffset, 0);
    }

    public String nametagText(ActivePet p) {
        String level = com.dex.boxpet.gui.PetIcons.level(plugin, p.data.level);
        String colour = level.substring(0, level.indexOf('>') + 1);
        return com.dex.boxpet.config.Messages.apply(s().nametagFormat, "name", displayName(p.data),
                "level_colour", colour, "level", String.valueOf(p.data.level),
                "hungry", p.hungry() ? plugin.messages().raw("nametag.hungry") : "");
    }

    public void refreshNametag(ActivePet p) {
        if (p.nametag != null) p.nametag.text(Text.parse(nametagText(p)));
    }

    /** Called by the follower after every move. */
    void afterMove(ActivePet p) {
        // the name tag is a real entity: moved every few ticks only (each move is a packet to every viewer)
        int every = s().nametagUpdateTicks;
        if (p.nametag != null && p.nametag.isValid() && (every <= 1 || (Bukkit.getCurrentTick() + p.phase) % every == 0)) {
            Location at = nametagLocation(p);
            if (at.getWorld() != p.nametag.getWorld()) spawnNametag(p);
            else if (at.distanceSquared(p.nametag.getLocation()) > 0.0025) p.nametag.teleport(at);
        }
        plugin.chat().tickBubble(p);
        plugin.chat().tickShowcase(p);
    }

    /** The server removed the name tag (its chunk unloaded, another plugin killed it): make a new one. */
    private void checkNametag(ActivePet p) {
        if (p.visual == null || p.visual.removed() || p.travelHidden || p.teleporting || p.deadHidden) return;
        if (p.nametag == null || !p.nametag.isValid()) spawnNametag(p);
    }

    public void playAnimation(ActivePet p, String state) {
        if (p.visual == null) return;
        String name = p.type.animations.get(state);
        if (name == null) name = state.equals("spawn") ? "" : state;
        if (!name.isEmpty()) p.visual.play(name);
    }

    /** Recreates the model next to the owner (world change, reload, after fast travel). */
    public void reposition(ActivePet p, boolean effects) {
        if (!p.owner.isOnline() || p.owner.isDead()) return;
        p.teleporting = false;
        spawnVisual(p, follower.spawnSlot(p), effects);
    }

    void teleportPet(ActivePet p, Location target) {
        if (p.visual == null) return;
        Effects.puff(p.pos);
        p.pos = target.clone();
        p.anchor = target.clone();
        p.followingToSlot = false;
        p.visual.move(p.pos, p.yaw, p.yaw);
        Effects.puff(p.pos);
        afterMove(p);
    }

    /** Owner teleported far in the same world: the pet appears next to them a moment later (spec 4.5). */
    public void onOwnerTeleport(ActivePet p) {
        if (p.visual == null || p.travelHidden) return;
        Effects.puff(p.pos);
        p.visual.visible(false);
        if (p.nametag != null) p.nametag.setVisibleByDefault(false);
        p.teleporting = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active.get(p.ownerId) != p || !p.owner.isOnline()) return;
            p.teleporting = false;
            reposition(p, true);
        }, s().teleportAppearTicks);
    }

    public void onWorldChange(ActivePet p) {
        despawnVisual(p, false);
        p.teleporting = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active.get(p.ownerId) != p || !p.owner.isOnline()) return;
            p.teleporting = false;
            reposition(p, true);
        }, s().teleportAppearTicks);
    }

    public void onOwnerDeath(ActivePet p) {
        if (p.shown()) plugin.chat().say(p, "owner-died");
        despawnVisual(p, true);
        p.deadHidden = true;
        refreshBonus(p);
    }

    public void onOwnerRespawn(ActivePet p) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active.get(p.ownerId) != p || !p.owner.isOnline() || p.owner.isDead()) return;
            p.deadHidden = false;
            reposition(p, true);
            refreshStats(p);
            refreshBonus(p);
        }, s().respawnAppearTicks);
    }

    void hideForTravel(ActivePet p) {
        if (p.visual != null) {
            Effects.puff(p.pos);
            p.visual.visible(false);
        }
        if (p.nametag != null) p.nametag.setVisibleByDefault(false);
        plugin.chat().removeBubble(p);
        plugin.chat().removeShowcase(p);
        p.travelHidden = true;
        p.mode = ActivePet.Mode.TRAVEL;
        refreshBonus(p);
    }

    void showAfterTravel(ActivePet p) {
        p.travelHidden = false;
        reposition(p, true);
        refreshBonus(p);
        plugin.chat().say(p, "arrived");
    }

    /** Vanish / spectator / invisibility changed: show or hide the model for everyone. */
    public void updateHiddenState(ActivePet p) {
        if (p.visual == null || p.travelHidden || p.teleporting) return;
        boolean hide = hiddenState(p.owner);
        p.visual.visible(!hide);
        if (p.nametag != null) p.nametag.setVisibleByDefault(!hide);
    }

    // ------------------------------------------------------------------ sit

    public String toggleSit(ActivePet p) {
        if (p.sitting) {
            stand(p, false);
            return "sit.off";
        }
        p.sitting = true;
        p.anchor = p.pos.clone();
        return "sit.on";
    }

    void stand(ActivePet p, boolean auto) {
        p.sitting = false;
        p.followingToSlot = true;
        if (auto) plugin.messages().send(p.owner, "sit.auto-off");
    }

    // ------------------------------------------------------------------ visibility

    private void setHidden(ActivePet p, Player viewer, boolean hidden) {
        if (p.visual == null) return;
        boolean was = p.hiddenViewers.contains(viewer.getUniqueId());
        if (was == hidden) return;
        if (hidden) p.hiddenViewers.add(viewer.getUniqueId());
        else p.hiddenViewers.remove(viewer.getUniqueId());
        p.visual.hiddenFor(viewer, hidden);
        if (p.nametag != null) {
            if (hidden) viewer.hideEntity(plugin, p.nametag);
            else viewer.showEntity(plugin, p.nametag);
        }
    }

    /** The viewer's "hide other players' pets" toggle (spec 4.3). */
    public void applyHiddenFor(Player viewer) {
        PlayerData vd = players.get(viewer.getUniqueId());
        boolean hide = vd != null && vd.hideOthers;
        for (ActivePet p : active.values()) {
            if (p.ownerId.equals(viewer.getUniqueId())) continue;
            setHidden(p, viewer, hide);
        }
    }

    /** At most {@code max-visible-per-player} other pets per viewer, closest first. Every 2 seconds. */
    private void visibleCap() {
        int max = s().maxVisiblePerPlayer;
        double range = (double) s().viewDistance * s().viewDistance;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            PlayerData vd = players.get(viewer.getUniqueId());
            if (vd == null || vd.hideOthers) continue;
            List<ActivePet> near = new ArrayList<>();
            for (ActivePet p : active.values()) {
                if (p.ownerId.equals(viewer.getUniqueId()) || p.visual == null) continue;
                if (p.pos.getWorld() != viewer.getWorld()) continue;
                if (p.pos.distanceSquared(viewer.getLocation()) <= range) near.add(p);
            }
            if (near.size() <= max) {
                for (ActivePet p : near) setHidden(p, viewer, false);
                continue;
            }
            Location vl = viewer.getLocation();
            near.sort((a, b) -> Double.compare(a.pos.distanceSquared(vl), b.pos.distanceSquared(vl)));
            for (int i = 0; i < near.size(); i++) setHidden(near.get(i), viewer, i >= max);
        }
    }

    // ------------------------------------------------------------------ stats / boost

    public void refreshStats(ActivePet p) {
        if (!p.owner.isOnline()) return;
        if (plugin.stats().applyPet(p.owner, p, s())) {
            p.statRetries = 0;
            return;
        }
        if (p.statRetries++ < 15) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (active.get(p.ownerId) == p) refreshStats(p);
            }, 20L);
        }
    }

    /** Recomputes the boost bonus of the active pet and tells BoxCore (spec 8). */
    public void refreshBonus(ActivePet p) {
        Map<String, Integer> m = new HashMap<>();
        Ability second = p.hasSecond(s().secondSkillLevel) ? p.ability2 : null;
        for (Ability a : new Ability[]{p.ability, second}) {
            if (a == null || a.type != Ability.Type.BOOST || a.boostType.isEmpty() || p.abilityPaused()) continue;
            int tenths = (int) Math.floor(a.boostTenths.at(p.data.level, p.rarity.statMultiplier()));
            if (a.boostWhole) tenths -= tenths % 10;
            if (tenths > 0) m.merge(a.boostType, tenths, Integer::sum);
        }
        if (m.isEmpty()) bonus.remove(p.ownerId);
        else bonus.put(p.ownerId, m);
        plugin.boxCore().ifPresent(h -> h.refreshBonus(p.ownerId));
    }

    /** Thread-safe (BoxCore may ask from its own recompute). */
    public int bonusTenths(UUID player, String type) {
        Map<String, Integer> m = bonus.get(player);
        if (m == null || type == null) return 0;
        Integer v = m.get(type);
        return v == null ? 0 : v;
    }

    // ------------------------------------------------------------------ neglect (spec 11.2, 11.6)

    /** Applies bond loss for every full neglect period. @return true when the bond changed */
    public boolean updateNeglect(PetData d, long now) {
        long total = d.neglectMs + (d.activeSince > 0 ? Math.max(0, now - d.activeSince) : 0);
        int steps = (int) Math.min(Integer.MAX_VALUE, total / s().neglectMillis);
        if (steps <= d.neglectSteps) return false;
        int lost = s().neglectDecay * (steps - d.neglectSteps);
        d.neglectSteps = steps;
        int before = d.bond;
        d.bond = Math.max(0, d.bond - lost);
        d.dirty = true;
        return before != d.bond;
    }

    // ------------------------------------------------------------------ ticks

    private void tick() {
        for (ActivePet p : new ArrayList<>(active.values())) {
            try {
                follower.tick(p);
                plugin.abilityService().tick(p);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Pet tick failed for " + p.owner.getName(), ex);
            }
        }
    }

    private void second() {
        seconds++;
        if (seconds % 300 == 0) {
            // swap cooldowns of players who left while it was running
            lastSwap.keySet().removeIf(id -> !players.containsKey(id) && swapCooldownLeft(id) <= 0);
        }
        long now = System.currentTimeMillis();
        for (ActivePet p : new ArrayList<>(active.values())) {
            try {
                plugin.care().second(p, now);
                if (seconds % 60 == 0) {
                    if (updateNeglect(p.data, now)) {
                        refreshStats(p);
                        plugin.chat().say(p, "low-bond");
                    }
                    plugin.xp().online(p);
                }
                if (now >= p.nextIdleChat) {
                    p.nextIdleChat = now + randomIdle();
                    if (p.shown() && !combat.tagged(p.ownerId)) plugin.chat().say(p, "idle");
                }
                updateHiddenState(p);
                checkNametag(p);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Pet second task failed for " + p.owner.getName(), ex);
            }
        }
        if (seconds % 2 == 0) visibleCap();
        if (seconds % 60 == 0) {
            for (PlayerData pd : players.values()) resetDay(pd);
            plugin.cataMines().ifPresent(com.dex.boxpet.hook.CataMinesHook::refresh);
        }
    }
}
