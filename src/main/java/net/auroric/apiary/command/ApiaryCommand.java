package net.auroric.apiary.command;

import net.auroric.apiary.Apiary;
import net.auroric.apiary.Migration;
import net.auroric.apiary.beehive.Beehive;
import net.auroric.apiary.beehive.BeehiveManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ApiaryCommand implements CommandExecutor {

    private final Apiary plugin;

    public ApiaryCommand(Apiary plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (plugin.hasPermission(sender, "default")) sendHelp(sender);
            else sendNoPerm(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> {
                if (plugin.hasPermission(sender, "default")) sendHelp(sender);
                else sendNoPerm(sender);
            }
            case "reload" -> handleReload(sender);
            case "give" -> handleGive(sender, args);
            case "addtokens" -> handleAddTokens(sender, args);
            case "migrate" -> handleMigrate(sender, args);
            default -> sendUnknown(sender);
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!plugin.hasPermission(sender, "reload")) {
            sendNoPerm(sender);
            return;
        }
        plugin.reloadConfig();
        clearLevelTokenRecipe();
        String err = plugin.loadAndValidateConfig();
        if (err == null) {
            sender.sendMessage(coloured(Apiary.CHAT_PREFIX + "&aSuccessfully reloaded the plugin"));
        } else {
            sender.sendMessage(coloured(Apiary.CHAT_PREFIX + "&cReload error: &7" + err));
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!plugin.hasPermission(sender, "give")) {
            sendNoPerm(sender);
            return;
        }
        // /apiary give <player> <preset> <amount>
        if (args.length == 4) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sendHelp(sender);
                sender.sendMessage(coloured(Apiary.CHAT_PREFIX + "&cInvalid player: " + args[1]));
                return;
            }
            List<String> presets = BeehiveManager.getPresetKeys(plugin);
            if (presets == null || !presets.contains(args[2])) {
                sender.sendMessage(coloured(Apiary.CHAT_PREFIX + "&cInvalid preset: " + args[2]));
                if (presets == null) {
                    sender.sendMessage(coloured(Apiary.CHAT_PREFIX + "&cNo presets configured."));
                } else {
                    sender.sendMessage(coloured(Apiary.CHAT_PREFIX
                            + "&cAvailable presets: &a" + String.join(", ", presets)));
                }
                return;
            }
            ConfigurationSection presetSection = plugin.getConfig()
                    .getConfigurationSection("beehive-presets." + args[2]);
            if (presetSection == null) return;
            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(coloured(Apiary.CHAT_PREFIX + "&cInvalid amount: '" + args[3] + "'"));
                return;
            }
            Beehive beehive = new Beehive(plugin,
                    coloured(String.valueOf(presetSection.getString("beehive-name"))),
                    presetSection.getStringList("bees"),
                    presetSection.getInt("level"));
            beehive.giveTo(target, amount);
            return;
        }

        // /apiary give <player> <name> <level> <bee1,bee2,bee3> <amount>
        if (args.length == 6) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sendHelp(sender);
                sender.sendMessage(coloured(Apiary.CHAT_PREFIX + "&cInvalid player: " + args[1]));
                return;
            }
            int level, amount;
            try {
                level = Integer.parseInt(args[3]);
                amount = Integer.parseInt(args[5]);
            } catch (NumberFormatException e) {
                sendHelp(sender);
                sender.sendMessage(coloured(Apiary.CHAT_PREFIX
                        + "&cInvalid number — level: '" + args[3] + "', amount: '" + args[5] + "'"));
                return;
            }
            if (level > BeehiveManager.getMaxBeehiveLevel()) {
                sender.sendMessage(coloured(Apiary.CHAT_PREFIX
                        + "&cLevel " + level + " exceeds max " + BeehiveManager.getMaxBeehiveLevel()));
                return;
            }
            List<String> beeNames = Arrays.asList(args[4].split(","));
            if (beeNames.size() > 3) {
                sender.sendMessage(coloured(Apiary.CHAT_PREFIX
                        + "&cMaximum 3 bees per beehive (got " + beeNames.size() + ")"));
                return;
            }
            Beehive beehive = new Beehive(plugin, coloured(args[2]), beeNames, level);
            beehive.giveTo(target, amount);
            return;
        }

        sendHelp(sender);
        sender.sendMessage(coloured(Apiary.CHAT_PREFIX + "&cWrong number of arguments for 'give'"));
    }

    private void handleAddTokens(CommandSender sender, String[] args) {
        if (!plugin.hasPermission(sender, "addtokens")) {
            sendNoPerm(sender);
            return;
        }
        if (args.length != 3) {
            sendHelp(sender);
            sender.sendMessage(coloured(Apiary.CHAT_PREFIX
                    + "&cUsage: /apiary addtokens <player> <amount>"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendHelp(sender);
            sender.sendMessage(coloured(Apiary.CHAT_PREFIX + "&cInvalid player: " + args[1]));
            return;
        }
        try {
            BeehiveManager.giveLevelTokens(target, Integer.parseInt(args[2]));
        } catch (NumberFormatException e) {
            sendHelp(sender);
            sender.sendMessage(coloured(Apiary.CHAT_PREFIX + "&cInvalid amount: '" + args[2] + "'"));
        }
    }

    private void handleMigrate(CommandSender sender, String[] args) {
        if (!plugin.hasPermission(sender, "migrate")) {
            sendNoPerm(sender);
            return;
        }
        boolean force = args.length >= 2 && args[1].equalsIgnoreCase("--force");
        Migration.Result result = Migration.importLegacyConfig(plugin, force);
        switch (result) {
            case IMPORTED -> {
                sender.sendMessage(coloured(Apiary.CHAT_PREFIX
                        + "&aImported BeehivesPro/config.yml. Run &e/apiary reload &ato apply."));
            }
            case NO_LEGACY_FOUND -> sender.sendMessage(coloured(Apiary.CHAT_PREFIX
                    + "&cNo plugins/BeehivesPro/config.yml found — nothing to import."));
            case ALREADY_PRESENT -> sender.sendMessage(coloured(Apiary.CHAT_PREFIX
                    + "&eApiary already has a config.yml. Use &7/apiary migrate --force&e to overwrite (a backup will be kept)."));
            case FAILED -> sender.sendMessage(coloured(Apiary.CHAT_PREFIX
                    + "&cMigration failed — see console for details."));
        }
    }

    private void clearLevelTokenRecipe() {
        if (!plugin.getConfig().getBoolean("enable-beehive-level-tokens-crafting")) return;
        NamespacedKey key = new NamespacedKey(plugin, "apiary-level-token");
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if ((r instanceof ShapedRecipe sr && sr.getKey().equals(key))
                    || (r instanceof ShapelessRecipe sl && sl.getKey().equals(key))) {
                it.remove();
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        String divider = "&6&m" + "-".repeat(20) + "&r&6 Apiary &m" + "-".repeat(20);
        sender.sendMessage(coloured(divider));
        sender.sendMessage(coloured("&f/apiary help &2- &7Show this menu"));
        sender.sendMessage(coloured("&f/apiary reload &2- &7Reload configuration"));
        sender.sendMessage(coloured("&f/apiary give <player> <name> <level> <bee1,bee2,bee3> <amount>"));
        sender.sendMessage(coloured("    &2- &7Give a custom-built beehive"));
        sender.sendMessage(coloured("&f/apiary give <player> <preset> <amount>"));
        sender.sendMessage(coloured("    &2- &7Give a preset beehive from config"));
        sender.sendMessage(coloured("&f/apiary addtokens <player> <amount>"));
        sender.sendMessage(coloured("    &2- &7Give level-up tokens"));
        sender.sendMessage(coloured("&f/apiary migrate [--force] &2- &7Import config from BeehivesPro"));
        var tool = BeehiveManager.getHoneyBlockHarvestToolMaterial();
        if (tool != null) {
            sender.sendMessage(coloured("&fHoney-block harvest tool &2= &7" + tool));
        }
        sender.sendMessage(coloured(divider));
    }

    private void sendNoPerm(CommandSender sender) {
        sender.sendMessage(coloured(Apiary.CHAT_PREFIX + "&cYou don't have permission for that"));
    }

    private void sendUnknown(CommandSender sender) {
        if (plugin.hasPermission(sender, "default")) {
            sendHelp(sender);
            sender.sendMessage(coloured(Apiary.CHAT_PREFIX + "&cUnknown subcommand"));
        } else {
            sendNoPerm(sender);
        }
    }

    private static String coloured(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
