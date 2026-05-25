package vn.nirussv.maceexclusive;

import org.bukkit.plugin.java.JavaPlugin;
import vn.nirussv.maceexclusive.ability.AbilityService;
import vn.nirussv.maceexclusive.command.MaceCommand;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.curse.CurseService;
import vn.nirussv.maceexclusive.effect.FreezeService;
import vn.nirussv.maceexclusive.forge.ForgeListener;
import vn.nirussv.maceexclusive.forge.ForgeService;
import vn.nirussv.maceexclusive.listener.AbilityListener;
import vn.nirussv.maceexclusive.listener.ChaosMaceListener;
import vn.nirussv.maceexclusive.listener.ContainerGuardListener;
import vn.nirussv.maceexclusive.listener.EffectMaceListener;
import vn.nirussv.maceexclusive.listener.MaceListener;
import vn.nirussv.maceexclusive.listener.SpearListener;
import vn.nirussv.maceexclusive.mace.MaceFactory;
import vn.nirussv.maceexclusive.mace.MaceManager;
import vn.nirussv.maceexclusive.mace.MaceRepository;
import vn.nirussv.maceexclusive.persistence.ForgeSessionStore;
import vn.nirussv.maceexclusive.projectile.SpearProjectileService;
import vn.nirussv.maceexclusive.recipe.RecipeRegistry;

import java.util.logging.Level;

public class MaceExclusivePlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MaceFactory maceFactory;
    private MaceRepository maceRepository;
    private MaceManager maceManager;
    private CurseService curseService;
    private AbilityService abilityService;
    private FreezeService freezeService;
    private SpearProjectileService spearProjectileService;
    private RecipeRegistry recipeRegistry;
    private ForgeService forgeService;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            reloadConfig();
            
            this.configManager = new ConfigManager(this);
            if (!getConfig().contains("settings.language")) {
                getLogger().warning("Config.yml might be corrupt or missing settings!");
            }
            this.configManager.reload();
            
            this.maceFactory = new MaceFactory(this, configManager);
            this.maceRepository = new MaceRepository(this);
            this.maceManager = new MaceManager(this, maceRepository, configManager, maceFactory);
            this.curseService = new CurseService(this, configManager, maceFactory.getItemMatcher());
            this.abilityService = new AbilityService(configManager, maceManager);
            this.freezeService = new FreezeService(this);
            this.spearProjectileService = new SpearProjectileService(this, configManager, maceFactory.getItemMatcher(), freezeService);
            this.recipeRegistry = new RecipeRegistry(this, configManager, maceFactory);
            this.forgeService = new ForgeService(this, configManager, maceFactory, maceManager, new ForgeSessionStore(this));
            
            MaceCommand cmd = new MaceCommand(this, maceManager, configManager, maceFactory);
            if (getCommand("macee") != null) {
                getCommand("macee").setExecutor(cmd);
                getCommand("macee").setTabCompleter(cmd);
            } else {
                getLogger().severe("Command 'macee' not found in plugin.yml!");
            }
            
            getServer().getPluginManager().registerEvents(
                new MaceListener(this, maceManager, configManager, maceFactory), this);
            getServer().getPluginManager().registerEvents(
                new ContainerGuardListener(maceManager, configManager), this);
            getServer().getPluginManager().registerEvents(
                new EffectMaceListener(this, maceManager, configManager), this);
            getServer().getPluginManager().registerEvents(
                new ChaosMaceListener(this, maceManager, configManager, maceFactory), this);
            getServer().getPluginManager().registerEvents(
                new AbilityListener(abilityService), this);
            getServer().getPluginManager().registerEvents(freezeService, this);
            getServer().getPluginManager().registerEvents(
                new SpearListener(spearProjectileService), this);
            getServer().getPluginManager().registerEvents(
                new ForgeListener(this, forgeService, maceFactory), this);

            try {
                getServer().getPluginManager().registerEvents(curseService, this);
                curseService.start();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to start CurseService", e);
            }
            
            if (configManager.shouldRemoveVanillaMaceRecipe()) {
                recipeRegistry.removeVanillaMaceRecipe();
            }
            recipeRegistry.registerAll();
            forgeService.start();
            
            getLogger().info("Mace-Exclusive has been enabled! Version: " + getDescription().getVersion());
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "CRITICAL ERROR: Failed to enable Mace-Exclusive!", t);
        }
    }

    @Override
    public void onDisable() {
        if (curseService != null) {
            curseService.shutdown();
        }
        if (spearProjectileService != null) {
            spearProjectileService.shutdown();
        }
        if (freezeService != null) {
            freezeService.shutdown();
        }
        if (forgeService != null) {
            forgeService.shutdown();
        }
        if (maceRepository != null) {
            maceRepository.save();
        }
    }

    public MaceFactory getMaceFactory() {
        return maceFactory;
    }

    public MaceManager getMaceManager() {
        return maceManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
