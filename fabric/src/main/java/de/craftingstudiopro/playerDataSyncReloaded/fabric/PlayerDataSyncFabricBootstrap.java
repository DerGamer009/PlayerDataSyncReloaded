package de.craftingstudiopro.playerDataSyncReloaded.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Entry point of the single Fabric jar. Each Minecraft line has its own adapter package, compiled
 * against that line's mappings; this class picks the one for the running game and starts it.
 * Adapters for other lines are never loaded, so their unresolvable references do not matter.
 */
public final class PlayerDataSyncFabricBootstrap implements ModInitializer {

    @Override
    public void onInitialize() {
        String minecraft = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("");
        String module = adapterFor(minecraft);
        if (module == null) {
            throw new IllegalStateException("PlayerDataSync has no Fabric adapter for Minecraft " + minecraft
                    + " (supported: 1.20.1, 1.21.4, 26.1.x, 26.2.x)");
        }

        String className = "de.craftingstudiopro.playerDataSyncReloaded.fabric." + module + ".PlayerDataSyncFabric";
        try {
            ((ModInitializer) Class.forName(className).getDeclaredConstructor().newInstance()).onInitialize();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("PlayerDataSync could not start adapter " + className + " for Minecraft " + minecraft, e);
        }
    }

    static String adapterFor(String minecraft) {
        return de.craftingstudiopro.playerDataSyncReloaded.common.util.Versions.fabricAdapterFor(minecraft);
    }
}
