package de.craftingstudiopro.playerDataSyncReloaded.api;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public class PlayerData implements Serializable {
    private static final long serialVersionUID = 1L;

    public UUID uuid;
    public String name;

    // Items
    public String inventoryContents; // Serialized NBT or Base64
    public String enderChestContents;
    public int selectedSlot;

    // Stats
    public double health;
    public double healthScale;
    public int foodLevel;
    public float saturation;
    public float exhaustion;

    // Experience
    public int level;
    public float exp;
    public int totalExperience;

    // Status
    public String gameMode;
    public boolean isFlying;
    public boolean canFly;

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

    // PDC
    public String persistentDataContainer;

    // Attributes
    public Map<String, Double> attributes;
}
