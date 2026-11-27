package de.craftingstudiopro.playerDataSyncReloaded.v26_2;

import de.craftingstudiopro.playerDataSyncReloaded.common.BukkitBaseVersionHandler;

/**
 * Paper handler for this Minecraft line. Everything version-dependent (attributes, max health,
 * health scale) is resolved at runtime in {@link BukkitBaseVersionHandler}; before 26.12-Alpha.3 the
 * 1.21 handler referenced Attribute.MAX_HEALTH, which does not exist on 1.21 and 1.21.1.
 */
public class VersionHandlerImpl extends BukkitBaseVersionHandler {
}
