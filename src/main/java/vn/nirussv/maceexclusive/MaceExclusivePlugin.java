package vn.nirussv.maceexclusive;

import org.bukkit.plugin.java.JavaPlugin;
import vn.nirussv.maceexclusive.ability.AbilityService;
import vn.nirussv.maceexclusive.command.MaceCommand;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.config.ResourceBootstrap;
import vn.nirussv.maceexclusive.core.CoreCraftListener;
import vn.nirussv.maceexclusive.core.CoreItemFactory;
import vn.nirussv.maceexclusive.core.CoreRegistry;
import vn.nirussv.maceexclusive.core.RitualService;
import vn.nirussv.maceexclusive.curse.CurseService;
import vn.nirussv.maceexclusive.curse.LockoutService;
import vn.nirussv.maceexclusive.listener.CursedSwordListener;
import vn.nirussv.maceexclusive.effect.FreezeService;
import vn.nirussv.maceexclusive.forge.ForgeListener;
import vn.nirussv.maceexclusive.forge.ForgeService;
import vn.nirussv.maceexclusive.forge.ForgeVisualService;
import vn.nirussv.maceexclusive.item.ExclusiveItemFactory;
import vn.nirussv.maceexclusive.item.ItemMatcher;
import vn.nirussv.maceexclusive.item.ItemRegistry;
import vn.nirussv.maceexclusive.item.PdcKeys;
import vn.nirussv.maceexclusive.listener.AbilityListener;
import vn.nirussv.maceexclusive.listener.ContainerGuardListener;
import vn.nirussv.maceexclusive.listener.EffectMaceListener;
import vn.nirussv.maceexclusive.listener.MaceListener;
import vn.nirussv.maceexclusive.listener.SpecialItemListener;
import vn.nirussv.maceexclusive.mace.MaceManager;
import vn.nirussv.maceexclusive.mace.MaceRepository;
import vn.nirussv.maceexclusive.mace.MaceTrackerService;
import vn.nirussv.maceexclusive.discord.DiscordWebhookService;
import vn.nirussv.maceexclusive.persistence.ForgeSessionStore;
import vn.nirussv.maceexclusive.recipe.RecipeRegistry;

import java.util.logging.Level;

public class MaceExclusivePlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MaceRepository maceRepository;
    private MaceManager maceManager;
    private CurseService curseService;
    private LockoutService lockoutService;
    private MaceTrackerService maceTrackerService;
    private AbilityService abilityService;
    private FreezeService freezeService;
    private RecipeRegistry recipeRegistry;
    private ForgeService forgeService;
    private ItemRegistry itemRegistry;
    private CoreRegistry coreRegistry;
    private ExclusiveItemFactory itemFactory;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            ResourceBootstrap.BootstrapSummary bootstrapSummary = ResourceBootstrap.ensureAll(this);
            getLogger().info("Resource bootstrap: copied=" + bootstrapSummary.copied()
                + ", missingBundled=" + bootstrapSummary.missing()
                + ", expected(root/items/cores)=" + bootstrapSummary.rootExpected() + "/"
                + bootstrapSummary.itemsExpected() + "/" + bootstrapSummary.coresExpected() + ".");
            reloadConfig();
            this.configManager = new ConfigManager(this);
            this.configManager.reload();
            PdcKeys pdcKeys = new PdcKeys();
            this.itemRegistry = new ItemRegistry(this, configManager);
            this.itemRegistry.reload();
            this.coreRegistry = new CoreRegistry(this);
            this.coreRegistry.reload();
            ItemMatcher itemMatcher = new ItemMatcher(pdcKeys, configManager);
            this.itemFactory = new ExclusiveItemFactory(configManager, pdcKeys, itemRegistry);
            CoreItemFactory coreItemFactory = new CoreItemFactory(coreRegistry, configManager, pdcKeys);
            this.maceRepository = new MaceRepository(this);
            this.maceTrackerService = new MaceTrackerService(this, configManager, maceRepository, itemMatcher);
            DiscordWebhookService discordWebhookService = new DiscordWebhookService(this);
            this.maceManager = new MaceManager(maceRepository, configManager, itemMatcher, itemRegistry, pdcKeys, maceTrackerService, discordWebhookService);
            this.lockoutService = new LockoutService();
            this.curseService = new CurseService(this, configManager, itemMatcher);
            this.freezeService = new FreezeService(this);
            this.abilityService = new AbilityService(this, configManager, itemMatcher, freezeService);
            // Phase 3 is mace-first: spear gameplay service/listener stays disabled until Phase 4.
            this.recipeRegistry = new RecipeRegistry(this, configManager, itemRegistry, itemFactory, coreRegistry, coreItemFactory);
            this.forgeService = new ForgeService(this, configManager, itemFactory, itemRegistry, maceManager, new ForgeSessionStore(this), new ForgeVisualService());

            MaceCommand cmd = new MaceCommand(this, maceManager, configManager, itemFactory, itemRegistry);
            if (getCommand("macee") != null) {
                getCommand("macee").setExecutor(cmd);
                getCommand("macee").setTabCompleter(cmd);
            } else {
                getLogger().severe("Command 'macee' not found in plugin.yml!");
            }

            getServer().getPluginManager().registerEvents(new MaceListener(maceManager), this);
            getServer().getPluginManager().registerEvents(maceTrackerService, this);
            getServer().getPluginManager().registerEvents(new ContainerGuardListener(maceManager, configManager), this);
            getServer().getPluginManager().registerEvents(new EffectMaceListener(this, maceManager, configManager), this);
            getServer().getPluginManager().registerEvents(new AbilityListener(abilityService), this);
            getServer().getPluginManager().registerEvents(freezeService, this);
            getServer().getPluginManager().registerEvents(new ForgeListener(forgeService, maceManager), this);
            getServer().getPluginManager().registerEvents(new CoreCraftListener(configManager, coreRegistry, coreItemFactory, itemMatcher, freezeService, lockoutService), this);
            getServer().getPluginManager().registerEvents(new CursedSwordListener(lockoutService, configManager, itemMatcher), this);
            getServer().getPluginManager().registerEvents(new RitualService(coreItemFactory, itemMatcher), this);
            getServer().getPluginManager().registerEvents(new SpecialItemListener(itemFactory, itemMatcher), this);

            try {
                getServer().getPluginManager().registerEvents(curseService, this);
                curseService.start();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to start CurseService", e);
            }
            if (configManager.shouldRemoveVanillaMaceRecipe()) recipeRegistry.removeVanillaMaceRecipe();
            recipeRegistry.registerAll();
            forgeService.start();
            getLogger().info("Mace-Exclusive has been enabled! Version: " + getDescription().getVersion());
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "CRITICAL ERROR: Failed to enable Mace-Exclusive!", t);
        }
    }

    @Override
    public void onDisable() {
        if (maceTrackerService != null) maceTrackerService.shutdown();
        if (curseService != null) curseService.shutdown();
        if (freezeService != null) freezeService.shutdown();
        if (forgeService != null) forgeService.shutdown();
        if (maceRepository != null) maceRepository.save();
    }

    public ExclusiveItemFactory getItemFactory() { return itemFactory; }
    public MaceManager getMaceManager() { return maceManager; }
    public ConfigManager getConfigManager() { return configManager; }
}
