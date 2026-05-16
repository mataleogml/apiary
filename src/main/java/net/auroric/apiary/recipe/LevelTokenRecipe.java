package net.auroric.apiary.recipe;

import net.auroric.apiary.Apiary;
import net.auroric.apiary.ApiaryConfigException;
import net.auroric.apiary.beehive.BeehiveManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.HashSet;
import java.util.Set;

public final class LevelTokenRecipe {

    private static final String RECIPE_KEY = "apiary-level-token";
    private static final String RECIPE_PATH = "beehive-level-token-crafting-recipe.recipe";

    private LevelTokenRecipe() {}

    public static void registerShapeless(Apiary plugin) throws ApiaryConfigException {
        FileConfiguration config = plugin.getConfig();
        ItemStack result = BeehiveManager.getLevelToken(1);
        NamespacedKey key = new NamespacedKey(plugin, RECIPE_KEY);
        ShapelessRecipe recipe = new ShapelessRecipe(key, result);

        ConfigurationSection section = config.getConfigurationSection(RECIPE_PATH);
        if (section == null) return;

        int total = 0;
        for (String itemName : section.getKeys(false)) {
            Material material = Material.getMaterial(itemName);
            if (material == null) {
                throw new ApiaryConfigException(
                        "Invalid material in level-token recipe: " + itemName);
            }
            int amount = config.getInt(RECIPE_PATH + "." + itemName);
            if (amount < 1 || amount > 9) {
                throw new ApiaryConfigException(
                        "Invalid amount " + amount + " for '" + itemName + "' (must be 1-9)");
            }
            total += amount;
            if (total > 9) {
                throw new ApiaryConfigException(
                        "Shapeless recipe total exceeds 9 ingredients");
            }
            recipe.addIngredient(amount, material);
        }
        Bukkit.addRecipe(recipe);
    }

    public static void registerShaped(Apiary plugin) throws ApiaryConfigException {
        FileConfiguration config = plugin.getConfig();
        ItemStack result = BeehiveManager.getLevelToken(1);
        NamespacedKey key = new NamespacedKey(plugin, RECIPE_KEY);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("123", "456", "789");

        ConfigurationSection section = config.getConfigurationSection(RECIPE_PATH);
        if (section == null) return;

        Set<Integer> seen = new HashSet<>();
        for (String slot : section.getKeys(false)) {
            String materialName = config.getString(RECIPE_PATH + "." + slot);
            if (materialName == null) continue;
            Material material = Material.getMaterial(materialName);
            if (material == null) {
                throw new ApiaryConfigException(
                        "Invalid material in shaped recipe at slot " + slot + ": " + materialName);
            }
            int position;
            try {
                position = Integer.parseInt(slot);
            } catch (NumberFormatException e) {
                throw new ApiaryConfigException("Slot key '" + slot + "' is not a number 1-9");
            }
            if (position < 1 || position > 9) {
                throw new ApiaryConfigException(
                        "Slot " + position + " out of range (must be 1-9)");
            }
            if (!seen.add(position)) {
                throw new ApiaryConfigException("Duplicate slot " + position + " in shaped recipe");
            }
            recipe.setIngredient(Character.forDigit(position, 10), material);
        }
        Bukkit.addRecipe(recipe);
    }
}
