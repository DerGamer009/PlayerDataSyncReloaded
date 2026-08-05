package de.craftingstudiopro.playerDataSyncReloaded.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Flat key/value config for the platforms that have no config system of their own
 * (Fabric, Forge). Bukkit keeps using its own YAML config.
 *
 * <p>Keys are the same dotted paths the Bukkit config uses, e.g. {@code sync.inventory},
 * so {@link SyncManager} does not need to know which platform it is running on.
 * List values are comma-separated.
 */
public class FileConfig {
    private final Properties properties = new Properties();
    private final Logger logger;

    public FileConfig(Path file, Logger logger) {
        this.logger = logger;
        load(file);
    }

    private void load(Path file) {
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                writeDefaults(file);
                logger.info("Created default config at " + file.toAbsolutePath());
            }
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            }
        } catch (IOException e) {
            logger.warning("Could not read config at " + file + " (" + e.getMessage()
                    + "). Falling back to built-in defaults.");
        }
    }

    private void writeDefaults(Path file) throws IOException {
        try (InputStream defaults = FileConfig.class.getResourceAsStream("/playerdatasync.properties")) {
            if (defaults == null) {
                // No bundled template: an empty file still lets getters fall back to their defaults.
                Files.createFile(file);
                return;
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                defaults.transferTo(out);
            }
        }
    }

    public String getString(String path, String def) {
        String value = properties.getProperty(path);
        return value != null ? value : def;
    }

    public boolean getBoolean(String path, boolean def) {
        String value = properties.getProperty(path);
        if (value == null) {
            return def;
        }
        value = value.trim();
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        logger.warning("Config key '" + path + "' is not a boolean ('" + value + "'), using " + def);
        return def;
    }

    public List<String> getStringList(String path) {
        String value = properties.getProperty(path);
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (String part : Arrays.asList(value.split(","))) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
