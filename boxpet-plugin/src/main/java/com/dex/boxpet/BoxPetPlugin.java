package com.dex.boxpet;

import com.dex.boxpet.ability.AbilityService;
import com.dex.boxpet.api.BoxPetService;
import com.dex.boxpet.command.AdminCommand;
import com.dex.boxpet.command.PetCommand;
import com.dex.boxpet.config.Abilities;
import com.dex.boxpet.config.CollectionConfig;
import com.dex.boxpet.config.ConfigFiles;
import com.dex.boxpet.config.Dialogues;
import com.dex.boxpet.config.ItemsConfig;
import com.dex.boxpet.config.Levels;
import com.dex.boxpet.config.MenuTexts;
import com.dex.boxpet.config.Messages;
import com.dex.boxpet.config.PetTypes;
import com.dex.boxpet.config.Rarities;
import com.dex.boxpet.config.Settings;
import com.dex.boxpet.config.XpSources;
import com.dex.boxpet.data.Storage;
import com.dex.boxpet.gui.ChatInput;
import com.dex.boxpet.gui.GuiListener;
import com.dex.boxpet.hook.BoxCoreHook;
import com.dex.boxpet.hook.CataMinesHook;
import com.dex.boxpet.hook.ClanHook;
import com.dex.boxpet.hook.ItemsAdderHook;
import com.dex.boxpet.hook.PapiExpansion;
import com.dex.boxpet.hook.VaultHook;
import com.dex.boxpet.hook.WorldGuardHook;
import com.dex.boxpet.item.ItemFactory;
import com.dex.boxpet.listener.CombatListener;
import com.dex.boxpet.listener.InteractListener;
import com.dex.boxpet.listener.PlayerListener;
import com.dex.boxpet.listener.XpListener;
import com.dex.boxpet.pet.CareService;
import com.dex.boxpet.pet.ChatService;
import com.dex.boxpet.pet.CollectionService;
import com.dex.boxpet.pet.IncubatorService;
import com.dex.boxpet.pet.PetActions;
import com.dex.boxpet.pet.PetManager;
import com.dex.boxpet.pet.StatService;
import com.dex.boxpet.pet.XpService;
import com.dex.boxpet.text.Text;
import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** BoxPet: 3D pets on ModelEngine dummies with MMOItems stats (BOXPET_SPEC.md). */
public final class BoxPetPlugin extends JavaPlugin {

    private static final String[] FILES = {"config.yml", "messages.yml", "menus.yml", "rarities.yml", "abilities.yml",
            "levels.yml", "xp-sources.yml", "items.yml", "dialogues.yml", "collection.yml"};

    private Settings settings;
    private final Messages messages = new Messages(this);
    private final MenuTexts menus = new MenuTexts(this);
    private Rarities rarities;
    private PetTypes types;
    private Abilities abilities;
    private Levels levels;
    private XpSources xpSources;
    private ItemsConfig items;
    private Dialogues dialogues;
    private CollectionConfig collectionConfig;

    private Storage storage;
    private StatService stats;
    private ChatService chat;
    private PetManager pets;
    private CollectionService collectionService;
    private CareService care;
    private XpService xp;
    private AbilityService abilityService;
    private ItemFactory itemFactory;
    private IncubatorService incubator;
    private PetActions actions;
    private ChatInput input;
    private InteractListener interact;
    private XpListener xpListener;

    private BoxCoreHook boxCore;
    private CataMinesHook cataMines;
    private WorldGuardHook worldGuard;
    private ClanHook clans;
    private VaultHook vault;
    private ItemsAdderHook itemsAdder;
    private boolean enabled;

    @Override
    public void onLoad() {
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                WorldGuardHook.registerFlag(getLogger());
            } catch (LinkageError | RuntimeException ex) {
                getLogger().warning("WorldGuard flag boxpet-ability not registered: " + ex);
            }
        }
    }

    @Override
    public void onEnable() {
        for (String f : FILES) ConfigFiles.ensure(this, f);
        ConfigFiles.ensureFolder(this, "pets");
        loadConfigs();
        storage = new Storage(settings, getDataFolder(), getLogger());
        try {
            storage.init();
        } catch (SQLException ex) {
            getLogger().log(Level.SEVERE, "BoxPet database could not be opened, disabling", ex);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        hooks();
        stats = new StatService(getLogger());
        chat = new ChatService(this);
        itemFactory = new ItemFactory(this);
        incubator = new IncubatorService(this);
        collectionService = new CollectionService(this);
        care = new CareService(this);
        xp = new XpService(this);
        abilityService = new AbilityService(this);
        actions = new PetActions(this);
        input = new ChatInput(this);
        pets = new PetManager(this);
        interact = new InteractListener(this);

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(input, this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(interact, this);
        xpListener = new XpListener(this);
        pm.registerEvents(xpListener, this);
        xpListener.registerOptional();

        command("pet", new PetCommand(this));
        command("boxpet", new AdminCommand(this));

        Bukkit.getServicesManager().register(BoxPetService.class, new ServiceImpl(this), this, ServicePriority.Normal);
        if (boxCore != null) boxCore.registerBonus(this, (player, type) -> pets.bonusTenths(player, type));
        if (pm.isPluginEnabled("PlaceholderAPI")) {
            try {
                new PapiExpansion(this).register();
                Text.setPlaceholderResolver(me.clip.placeholderapi.PlaceholderAPI::setPlaceholders);
            } catch (LinkageError ex) {
                getLogger().warning("PlaceholderAPI hook failed: " + ex);
            }
        }
        abilityService.start();
        pets.start();
        if (cataMines != null) Bukkit.getScheduler().runTaskLater(this, cataMines::refresh, 60L);
        enabled = true;
        getLogger().info("BoxPet " + getPluginMeta().getVersion() + ": " + types.size() + " pets, " + abilities.size()
                + " abilities, storage " + settings.storageType);
    }

    @Override
    public void onDisable() {
        if (!enabled) {
            if (storage != null) storage.close();
            return;
        }
        GuiListener.closeAll();
        abilityService.stop();
        pets.stop();
        storage.close();
        Text.setPlaceholderResolver(null);
    }

    private void command(String name, Object executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) return;
        cmd.setExecutor((org.bukkit.command.CommandExecutor) executor);
        cmd.setTabCompleter((org.bukkit.command.TabCompleter) executor);
    }

    private void loadConfigs() {
        reloadConfig();
        YamlConfiguration config = ConfigFiles.load(this, "config.yml");
        settings = new Settings(config);
        com.dex.boxpet.pet.PetVisual.idleLoop = settings.idleLoop;
        ConfigurationSection pal = config.getConfigurationSection("style.palette");
        if (pal != null) {
            Map<String, String> colours = new HashMap<>();
            for (String k : pal.getKeys(false)) colours.put(k, pal.getString(k));
            Text.setPalette(colours);
        }
        messages.load();
        menus.load();
        rarities = new Rarities(ConfigFiles.load(this, "rarities.yml"), getLogger());
        types = new PetTypes(new File(getDataFolder(), "pets"), rarities, getLogger());
        abilities = new Abilities(ConfigFiles.load(this, "abilities.yml"), getLogger(),
                settings.abilityDamageMultiplier, settings.abilityHealMultiplier);
        levels = new Levels(ConfigFiles.load(this, "levels.yml"), getLogger());
        xpSources = new XpSources(ConfigFiles.load(this, "xp-sources.yml"));
        items = new ItemsConfig(ConfigFiles.load(this, "items.yml"));
        dialogues = new Dialogues(ConfigFiles.load(this, "dialogues.yml"));
        collectionConfig = new CollectionConfig(ConfigFiles.load(this, "collection.yml"));
    }

    /** /boxpet reload. The database settings are only read at startup. */
    public void reloadAll() {
        GuiListener.closeAll();
        loadConfigs();
        pets.reloadConfig();
        if (cataMines != null) cataMines.refresh();
    }

    private void hooks() {
        var pm = Bukkit.getPluginManager();
        if (pm.isPluginEnabled("BoxCore")) {
            try {
                boxCore = new BoxCoreHook(getLogger());
            } catch (LinkageError ex) {
                getLogger().warning("BoxCore API not found: " + ex);
            }
        }
        if (CataMinesHook.present()) cataMines = new CataMinesHook(getLogger());
        if (pm.isPluginEnabled("WorldGuard")) {
            try {
                worldGuard = new WorldGuardHook();
            } catch (LinkageError ex) {
                getLogger().warning("WorldGuard API not found: " + ex);
            }
        }
        if (pm.isPluginEnabled("BoxClans")) {
            try {
                clans = new ClanHook();
            } catch (LinkageError ex) {
                getLogger().warning("BoxClans API not found: " + ex);
            }
        }
        if (pm.isPluginEnabled("Vault")) {
            try {
                vault = new VaultHook();
            } catch (LinkageError ex) {
                getLogger().warning("Vault API not found: " + ex);
            }
        }
        itemsAdder = ItemsAdderHook.create(this);
    }

    // ------------------------------------------------------------------ accessors

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public MenuTexts menus() {
        return menus;
    }

    public Rarities rarities() {
        return rarities;
    }

    public PetTypes types() {
        return types;
    }

    public Abilities abilities() {
        return abilities;
    }

    public Levels levels() {
        return levels;
    }

    public XpSources xpSources() {
        return xpSources;
    }

    public ItemsConfig items() {
        return items;
    }

    public Dialogues dialogues() {
        return dialogues;
    }

    public CollectionConfig collectionConfig() {
        return collectionConfig;
    }

    public Storage storage() {
        return storage;
    }

    public StatService stats() {
        return stats;
    }

    public ChatService chat() {
        return chat;
    }

    public PetManager pets() {
        return pets;
    }

    public CollectionService collectionService() {
        return collectionService;
    }

    public CareService care() {
        return care;
    }

    public XpService xp() {
        return xp;
    }

    public AbilityService abilityService() {
        return abilityService;
    }

    public ItemFactory itemFactory() {
        return itemFactory;
    }

    public IncubatorService incubator() {
        return incubator;
    }

    public PetActions actions() {
        return actions;
    }

    public ChatInput input() {
        return input;
    }

    public InteractListener interact() {
        return interact;
    }

    public XpListener xpListener() {
        return xpListener;
    }

    public Optional<BoxCoreHook> boxCore() {
        return Optional.ofNullable(boxCore);
    }

    public Optional<CataMinesHook> cataMines() {
        return Optional.ofNullable(cataMines);
    }

    public Optional<WorldGuardHook> worldGuard() {
        return Optional.ofNullable(worldGuard);
    }

    public Optional<ClanHook> clans() {
        return Optional.ofNullable(clans);
    }

    public Optional<VaultHook> vault() {
        return Optional.ofNullable(vault);
    }

    public Optional<ItemsAdderHook> itemsAdder() {
        return Optional.ofNullable(itemsAdder);
    }
}
