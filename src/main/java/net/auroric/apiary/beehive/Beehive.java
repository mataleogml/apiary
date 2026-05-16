package net.auroric.apiary.beehive;

import net.auroric.apiary.Apiary;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Bee;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents an Apiary beehive instance — either reconstructed from a placed
 * block's ItemStack or built fresh from a preset.
 */
public class Beehive {

    private final Apiary plugin;
    private final String name;
    private final int level;
    private final List<String> beeNames;
    private final List<Bee> spawnedBees = new ArrayList<>();

    public Beehive(Apiary plugin, String name, List<String> beeNames, int level) {
        this.plugin = plugin;
        this.name = name;
        this.beeNames = beeNames;
        int max = BeehiveManager.getMaxBeehiveLevel();
        this.level = Math.min(level, max);
    }

    public Beehive(Apiary plugin, ItemStack stack) {
        this.plugin = plugin;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            this.name = null;
            this.level = 0;
            this.beeNames = new ArrayList<>();
            return;
        }
        this.name = BeehiveManager.parseName(meta.getDisplayName());
        this.level = BeehiveManager.parseLevel(meta.getDisplayName());
        this.beeNames = new ArrayList<>();
        if (meta.getLore() != null) {
            String beesLoreHeader = ChatColor.translateAlternateColorCodes(
                    '&', Objects.requireNonNull(plugin.getConfig().getString("beehive-item-bees-lore")));
            for (String line : meta.getLore()) {
                if (line.isEmpty() || line.equalsIgnoreCase(beesLoreHeader)) continue;
                this.beeNames.add(line);
            }
        }
    }

    public int getLevel() {
        return level;
    }

    public void spawnBees(Location hiveLocation, Location spawnLocation) {
        if (beeNames == null || spawnLocation.getWorld() == null) return;
        String format = plugin.getConfig().getString("bee-name-format", "%BEE_NAME%");
        for (String beeName : beeNames) {
            if (spawnedBees.size() >= 3) break;
            Bee bee = (Bee) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.BEE);
            spawnLocation.add(0.0, 0.61, 0.0);
            String displayName = ChatColor.translateAlternateColorCodes('&',
                    format.replace("%BEE_NAME%", beeName).replace("%BEEHIVE_NAME%", name));
            bee.setCustomName(displayName);
            if (!displayName.isEmpty()) {
                bee.setCustomNameVisible(true);
            }
            bee.setHive(hiveLocation);
            spawnedBees.add(bee);
        }
    }

    public ItemStack giveTo(Player player, int amount) {
        ItemStack stack = new ItemStack(Material.BEEHIVE, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name + BeehiveManager.levelDelimiterAndPrefix() + level);
            meta.setCustomModelData(BeehiveManager.BEEHIVE_MODEL_DATA);
            if (beeNames != null) {
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.translateAlternateColorCodes(
                        '&', String.valueOf(plugin.getConfig().getString("beehive-item-bees-lore"))));
                lore.add("");
                for (String bee : beeNames) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', bee));
                }
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        player.getInventory().addItem(stack);
        return stack;
    }

    public void placeMarker(Location location) {
        BeehiveManager.removeBeehiveMarker(location);
        if (location.getWorld() == null) return;
        Location offset = location.clone().add(0.5, 1.0, 0.5);
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(offset, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setSmall(true);
        stand.setInvulnerable(true);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setMarker(true);
        stand.setCustomName(name + BeehiveManager.levelDelimiterAndPrefix() + level);
        stand.setCustomNameVisible(true);
    }
}
