package vn.nirussv.maceexclusive;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import vn.nirussv.maceexclusive.command.MaceCommand;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.listener.ChaosMaceListener;
import vn.nirussv.maceexclusive.listener.EffectMaceListener;
import vn.nirussv.maceexclusive.listener.MaceListener;
import vn.nirussv.maceexclusive.mace.MaceFactory;
import vn.nirussv.maceexclusive.mace.MaceManager;
import vn.nirussv.maceexclusive.mace.MaceRepository;
import vn.nirussv.maceexclusive.mace.MaceType;
import vn.nirussv.maceexclusive.task.MaceEffectTask;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

public class MaceExclusivePlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MaceFactory maceFactory;
    private MaceRepository maceRepository;
    private MaceManager maceManager;

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
            
            this.maceFactory = new MaceFactory(this);
            this.maceRepository = new MaceRepository(this);
            this.maceManager = new MaceManager(this, maceRepository, configManager, maceFactory);
            
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
                new EffectMaceListener(this, maceManager, configManager), this);
            getServer().getPluginManager().registerEvents(
                new ChaosMaceListener(this, maceManager, configManager, maceFactory), this);
            
            try {
                new MaceEffectTask(this, maceManager).runTaskTimer(this, 10L, 5L);
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to start MaceEffectTask", e);
            }
            
            removeVanillaRecipe();
            registerRecipes();
            
            getLogger().info("Mace-Exclusive has been enabled! Version: " + getDescription().getVersion());
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "CRITICAL ERROR: Failed to enable Mace-Exclusive!", t);
        }
    }

    @Override
    public void onDisable() {
        if (maceRepository != null) {
            maceRepository.save();
        }
    }

    private void removeVanillaRecipe() {
        Iterator<Recipe> it = getServer().recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (r.getResult().getType() == Material.MACE) {
                if (r instanceof ShapedRecipe sr) {
                    if (sr.getKey().getNamespace().equals("minecraft")) {
                        it.remove();
                        getLogger().info("Removed vanilla Mace recipe.");
                    }
                }
            }
        }
    }

    private void registerRecipes() {
        registerMaceRecipe(MaceType.POWER, "exclusive_mace_recipe", "mace.recipe");
        
        if (getConfig().getBoolean("mace-chaos.enabled", true)) {
            registerMaceRecipe(MaceType.CHAOS, "chaos_mace_recipe", "mace-chaos.recipe");
        }
    }

    private void registerMaceRecipe(MaceType type, String recipeKey, String configPath) {
        if (!getConfig().getBoolean(configPath.replace(".recipe", ".recipe.enabled"), true) 
            && type == MaceType.POWER) {
            return;
        }

        NamespacedKey key = new NamespacedKey(this, recipeKey);
        ItemStack result = maceFactory.createMace(type);
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        List<String> shape = getConfig().getStringList(configPath + ".shape");
        if (shape.size() != 3) {
            if (type == MaceType.POWER) {
                recipe.shape(" H ", " I ", " B ");
                recipe.setIngredient('H', Material.HEAVY_CORE);
                recipe.setIngredient('I', Material.NETHERITE_INGOT);
                recipe.setIngredient('B', Material.BREEZE_ROD);
            } else {
                recipe.shape("NHN", "HMH", "NWN");
                recipe.setIngredient('N', Material.NETHER_STAR); // Dark Ego
                recipe.setIngredient('H', Material.HEAVY_CORE);
                recipe.setIngredient('M', Material.MACE);
                recipe.setIngredient('W', Material.WITHER_ROSE);
            }
        } else {
            recipe.shape(shape.toArray(new String[0]));
            
            ConfigurationSection ingredients = getConfig().getConfigurationSection(configPath + ".ingredients");
            if (ingredients != null) {
                for (String k : ingredients.getKeys(false)) {
                    String matName = ingredients.getString(k);
                    Material mat = Material.matchMaterial(matName);
                    if (mat != null) {
                        recipe.setIngredient(k.charAt(0), mat);
                    } else {
                        getLogger().warning("Invalid ingredient: " + k + " -> " + matName);
                    }
                }
            }
        }
        
        getServer().addRecipe(recipe);
        getLogger().info("Registered " + type.name() + " Mace recipe: " + key);
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
