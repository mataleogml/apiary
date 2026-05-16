package net.auroric.apiary.listener;

import net.auroric.apiary.Apiary;
import net.auroric.apiary.beehive.Beehive;
import net.auroric.apiary.beehive.BeehiveManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Bee;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

public class BeehiveListener implements Listener {

    private final Apiary plugin;

    public BeehiveListener(Apiary plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack held = event.getItemInHand();
        if (!BeehiveManager.isApiaryBeehiveItem(held)) return;
        Beehive beehive = new Beehive(plugin, held);
        Location blockLoc = event.getBlock().getLocation();
        beehive.placeMarker(blockLoc);
        beehive.spawnBees(blockLoc, middlePoint(blockLoc, event.getPlayer().getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!BeehiveManager.isApiaryBeehiveBlock(loc)) return;
        event.setDropItems(false);
        String markerName = BeehiveManager.removeBeehiveMarker(loc);
        Beehive beehive = new Beehive(plugin, BeehiveManager.parseName(markerName), null,
                BeehiveManager.parseLevel(markerName));
        beehive.giveTo(event.getPlayer(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Location loc = event.getClickedBlock().getLocation();

        if (BeehiveManager.isApiaryBeehiveBlock(loc)) {
            event.setCancelled(true);
            handleApiaryInteract(event, player, loc);
            return;
        }

        if (event.getClickedBlock().getType() == Material.BEEHIVE
                && event.getItem() != null
                && BeehiveManager.isLevelToken(event.getItem())
                && plugin.getConfig().getBoolean("enable-vanilla-beehive-upgrade")) {
            handleVanillaUpgrade(event, player);
        }
    }

    private void handleApiaryInteract(PlayerInteractEvent event, Player player, Location loc) {
        org.bukkit.block.data.type.Beehive hiveData =
                (org.bukkit.block.data.type.Beehive) event.getClickedBlock().getBlockData();
        ItemStack item = event.getItem();

        if (item != null && BeehiveManager.isLevelToken(item)) {
            tryLevelUpBlock(event, player, loc, item);
            return;
        }

        if (hiveData.getHoneyLevel() != hiveData.getMaximumHoneyLevel()) {
            sendHoneyLevelMessage(player, loc, hiveData);
            return;
        }

        if (item == null) {
            sendHoneyLevelMessage(player, loc, hiveData);
            return;
        }

        Material itemType = item.getType();
        if (itemType == Material.SHEARS) {
            harvestHoneycombs(event, player, loc, hiveData);
        } else if (itemType == Material.GLASS_BOTTLE) {
            harvestBottles(event, player, loc, hiveData);
        } else if (itemType == BeehiveManager.getHoneyBlockHarvestToolMaterial()) {
            harvestHoneyBlocks(event, player, loc, hiveData);
        } else {
            sendHoneyLevelMessage(player, loc, hiveData);
        }
    }

    private void tryLevelUpBlock(PlayerInteractEvent event, Player player, Location loc, ItemStack token) {
        int level = BeehiveManager.getBlockLevel(loc);
        if (level >= BeehiveManager.getMaxBeehiveLevel()) {
            sendPrefixed(player, BeehiveManager.getMarkerName(loc)
                    + BeehiveManager.levelDelimiter()
                    + plugin.getConfig().getString("beehive-max-level-message"));
            return;
        }
        token.setAmount(token.getAmount() - 1);
        int newLevel = BeehiveManager.levelUpBlock(loc);
        sendPrefixed(player, BeehiveManager.getBlockName(loc)
                + BeehiveManager.levelDelimiter()
                + plugin.getConfig().getString("beehive-level-up-message")
                .replace("%NEW_LEVEL%", String.valueOf(newLevel)));
        playLevelUpFx(event, player, newLevel);
        runLevelUpCommands(player);
        if (newLevel == BeehiveManager.getMinimumHoneyBlockHarvestLevel()) {
            sendPrefixed(player, BeehiveManager.getMarkerName(loc)
                    + BeehiveManager.levelDelimiter()
                    + plugin.getConfig().getString("unlock-honey-block-harvest-message"));
        }
    }

    private void harvestHoneycombs(PlayerInteractEvent event, Player player, Location loc,
                                   org.bukkit.block.data.type.Beehive hiveData) {
        int level = BeehiveManager.getBlockLevel(loc);
        int amount = 3 * level;
        player.getInventory().addItem(new ItemStack(Material.HONEYCOMB, amount));
        sendPrefixed(player, (BeehiveManager.getMarkerName(loc)
                + BeehiveManager.levelDelimiter()
                + plugin.getConfig().getString("beehive-shear-message"))
                .replace("%AMOUNT%", String.valueOf(amount)));
        playHarvestFx(event, hiveData, level);
    }

    private void harvestBottles(PlayerInteractEvent event, Player player, Location loc,
                                org.bukkit.block.data.type.Beehive hiveData) {
        int level = BeehiveManager.getBlockLevel(loc);
        ItemStack item = event.getItem();
        int amount = Math.min(level, item.getAmount());
        item.setAmount(item.getAmount() - amount);
        player.getInventory().addItem(new ItemStack(Material.HONEY_BOTTLE, amount));
        sendPrefixed(player, (BeehiveManager.getMarkerName(loc)
                + BeehiveManager.levelDelimiter()
                + plugin.getConfig().getString("beehive-bottle-message"))
                .replace("%AMOUNT%", String.valueOf(amount)));
        playHarvestFx(event, hiveData, level);
    }

    private void harvestHoneyBlocks(PlayerInteractEvent event, Player player, Location loc,
                                    org.bukkit.block.data.type.Beehive hiveData) {
        if (!plugin.hasPermission(player, "harvest-honey-blocks")) {
            sendPrefixed(player, "&cYou don't have permission to harvest honey blocks");
            return;
        }
        int level = BeehiveManager.getBlockLevel(loc);
        int min = BeehiveManager.getMinimumHoneyBlockHarvestLevel();
        if (level < min) {
            sendPrefixed(player, "&cBeehives need to be at least level &e" + min
                    + " &cto harvest honey blocks!");
            return;
        }
        int amount = plugin.getConfig().getInt("harvest-honey-blocks-start-amount") + (level - min);
        player.getInventory().addItem(new ItemStack(Material.HONEY_BLOCK, amount));
        sendPrefixed(player, (BeehiveManager.getMarkerName(loc)
                + BeehiveManager.levelDelimiter()
                + plugin.getConfig().getString("harvest-honey-block-message"))
                .replace("%AMOUNT%", String.valueOf(amount)));
        player.playSound(player.getLocation(), Sound.BLOCK_HONEY_BLOCK_PLACE, 0.5f, 1.0f);
        playHarvestFx(event, hiveData, level);
    }

    private void sendHoneyLevelMessage(Player player, Location loc,
                                       org.bukkit.block.data.type.Beehive hiveData) {
        sendPrefixed(player, BeehiveManager.getMarkerName(loc)
                + BeehiveManager.levelDelimiter()
                + plugin.getConfig().getString("beehive-honey-level-message")
                .replace("%HONEY_LEVEL%", String.valueOf(hiveData.getHoneyLevel()))
                .replace("%MAX_HONEY_LEVEL%", String.valueOf(hiveData.getMaximumHoneyLevel())));
    }

    private void handleVanillaUpgrade(PlayerInteractEvent event, Player player) {
        String presetName = plugin.getConfig().getString("vanilla-beehive-upgrade-preset");
        List<String> presets = BeehiveManager.getPresetKeys(plugin);
        if (presets == null || !presets.contains(presetName)) return;

        ConfigurationSection presetSection = plugin.getConfig()
                .getConfigurationSection("beehive-presets." + presetName);
        if (presetSection == null) return;

        Location blockLoc = event.getClickedBlock().getLocation();
        Beehive beehive = new Beehive(plugin,
                ChatColor.translateAlternateColorCodes('&',
                        String.valueOf(presetSection.getString("beehive-name"))),
                presetSection.getStringList("bees"),
                presetSection.getInt("level"));
        beehive.placeMarker(blockLoc);
        if (plugin.getConfig().getBoolean("vanilla-beehive-spawns-bees-when-upgraded")) {
            beehive.spawnBees(blockLoc, middlePoint(blockLoc, player.getLocation()));
        }
        sendPrefixed(player, BeehiveManager.parseName(BeehiveManager.getMarkerName(blockLoc))
                + BeehiveManager.levelDelimiter()
                + plugin.getConfig().getString("beehive-level-up-message")
                .replace("%NEW_LEVEL%", String.valueOf(beehive.getLevel())));
        event.getItem().setAmount(event.getItem().getAmount() - 1);
        playLevelUpFx(event, player, beehive.getLevel());
        runLevelUpCommands(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (clicked == null || cursor == null) return;
        if (clicked.getType() != Material.BEEHIVE) return;
        if (!BeehiveManager.isApiaryBeehiveItem(clicked)) return;
        if (!BeehiveManager.isLevelToken(cursor)) return;

        event.setCancelled(true);
        String displayName = Objects.requireNonNull(clicked.getItemMeta()).getDisplayName();
        int level = BeehiveManager.parseLevel(displayName);
        Player player = (Player) event.getWhoClicked();

        if (level >= BeehiveManager.getMaxBeehiveLevel()) {
            sendPrefixed(player, BeehiveManager.parseName(displayName)
                    + BeehiveManager.levelDelimiterAndPrefix()
                    + level + BeehiveManager.levelDelimiter()
                    + "&cThis beehive is already max level!");
            return;
        }

        ItemStack leveled = BeehiveManager.levelUpItem(clicked);
        clicked.setAmount(clicked.getAmount() - 1);
        cursor.setAmount(cursor.getAmount() - 1);
        if (leveled == null || leveled.getItemMeta() == null) return;

        player.getInventory().addItem(leveled);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
        runLevelUpCommands(player);

        String leveledName = leveled.getItemMeta().getDisplayName();
        int newLevel = BeehiveManager.parseLevel(leveledName);
        sendPrefixed(player, BeehiveManager.parseName(leveledName)
                + BeehiveManager.levelDelimiter()
                + plugin.getConfig().getString("beehive-level-up-message")
                .replace("%NEW_LEVEL%", String.valueOf(newLevel)));

        if (plugin.getConfig().getBoolean("beehive-level-up-animation")) {
            int particles = Math.min(newLevel * 5, 100);
            for (int i = 0; i < particles; i++) {
                Objects.requireNonNull(player.getLocation().getWorld())
                        .spawnParticle(Particle.TOTEM_OF_UNDYING, player.getEyeLocation(), 1,
                                0.5, 0.5, 0.5, 0.0);
            }
        }

        if (newLevel == BeehiveManager.getMinimumHoneyBlockHarvestLevel()) {
            sendPrefixed(player, BeehiveManager.parseName(leveledName)
                    + BeehiveManager.levelDelimiter()
                    + plugin.getConfig().getString("unlock-honey-block-harvest-message"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRenameBee(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() != EntityType.BEE) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if (hand.getType() != Material.NAME_TAG) return;
        if (hand.getItemMeta() == null || !hand.getItemMeta().hasDisplayName()) return;

        Bee bee = (Bee) event.getRightClicked();
        if (!plugin.getConfig().getBoolean("enable-bee-renaming")) {
            if (bee.getCustomName() != null) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        String newName = ChatColor.translateAlternateColorCodes('&',
                hand.getItemMeta().getDisplayName());
        Location hive = bee.getHive();
        if (hive == null) {
            sendPrefixed(player, "&cThis bee currently doesn't have a hive");
        } else if (BeehiveManager.isApiaryBeehiveBlock(hive)) {
            newName += ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("bee-renamed-hive", "")
                            .replace("%BEEHIVE_NAME%",
                                    Objects.toString(BeehiveManager.getBlockName(hive), "")));
        } else {
            sendPrefixed(player, "&cThis bee's hive doesn't have a name");
        }
        bee.setCustomName(newName);
        bee.setCustomNameVisible(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        Location loc = event.getBlock().getLocation();
        if (BeehiveManager.getMarkerName(loc) != null) {
            BeehiveManager.removeBeehiveMarker(loc);
        }
    }

    // ---- FX helpers ----------------------------------------------------------

    private void playHarvestFx(PlayerInteractEvent event, org.bukkit.block.data.type.Beehive hive, int level) {
        if (event.getClickedBlock() == null || event.getClickedBlock().getLocation().getWorld() == null) return;
        if (plugin.getConfig().getBoolean("beehive-harvest-animation")) {
            int particles = Math.min(level * 5, 100);
            for (int i = 0; i < particles; i++) {
                event.getClickedBlock().getLocation().getWorld().spawnParticle(
                        Particle.FALLING_HONEY,
                        event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5),
                        1, 0.5, 0.5, 0.5, 0.0);
            }
        }
        hive.setHoneyLevel(0);
        event.getClickedBlock().setBlockData(hive);
    }

    private void playLevelUpFx(PlayerInteractEvent event, Player player, int newLevel) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
        if (event.getClickedBlock() == null) return;
        if (!plugin.getConfig().getBoolean("beehive-level-up-animation")) return;
        var world = event.getClickedBlock().getLocation().getWorld();
        if (world == null) return;
        int particles = Math.min(newLevel * 5, 100);
        for (int i = 0; i < particles; i++) {
            world.spawnParticle(Particle.TOTEM_OF_UNDYING,
                    event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5),
                    1, 0.5, 0.5, 0.5, 0.0);
        }
    }

    private void runLevelUpCommands(Player player) {
        for (String command : plugin.getConfig().getStringList("beehive-level-up-commands")) {
            if (command.isEmpty()) continue;
            String resolved = ChatColor.translateAlternateColorCodes('&',
                    command.replace("%PLAYER%", player.getName()));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
    }

    private void sendPrefixed(Player player, String message) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    private Location middlePoint(Location a, Location b) {
        return new Location(a.getWorld(),
                (a.getX() + b.getX()) / 2.0,
                (a.getY() + b.getY()) / 2.0,
                (a.getZ() + b.getZ()) / 2.0);
    }
}
