package net.auroric.apiary;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/**
 * One-shot migration helpers for servers transitioning from BeehivesPro 1.1.2.
 *
 * <p>The first responsibility is config import: BeehivesPro's {@code config.yml}
 * uses the same keys we do, so we copy it across when Apiary has no config yet.
 * Existing items keep working because we honour the original CustomModelData
 * identifier, and placed beehives keep working because we use the same marker
 * ArmorStand mechanism — no in-world rewrite is needed for v1.
 */
public final class Migration {

    private static final String LEGACY_FOLDER = "BeehivesPro";
    private static final String CONFIG_FILE = "config.yml";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private Migration() {}

    public enum Result {
        IMPORTED,
        NO_LEGACY_FOUND,
        ALREADY_PRESENT,
        FAILED
    }

    /**
     * Imports {@code plugins/BeehivesPro/config.yml} into Apiary's data folder.
     *
     * @param plugin owning plugin
     * @param force when true, overwrite an existing Apiary config (a timestamped
     *              backup of the displaced file is kept)
     */
    public static Result importLegacyConfig(Apiary plugin, boolean force) {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        File legacy = new File(pluginsDir, LEGACY_FOLDER + File.separator + CONFIG_FILE);
        File target = new File(plugin.getDataFolder(), CONFIG_FILE);

        if (!legacy.isFile()) {
            return Result.NO_LEGACY_FOUND;
        }

        if (target.isFile() && !force) {
            plugin.getLogger().info(
                    "BeehivesPro/config.yml detected but Apiary/config.yml already exists; not importing.");
            return Result.ALREADY_PRESENT;
        }

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for migration.");
            return Result.FAILED;
        }

        try {
            if (target.isFile()) {
                String stamp = LocalDateTime.now().format(STAMP);
                File backup = new File(plugin.getDataFolder(), CONFIG_FILE + ".pre-migrate-" + stamp);
                Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Backed up existing config to " + backup.getName());
            }
            Files.copy(legacy.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Migration copy failed: " + e.getMessage(), e);
            return Result.FAILED;
        }

        plugin.getLogger().info("Imported BeehivesPro config.yml into Apiary/config.yml.");
        plugin.getLogger().info("Existing in-world beehives and items will continue to work.");
        plugin.getLogger().info("Once you've verified everything looks correct, the old "
                + LEGACY_FOLDER + " folder can be deleted.");
        return Result.IMPORTED;
    }
}
