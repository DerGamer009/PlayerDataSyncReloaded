package de.craftingstudiopro.playerDataSyncReloaded.api;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerData implements Serializable {
    private static final long serialVersionUID = 1L;

    public UUID uuid;
    public String name;
    /** When this record was saved, epoch millis. 0 in records from versions before 26.9-Release. */
    public long savedAt;
    /** {@code server_name} of the server that saved this record. Null in records from versions before 26.12. */
    public String savedBy;
    /**
     * Sync keys (the part after {@code sync.}, e.g. {@code game_mode}) that the saving server had turned off.
     * The stored values for these keys are placeholders and must not be applied. Null in records before 26.12-Alpha.2.
     */
    public Set<String> notSynced;

    /** Whether the value behind this sync key may be applied. */
    public boolean isSynced(String key) {
        return notSynced == null || !notSynced.contains(key);
    }

    public void markNotSynced(String key) {
        if (notSynced == null) {
            notSynced = new HashSet<>();
        }
        notSynced.add(key);
    }
    public int arrowsInBody;

    // Items
    public String inventoryContents; // Serialized NBT or Base64
    public String enderChestContents;
    public int selectedSlot;

    // Stats
    public double health;
    public double healthScale;
    public double absorptionAmount;
    public int foodLevel;
    public float saturation;
    public float exhaustion;
    public int airLevel;
    public int fireTicks;
    public int freezeTicks;

    // Experience
    public int level;
    public float exp;
    public int totalExperience;

    // Status
    public String gameMode;
    public boolean isFlying;
    public boolean canFly;
    public float walkSpeed;
    public float flySpeed;
    public float fallDistance;

    // Potion Effects (Serialized)
    public String potionEffects;

    // Advancements/Statistics
    public String statistics;
    public String advancements;
    public double balance;

    // Location
    public String worldName;
    public double x, y, z;
    public float yaw, pitch;
    public long playerTime = -1;
    public String playerWeather;

    // PDC
    public String persistentDataContainer;

    // Attributes
    public Map<String, Double> attributes;

    // Extra Data for third-party hooks
    public Map<String, String> extraData = new java.util.HashMap<>();
}
