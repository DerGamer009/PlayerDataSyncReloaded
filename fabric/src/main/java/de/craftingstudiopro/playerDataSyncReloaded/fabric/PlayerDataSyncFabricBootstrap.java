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
                    + " (supported: 1.20.x, 1.21.x, 26.1, 26.2)");
        }

        String className = "de.craftingstudiopro.playerDataSyncReloaded.fabric." + module + ".PlayerDataSyncFabric";
        try {
            ((ModInitializer) Class.forName(className).getDeclaredConstructor().newInstance()).onInitialize();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("PlayerDataSync could not start adapter " + className + " for Minecraft " + minecraft, e);
        }
    }

    static String adapterFor(String minecraft) {
        if (minecraft.startsWith("26.2")) return "v26_2_R1";
        if (minecraft.startsWith("26.1")) return "v26_1_R1";
        if (minecraft.startsWith("1.21")) return "v1_21_R1";
        if (minecraft.startsWith("1.20")) return "v1_20_R1";
        return null;
    }
}
