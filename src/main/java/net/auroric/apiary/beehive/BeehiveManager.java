package net.auroric.apiary.beehive;

import net.auroric.apiary.Apiary;
import net.auroric.apiary.ApiaryConfigException;
import net.auroric.apiary.recipe.LevelTokenRecipe;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Static registry of plugin-wide beehive state plus helpers for reading and
 * mutating beehive identity (name + level) from both ItemStacks and placed
 * blocks. State on placed beehives lives in an invisible marker ArmorStand
 * floating just above the block.
 */
public final class BeehiveManager {

    public static final int BEEHIVE_MODEL_DATA = 555666555;

    private static final String NAME_LEVEL_DELIMITER = " " + ChatColor.WHITE + "- ";
    private static final String LEVEL_PREFIX = ChatColor.GRAY + "Lv." + ChatColor.YELLOW;

    private static ItemStack levelToken;
    private static int maxBeehiveLevel;
    private static int minimumHoneyBlockHarvestLevel;
    private static Material honeyBlockHarvestToolMaterial;

    private BeehiveManager() {}

    // ---- ItemStack identity ---------------------------------------------------

    public static boolean isApiaryBeehiveItem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.BEEHIVE) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == BEEHIVE_MODEL_DATA;
    }

    public static boolean isApiaryBeehiveBlock(Location location) {
        if (location.getBlock().getType() != Material.BEEHIVE) {
            removeBeehiveMarker(location);
            return false;
        }
        return findMarker(location) != null;
    }

    // ---- Marker (placed beehive state) ---------------------------------------

    public static String removeBeehiveMarker(Location location) {
        ArmorStand marker = findMarker(location);
        if (marker == null) return null;
        String name = marker.getCustomName();
        marker.remove();
        return name;
    }

    public static String getMarkerName(Location location) {
        ArmorStand marker = findMarker(location);
        return marker == null ? null : marker.getCustomName();
    }

    private static ArmorStand findMarker(Location location) {
        if (location.getWorld() == null) return null;
        Location centre = new Location(location.getWorld(),
                location.getX() + 0.5, location.getY() + 1.0, location.getZ() + 0.5);
        for (Entity entity : location.getWorld().getNearbyEntities(centre, 0.1, 0.1, 0.1)) {
            if (entity instanceof ArmorStand stand && stand.isMarker()) {
                return stand;
            }
        }
        return null;
    }

    // ---- Name / level encoding ------------------------------------------------
    //
    // The encoded form is: "<name>" + NAME_LEVEL_DELIMITER + LEVEL_PREFIX + "<n>"
    // where <name> may itself contain the delimiter (so we split-then-rejoin).

    public static int parseLevel(String encoded) {
        if (encoded == null) return 0;
        String[] parts = encoded.split(NAME_LEVEL_DELIMITER);
        String levelChunk = parts[parts.length - 1];
        if (!levelChunk.startsWith(LEVEL_PREFIX)) return 0;
        try {
            return Integer.parseInt(levelChunk.substring(LEVEL_PREFIX.length()));
        } catch (NumberFormatException e) {
            // Player-craftable string (item display name, marker custom name) —
            // never throw out of an event handler on malformed input.
            return 0;
        }
    }

    public static String parseName(String encoded) {
        if (encoded == null) return null;
        String[] parts = encoded.split(NAME_LEVEL_DELIMITER);
        if (parts.length == 2) return parts[0];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            sb.append(parts[i]).append(NAME_LEVEL_DELIMITER);
        }
        sb.delete(sb.lastIndexOf(NAME_LEVEL_DELIMITER), sb.length());
        return sb.toString();
    }

    public static int getBlockLevel(Location location) {
        return isApiaryBeehiveBlock(location) ? parseLevel(getMarkerName(location)) : 0;
    }

    public static String getBlockName(Location location) {
        return isApiaryBeehiveBlock(location) ? parseName(getMarkerName(location)) : null;
    }

    public static String levelDelimiterAndPrefix() {
        return NAME_LEVEL_DELIMITER + LEVEL_PREFIX;
    }

    public static String levelDelimiter() {
        return NAME_LEVEL_DELIMITER;
    }

    // ---- Level-up operations --------------------------------------------------

    public static int levelUpBlock(Location location) {
        if (!isApiaryBeehiveBlock(location)) return 0;
        int level = getBlockLevel(location);
        ArmorStand marker = findMarker(location);
        if (marker != null) {
            marker.setCustomName(getBlockName(location) + levelDelimiterAndPrefix() + (level + 1));
        }
        return level + 1;
    }

    public static ItemStack levelUpItem(ItemStack stack) {
        if (!isApiaryBeehiveItem(stack)) return null;
        ItemStack copy = stack.clone();
        copy.setAmount(1);
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            int level = parseLevel(meta.getDisplayName());
            String name = parseName(meta.getDisplayName());
            meta.setDisplayName(name + levelDelimiterAndPrefix() + (level + 1));
            copy.setItemMeta(meta);
        }
        return copy;
    }

    // ---- Presets --------------------------------------------------------------

    public static List<String> getPresetKeys(Apiary plugin) {
        if (!plugin.getConfig().contains("beehive-presets", true)) return null;
        var section = plugin.getConfig().getConfigurationSection("beehive-presets");
        return section == null ? null : new ArrayList<>(section.getKeys(false));
    }

    // ---- Level token ---------------------------------------------------------

    public static void initializeLevelToken(Apiary plugin, Material material) throws ApiaryConfigException {
        FileConfiguration config = plugin.getConfig();
        levelToken = new ItemStack(material, 1);
        ItemMeta meta = levelToken.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes(
                    '&', String.valueOf(config.getString("beehive-level-token-name"))));
            List<String> lore = config.getStringList("beehive-level-token-lore").stream()
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .toList();
            meta.setLore(lore);
            meta.addEnchant(Enchantment.INFINITY, 10, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            levelToken.setItemMeta(meta);
        }
        if (config.getBoolean("enable-beehive-level-tokens-crafting", false)) {
            if (config.getBoolean("beehive-level-token-crafting-recipe.shapeless-recipe", false)) {
                LevelTokenRecipe.registerShapeless(plugin);
            } else {
                LevelTokenRecipe.registerShaped(plugin);
            }
        }
    }

    public static void giveLevelTokens(Player player, int amount) {
        ItemStack give = levelToken.clone();
        give.setAmount(amount);
        player.getInventory().addItem(give);
    }

    public static boolean isLevelToken(ItemStack stack) {
        if (stack == null || levelToken == null) return false;
        ItemStack probe = levelToken.clone();
        probe.setAmount(stack.getAmount());
        return Objects.equals(probe, stack);
    }

    public static ItemStack getLevelToken(int amount) {
        ItemStack give = levelToken.clone();
        give.setAmount(amount);
        return give;
    }

    // ---- Accessors ------------------------------------------------------------

    public static int getMaxBeehiveLevel() { return maxBeehiveLevel; }
    public static void setMaxBeehiveLevel(int v) { maxBeehiveLevel = v; }
    public static int getMinimumHoneyBlockHarvestLevel() { return minimumHoneyBlockHarvestLevel; }
    public static void setMinimumHoneyBlockHarvestLevel(int v) { minimumHoneyBlockHarvestLevel = v; }
    public static Material getHoneyBlockHarvestToolMaterial() { return honeyBlockHarvestToolMaterial; }
    public static void setHoneyBlockHarvestToolMaterial(Material v) { honeyBlockHarvestToolMaterial = v; }
}
