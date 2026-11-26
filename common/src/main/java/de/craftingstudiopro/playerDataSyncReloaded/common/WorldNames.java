package de.craftingstudiopro.playerDataSyncReloaded.common;

import java.util.Locale;

/**
 * Translates stored world names between platforms. Paper stores the Bukkit world name
 * ({@code world}, {@code world_nether}, {@code world_the_end}), Fabric the dimension id
 * ({@code minecraft:overworld}, ...). Both sides accept either form for the three vanilla dimensions.
 */
public final class WorldNames {
    public static final String OVERWORLD = "minecraft:overworld";
    public static final String NETHER = "minecraft:the_nether";
    public static final String END = "minecraft:the_end";

    private WorldNames() {
    }

    /** Dimension id for a stored world name, or {@code null} if it cannot be mapped to one. */
    public static String toDimensionId(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        String name = worldName.trim().toLowerCase(Locale.ROOT);
        if (name.contains(":")) {
            return name;
        }
        switch (name) {
            case "world":
            case "overworld":
                return OVERWORLD;
            case "world_nether":
            case "the_nether":
                return NETHER;
            case "world_the_end":
            case "the_end":
                return END;
            default:
                return null;
        }
    }

    /**
     * Bukkit environment name ({@code NORMAL}, {@code NETHER}, {@code THE_END}) for a vanilla dimension id,
     * or {@code null} for anything else.
     */
    public static String environmentFor(String worldName) {
        String id = toDimensionId(worldName);
        if (OVERWORLD.equals(id)) return "NORMAL";
        if (NETHER.equals(id)) return "NETHER";
        if (END.equals(id)) return "THE_END";
        return null;
    }
}
