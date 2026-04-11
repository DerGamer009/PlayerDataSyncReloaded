package de.craftingstudiopro.playerDataSyncReloaded.common;

import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Utility to convert data from the OLD version of PlayerDataSync to the NEW format.
 */
public class LegacyMigrator {
    private final Logger logger;

    public LegacyMigrator(Logger logger) {
        this.logger = logger;
    }

    /**
     * Maps a SQL ResultSet (representing one row from the old player_data table) to PlayerData.
     */
    public PlayerData mapSql(java.sql.ResultSet rs) throws java.sql.SQLException {
        PlayerData data = new PlayerData();
        data.uuid = UUID.fromString(rs.getString("uuid"));
        data.name = rs.getString("uuid"); // Old version didn't always store names prominently or differently
        
        data.worldName = rs.getString("world");
        data.x = rs.getDouble("x");
        data.y = rs.getDouble("y");
        data.z = rs.getDouble("z");
        data.yaw = rs.getFloat("yaw");
        data.pitch = rs.getFloat("pitch");
        
        data.totalExperience = rs.getInt("xp");
        // Recalculate level/exp from total XP if needed, or let Bukkit handle it during apply
        
        data.gameMode = rs.getString("gamemode");
        data.health = rs.getDouble("health");
        data.foodLevel = rs.getInt("hunger");
        data.saturation = rs.getFloat("saturation");
        
        data.inventoryContents = rs.getString("inventory");
        // Note: Old version had armor/offhand separate. 
        // Simple migration might lose them if we don't merge them.
        // However, if we just want a 1:1, we'd need a more complex merger.
        
        data.enderChestContents = rs.getString("enderchest");
        data.potionEffects = rs.getString("effects");
        data.statistics = rs.getString("statistics");
        data.attributes = new java.util.HashMap<>(); // Old attributes were a string, new is a Map. Needs conversion.
        data.advancements = rs.getString("advancements");
        data.balance = rs.getDouble("economy");
        
        return data;
    }

    /**
     * Maps a MongoDB Document (from old collection) to PlayerData.
     */
    public PlayerData mapMongo(org.bson.Document doc) {
        PlayerData data = new PlayerData();
        data.uuid = UUID.fromString(doc.getString("uuid"));
        
        data.worldName = doc.getString("world");
        data.x = doc.getDouble("x");
        data.y = doc.getDouble("y");
        data.z = doc.getDouble("z");
        data.yaw = doc.getDouble("yaw").floatValue();
        data.pitch = doc.getDouble("pitch").floatValue();
        
        data.totalExperience = doc.getInteger("xp");
        data.gameMode = doc.getString("gamemode");
        data.health = doc.getDouble("health");
        data.foodLevel = doc.getInteger("hunger");
        data.saturation = doc.getDouble("saturation").floatValue();
        
        data.inventoryContents = doc.getString("inventory");
        data.enderChestContents = doc.getString("enderchest");
        data.potionEffects = doc.getString("effects");
        data.statistics = doc.getString("statistics");
        data.advancements = doc.getString("advancements");
        data.balance = doc.getDouble("economy");
        
        return data;
    }
}
