package com.dex.boxpet.listener;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.XpSources;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.plugin.EventExecutor;

/** Activity XP (spec 9.1): kills, mining, BoxQuest quests, MyBoxMobs mobs. */
public final class XpListener implements Listener {

    private final BoxPetPlugin plugin;
    private final Map<UUID, Map<UUID, Long>> lastVictim = new HashMap<>();
    private final Set<UUID> handledByMbm = new HashSet<>();

    public XpListener(BoxPetPlugin plugin) {
        this.plugin = plugin;
    }

    private XpSources src() {
        return plugin.xpSources();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKill(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || plugin.pets().active(killer.getUniqueId()) == null) return;
        if (src().blockSameIp && sameIp(killer, victim)) return;
        long now = System.currentTimeMillis();
        long window = src().sameVictimCooldownSeconds * 1000L;
        if (lastVictim.size() > 200) {
            // killers who left with a running cooldown: drop them once it is over
            lastVictim.values().forEach(m -> m.values().removeIf(t -> now - t >= window));
            lastVictim.values().removeIf(Map::isEmpty);
        }
        Map<UUID, Long> seen = lastVictim.computeIfAbsent(killer.getUniqueId(), k -> new HashMap<>());
        Long last = seen.get(victim.getUniqueId());
        if (last != null && now - last < src().sameVictimCooldownSeconds * 1000L) return;
        seen.put(victim.getUniqueId(), now);
        if (seen.size() > 100) seen.values().removeIf(t -> now - t > src().sameVictimCooldownSeconds * 1000L);
        plugin.xp().give(killer, XpSources.PLAYER_KILL, src().get(XpSources.PLAYER_KILL).amount());
        var pet = plugin.pets().active(killer.getUniqueId());
        if (pet != null) plugin.chat().say(pet, "owner-kill");
    }

    /** Logout: drop expired kill cooldowns only (relogging must not reset a running cooldown). */
    public void forget(UUID player) {
        Map<UUID, Long> seen = lastVictim.get(player);
        if (seen == null) return;
        long now = System.currentTimeMillis();
        seen.values().removeIf(t -> now - t >= src().sameVictimCooldownSeconds * 1000L);
        if (seen.isEmpty()) lastVictim.remove(player);
    }

    private static boolean sameIp(Player a, Player b) {
        InetSocketAddress x = a.getAddress();
        InetSocketAddress y = b.getAddress();
        return x != null && y != null && x.getAddress() != null && x.getAddress().equals(y.getAddress());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player) return;
        Player killer = dead.getKiller();
        if (killer == null || plugin.pets().active(killer.getUniqueId()) == null) return;
        UUID id = dead.getUniqueId();
        boolean spawner = dead.fromMobSpawner();
        String type = dead.getType().name();
        // MyBoxMobs fires its event during the death: decide one tick later
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (handledByMbm.remove(id)) return;
            double amount = src().mobOverrides.getOrDefault(type, src().get(XpSources.MOB_KILL).amount());
            if (spawner) amount *= src().spawnerMultiplier;
            if (killer.isOnline()) plugin.xp().give(killer, XpSources.MOB_KILL, amount);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (plugin.pets().active(p.getUniqueId()) == null) return;
        XpSources s = src();
        boolean counts = s.miningEverywhere;
        if (!counts && s.miningCataMines && plugin.cataMines().isPresent()) counts = plugin.cataMines().get().inMine(e.getBlock());
        if (!counts && !s.miningRegions.isEmpty() && plugin.worldGuard().isPresent()) {
            counts = plugin.worldGuard().get().inRegion(e.getBlock().getLocation(), s.miningRegions);
        }
        if (counts) plugin.xp().give(p, XpSources.MINING, s.get(XpSources.MINING).amount());
    }

    // ------------------------------------------------------------------ optional plugins (reflection)

    /** BoxQuest 0.3.2 {@code QuestCompleteEvent} and MyBoxMobs {@code MbmMobDeathEvent}, when installed. */
    @SuppressWarnings("unchecked")
    public void registerOptional() {
        try {
            Class<? extends Event> quest = (Class<? extends Event>) Class.forName("vn.boxpvp.boxquest.api.QuestCompleteEvent");
            EventExecutor exec = (l, ev) -> {
                if (quest.isInstance(ev) && ev instanceof PlayerEvent pe) {
                    plugin.xp().give(pe.getPlayer(), XpSources.QUEST, src().get(XpSources.QUEST).amount());
                }
            };
            Bukkit.getPluginManager().registerEvent(quest, this, EventPriority.MONITOR, exec, plugin, true);
            plugin.getLogger().info("BoxQuest found: quests give pet XP");
        } catch (ClassNotFoundException ex) {
            if (Bukkit.getPluginManager().isPluginEnabled("BoxQuest")) {
                plugin.getLogger().warning("BoxQuest is older than 0.3.2: quests give no pet XP until it is updated");
            }
        }
        try {
            Class<? extends Event> mbm = (Class<? extends Event>) Class.forName("com.dex.myboxmobs.api.event.MbmMobDeathEvent");
            Method getEntity = mbm.getMethod("getEntity");
            Method getMobId = mbm.getMethod("getMobId");
            Method getKiller = mbm.getMethod("getKiller");
            EventExecutor exec = (l, ev) -> {
                if (!mbm.isInstance(ev)) return;
                try {
                    org.bukkit.entity.Entity ent = (org.bukkit.entity.Entity) getEntity.invoke(ev);
                    String mobId = String.valueOf(getMobId.invoke(ev)).toLowerCase(Locale.ROOT);
                    UUID killerId = (UUID) getKiller.invoke(ev);
                    if (ent != null) {
                        UUID entId = ent.getUniqueId();
                        handledByMbm.add(entId);
                        // always forgotten 2 ticks later, whoever killed it (no growing set)
                        Bukkit.getScheduler().runTaskLater(plugin, () -> handledByMbm.remove(entId), 2L);
                    }
                    Player killer = killerId == null ? null : Bukkit.getPlayer(killerId);
                    if (killer == null) return;
                    double amount = src().myBoxMobsOverrides.getOrDefault(mobId, src().myBoxMobsDefault);
                    plugin.xp().give(killer, XpSources.BOSS_KILL, amount);
                    var pet = plugin.pets().active(killer.getUniqueId());
                    if (pet != null && amount >= src().get(XpSources.BOSS_KILL).amount()) plugin.chat().say(pet, "owner-kill");
                } catch (ReflectiveOperationException ignored) {
                    // API changed: no XP
                }
            };
            Bukkit.getPluginManager().registerEvent(mbm, this, EventPriority.MONITOR, exec, plugin, false);
            plugin.getLogger().info("MyBoxMobs found: its mobs and bosses give pet XP");
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // MyBoxMobs not installed
        }
    }
}
