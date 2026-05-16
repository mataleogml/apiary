package net.auroric.apiary;

import net.auroric.apiary.beehive.BeehiveManager;
import net.auroric.apiary.command.ApiaryCommand;
import net.auroric.apiary.listener.BeehiveListener;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class Apiary extends JavaPlugin {

    public static final String CHAT_PREFIX =
            ChatColor.GOLD + "[" + ChatColor.YELLOW + "Apiary" + ChatColor.GOLD + "] ";

    @Override
    public void onEnable() {
        printBanner();

        // Auto-import legacy BeehivesPro config on first run, before saveDefaultConfig
        // would otherwise create a fresh default. This is silent if there's no legacy
        // config to import, or if our own config already exists.
        File ownConfig = new File(getDataFolder(), "config.yml");
        if (!ownConfig.exists()) {
            Migration.importLegacyConfig(this, false);
        }
        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(new BeehiveListener(this), this);

        ApiaryCommand executor = new ApiaryCommand(this);
        PluginCommand main = getCommand("apiary");
        if (main != null) main.setExecutor(executor);

        PluginCommand legacy = getCommand("beehivespro");
        if (legacy != null) {
            if (getConfig().getBoolean("legacy-compatibility.command-aliases", true)) {
                legacy.setExecutor(executor);
            } else {
                legacy.setExecutor(legacyDisabledExecutor());
            }
        }

        String error = loadAndValidateConfig();
        if (error != null) {
            getLogger().severe("Configuration error: " + error);
        }
    }

    /**
     * Centralised permission check. Always recognises the native {@code apiary.}
     * namespace. When {@code legacy-compatibility.permissions} is true (the
     * default for migrated servers), also recognises the legacy
     * {@code beehivespro.} namespace, so existing LuckPerms grants keep working.
     * The {@code apiary.admin} or legacy {@code beehivespro.admin} node grants
     * access to every check.
     */
    public boolean hasPermission(Permissible target, String suffix) {
        if (target.hasPermission("apiary." + suffix)) return true;
        if (target.hasPermission("apiary.admin")) return true;
        if (getConfig().getBoolean("legacy-compatibility.permissions", true)) {
            if (target.hasPermission("beehivespro." + suffix)) return true;
            if (target.hasPermission("beehivespro.admin")) return true;
        }
        return false;
    }

    /**
     * Validates the loaded configuration and pushes values into BeehiveManager.
     * Returns null on success, or an error message string if validation failed.
     */
    public String loadAndValidateConfig() {
        FileConfiguration config = getConfig();
        try {
            int maxLevel = config.getInt("beehive-max-level");
            if (maxLevel <= 0) {
                throw new ApiaryConfigException("beehive-max-level must be > 0 (was " + maxLevel + ")");
            }
            BeehiveManager.setMaxBeehiveLevel(maxLevel);

            if (config.getBoolean("harvest-honey-blocks")) {
                int minLevel = config.getInt("harvest-honey-blocks-minimum-level");
                if (minLevel > maxLevel) {
                    throw new ApiaryConfigException(
                            "harvest-honey-blocks-minimum-level (" + minLevel + ") cannot exceed beehive-max-level (" + maxLevel + ")");
                }
                BeehiveManager.setMinimumHoneyBlockHarvestLevel(minLevel);

                String toolName = String.valueOf(config.getString("harvest-honey-blocks-tool"));
                Material tool = Material.getMaterial(toolName);
                if (tool == null) {
                    throw new ApiaryConfigException("Invalid harvest-honey-blocks-tool material: '" + toolName + "'");
                }
                BeehiveManager.setHoneyBlockHarvestToolMaterial(tool);
            }

            ConfigurationSection presets = config.getConfigurationSection("beehive-presets");
            if (presets != null) {
                for (String key : presets.getKeys(false)) {
                    int beeCount = presets.getStringList(key + ".bees").size();
                    if (beeCount > 3) {
                        throw new ApiaryConfigException(
                                "Preset '" + key + "' has " + beeCount + " bees (maximum is 3)");
                    }
                }
            }

            String tokenItemName = String.valueOf(config.getString("beehive-level-token-item"));
            Material tokenMaterial = Material.getMaterial(tokenItemName);
            if (tokenMaterial == null) {
                throw new ApiaryConfigException("Invalid beehive-level-token-item material: '" + tokenItemName + "'");
            }
            BeehiveManager.initializeLevelToken(this, tokenMaterial);

            return null;
        } catch (ApiaryConfigException e) {
            getServer().getConsoleSender().sendMessage(ChatColor.RED + "[Apiary] " + e.getMessage());
            return e.getMessage();
        }
    }

    private CommandExecutor legacyDisabledExecutor() {
        return (CommandSender sender, Command cmd, String label, String[] args) -> {
            sender.sendMessage(CHAT_PREFIX + ChatColor.GRAY
                    + "Legacy command aliases are disabled. Use " + ChatColor.YELLOW + "/apiary"
                    + ChatColor.GRAY + " instead.");
            return true;
        };
    }

    private void printBanner() {
        var console = getServer().getConsoleSender();
        console.sendMessage(ChatColor.GOLD + "Apiary " + ChatColor.DARK_GRAY + "v" + getPluginMeta().getVersion());
        console.sendMessage(ChatColor.YELLOW + "  Tiered beehives loaded.");
    }
}
