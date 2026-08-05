# Changelog

All notable changes to PlayerDataSyncReloaded will be documented in this file.

## [26.8-ALPHA] - 2026-07-31
### Fixed
- **Dead config toggles**: `sync.attributes`, `sync.pdc` and `sync.flight` were declared in `config.yml` but never evaluated in `SyncManager#filterData`, so switching them off had no effect. Same bug class as the `sync.economy` fix in 26.7.
- **Item exclusions on Fabric**: `exclusions.items` was stored by `FabricVersionHandler` and never read, so excluded items synced anyway. Both the namespaced ID (`minecraft:diamond`) and the Bukkit-style name (`DIAMOND`) are accepted.

### Added
- **Location sync**: Player position was captured and written to storage but never restored — no teleport existed anywhere in the codebase. Joining players are now teleported to their stored position. Guarded by `sync.location`, which **defaults to `false`**: it only works when the destination server has a world of the same name, and enabling it by default would have started teleporting players on upgrade. When the world is missing, the restore is skipped with a warning instead of dropping the player somewhere wrong.
- **Redis live sync**: The subscribe handler parsed the incoming UUID and then did nothing — publishing worked, but no server ever acted on it. Receiving servers now reload that player's data if they are online locally. Messages carry a per-server node ID (`saved:<uuid>:<nodeId>`) so a server ignores its own publishes rather than reloading over the save it just made.
- **Config support for Fabric**: `FabricPlatform` returned hardcoded defaults for every lookup, so no `sync.*` toggle worked on Fabric at all. A `playerdatasync.properties` file is now created in the Fabric config directory on first start, using the same dotted keys as the Bukkit config. Storage settings (`storage.type`, `storage.host`, …) are read from it as well, instead of the previously hardcoded SQLite database.
- **Fabric data coverage (MC 26.x)**: Ender chest, potion effects and attributes are now synchronized alongside inventory, health, food, experience and game mode.
- **`Platform#getPlayer(UUID)`**: Resolves an online player, which the Redis listener needs since it only receives a UUID.

### Known issues
- **Fabric is not yet at full parity with Bukkit.** Advancements and statistics are still not synchronized on any Fabric version. The 1.20 and 1.21 modules also still lack ender chest, potion effects and attributes — only the 26.x modules gained those. Planned for 26.8-BETA.
- Forge remains excluded from the build (ForgeGradle 6.0.x does not support Gradle 9+); the `Platform#getPlayer` implementations there are written but not compile-verified.

### Notes
- **This is an alpha.** The economy fix from 26.7, the new location teleport and the Redis live sync have all been verified to compile and are code-reviewed, but none of them have been exercised against a running server with Vault, Redis or a multi-server setup. Test on a staging server before deploying to production.

## [26.7-Release] - 2026-07-31
### Fixed
- **Economy sync (Vault)**: Balances were never synchronized. `SyncManager#setEconomy` discarded the Vault provider into an unused field, and `PlayerData.balance` was neither captured on quit nor applied on join — the `sync.economy` config toggle had no effect. `SyncManager` now keeps the provider and reads/writes balances through it (via reflection, so `common/` stays free of Bukkit and Vault types). Applying a balance is delta-based: it deposits or withdraws only the difference. `sync.economy: false` now genuinely disables both directions.

### Added
- **Paper 26.2**: New `v26_2_R1` handler built against `paper-api 26.2`; `setupVersionHandler` recognizes `26.2` servers.
- **Fabric / Forge 26.2 modules**: `fabric-versions/v26_2_R1` and `forge-versions/v26_2_R1` added alongside the Paper line.

### Changed
- **Paper 26.1.2**: Bumped to `26.1.2.build.72-stable`.
- **Gradle 9.6.0**: Required by Fabric Loom 1.15+, which the MC 26.x modules need.
- **Fabric Loom**: `1.15.5` for the 1.20/1.21 lines, `1.17.12` for the 26.x lines.
- **Fabric mappings for MC 26.x**: Mojang no longer publishes official mappings for 26.x, and no Yarn build targets it. The 26.x modules therefore map against `yarn 1.21.11+build.6`. Fabric aligned that Yarn release with Mojang's names, so the 26.x adapters use `ServerPlayer`, `CompoundTag`, `StreamCodec`, `Identifier.fromNamespaceAndPath(...)` and `ResourceKey#identifier()` — not the older `ServerPlayerEntity` / `NbtCompound` / `PacketCodec` names still used by the 1.20 and 1.21 modules.
- **Loom source remapping and decompilation disabled for 26.x** (`fabric.loom.ci=true`, empty `decompilers {}`): the cross-version mapping breaks both, and neither is needed to produce the mod jar.

### Known issues
- **Forge is not built in this release.** ForgeGradle 6.0.x refuses to run on Gradle 9+ and no compatible release exists yet, so `forge-versions/*` are excluded from the build. The modules remain in the tree and can be re-enabled with `-Ppds.enableForge=true` once ForgeGradle supports Gradle 9.

### Notes
- Building the MC 26.x modules requires a **JDK 25** Gradle daemon; `gradle/gradle-daemon-jvm.properties` provisions it automatically.
- Loom prints `The mappings (net.fabricmc:yarn:1.21.11+build.6) were not built for Minecraft version 26.1` during configuration. This warning is expected for the 26.x lines and does not indicate a broken build.

## [26.6-Release] - 2026-06-14
### Changed
- Published the unified release artifact as `PlayerDataSyncReloaded-26.6-Release.jar`.
- Updated CI and documentation to use the canonical release version and artifact name.
- Reposilite publishing credentials can now be supplied through `PDS_REPOSILITE_USERNAME` and `PDS_REPOSILITE_PASSWORD`.
- Incoming Bukkit plugin messages are decoded explicitly as UTF-8.

### Security
- Removed committed Reposilite credentials from `gradle.properties`.

## [26.5.5.1-ALPHA] - 2026-05-20
### Added
- **Fabric**: `fabric-api` (`0.92.2+1.20.1`) so lifecycle and server-play networking APIs resolve at compile time; `fabric.mod.json` declares a `fabric-api` dependency.
- **Velocity**: Gradle task `generatePluginBuildInfo` emits `PluginBuildInfo.VERSION` so the `@Plugin` version always matches the Gradle project version.
- **Modrinth / Fabric**: Guide [`pds-docs/modrinth-upload.md`](pds-docs/modrinth-upload.md) (Paper vs Fabric JAR); Gradle task **`checkFabricModMetadata`** asserts `fabric.mod.json` exists in the remapped Fabric JAR; Fabric archive base name **`playerdatasync-fabric`** so it is not confused with the Paper plugin artifact.
- **Unified distribution JAR**: The Paper **`PlayerDataSyncReloaded-*.jar`** (also copied to **`build/libs/`**) embeds **`bundled/playerdatasync-velocity.jar`**, **`bundled/playerdatasync-fabric.jar`**, and **`bundled/playerdatasync-forge.jar`** plus **`bundled/README.txt`** — one download; extract sibling JARs for Velocity/Fabric/Forge (classes are not merged to avoid mapping clashes).

### Changed
- **Gradle / bytecode targets**: `api` and `common` use **Java 17** (Fabric/Forge); **`plugin`** and **Velocity** use **Java 21** so **Paper 1.21.x** can remap the shaded plugin (ASM does not accept class file **69**). Nested **`:versions:*`** modules are set to **21** in **`versions/build.gradle.kts`** (they are not root `subprojects`, so they previously picked up the toolchain default and emitted **69**).
- **Velocity**: `velocity-api` updated to **3.5.0-SNAPSHOT**; Velocity→backend plugin messages use **UTF-8** (`StandardCharsets.UTF_8`); `ServerConnectedEvent` log line uses **`event.getServer().getServerInfo()`** (Velocity 3 API).
- **Shadow plugin**: Replaced `com.github.johnrengelman.shadow` **8.1.1** with **`com.gradleup.shadow` 8.3.10** so `shadowJar` can process modern class files when relocating (e.g. bundled jars).
- **Forge**: `META-INF/mods.toml` version is expanded from Gradle (`mod_version`); Forge `build.gradle.kts` no longer overrides root `version` / `group`.
- **Fabric**: `build.gradle.kts` no longer overrides root `version` / `group` (single version from `gradle.properties`).
- **Gradle runtime**: **Configuration cache disabled** in `gradle.properties` (ForgeGradle, Fabric Loom, and Shadow-related flows are not reliable with CC enabled); parallel and build caching remain enabled.
- **CI**: Workflow runs **`./gradlew :plugin:build`** and uploads the unified **`build/libs/PlayerDataSyncReloaded-*.jar`** (includes **`bundled/`** platform JARs).

### Fixed
- **Bukkit version handlers**: `v1_20_R1` and `v26_1_R1` `VersionHandlerImpl` now extend **`BukkitBaseVersionHandler`** and implement **`capture` / `apply` with `PDSPlayer`** (restores compatibility after removal of `BaseVersionHandler`).
- **Fabric** `PlayerDataSyncFabric`: `ServerPlayNetworking` global receiver is registered in **`SERVER_STARTING`** after storage/`SyncManager` setup; SLF4J error logging for storage init; guards when `syncManager` is not ready yet.
- **Forge** `PlayerDataSyncForge`: storage init failures use **Log4j** instead of `printStackTrace`; packet and login handlers guard **`syncManager == null`**.
- **Multi-platform build**: Resolved Gradle dependency variant mismatches (Fabric vs `api`/`common` JVM level). **`v26_1_R1`**: `compileClasspath` requests JVM **25** so Paper 26 `paper-api` resolves while sources still compile with **`--release 21`**.

### Notes
- **One JAR download**: `build/libs/PlayerDataSyncReloaded-*.jar` contains Paper + **`bundled/`** Velocity/Fabric/Forge JARs; Modrinth still needs the **extracted** platform files per loader (see `pds-docs/modrinth-upload.md`).
- **Modrinth**: Use **`bundled/playerdatasync-fabric.jar`** (extracted from the distribution JAR) for the Fabric loader file — not the outer archive root (that is the Paper plugin with **`plugin.yml`** only).
- **Paper 1.21.x**: The main plugin classes are built as **Java 21 bytecode** so the server’s **PluginRemapper** can read them. Run the server on **Java 21+** as required by Paper 1.21. **Deploy the JAR whose name matches `gradle.properties` `version`** (e.g. **`PlayerDataSyncReloaded-26.5.5.1-ALPHA.jar`**); delete any older file such as **`PlayerDataSyncReloaded-26.5.5-SNAPSHOT.jar`** from `plugins/` so Paper does not remap a stale build. The shaded jar **excludes `META-INF/versions/**`** so multi-release dependency layers (class file 69) are not shipped to the remapper.

## [26.5.5-ALPHA] - 2026-05-05
### Added
- **Initial Development**: Preparing for new features in the upcoming 26.5.5 release.

## [26.5.4-Release] - 2026-05-05
### Fixed
- **Build Process**: Redirected final shaded JAR output to the root build directory for easier access.
- **Database Migration**: Added automatic schema migration for SQL storage to fix "Unknown column" errors when upgrading from older versions.
- **Legacy Fallback**: Implemented automatic fallback to the legacy data format if the new JSON format is missing in SQL storage.

## [26.5.3-Release] - 2026-05-05
### Changed
- **Paper Compatibility**: Updated NMS support for the stable Paper 26.1.2 release.
- **Dependency Management**: Updated internal version modules to target the latest stable API builds.

## [26.5.1-Release] - 2026-05-01
### Fixed
- **Thread Safety**: Resolved `IllegalStateException` on Paper/Purpur servers where `PlayerDataSaveEvent` was incorrectly triggered on the main thread while marked as an asynchronous event. Both Save and Load events are now synchronous to ensure full compatibility with the Bukkit threading model.

## [26.5-Release] - 2026-04-27
### Added
- **API Extensibility**: Added `extraData` map to `PlayerData` for third-party plugin data synchronization.
- **Save Cancellation**: `PlayerDataSaveEvent` now implements `Cancellable`, allowing plugins to prevent data from being saved under specific conditions.
- **Granular Sync Controls**: Added new configuration options to disable syncing for Potion Effects, Food, GameMode, Advancements, and Statistics.
- **Improved Version Detection**: Better handling for Minecraft 1.21.1 and future sub-versions.

### Changed
- **Banner Update**: Refreshed the startup banner with new colors and "Expansion Update" subtitle.
- **Performance**: Minor internal optimizations for event handling.

### Fixed
- Potential edge case where data might save during an invalid state.


## [26.4-Release] - 2026-04-18
### Added
- **Storage Migrator**: Full release of the migration tool for seamless transitions between SQL and NoSQL.
- **Zipped Backup System**: Reliable export/import system for disaster recovery.
- **Legacy Migration Support**: Bridge for users coming from the original PlayerDataSync version.
- **Vault Economy Sync**: Stable cross-network balance synchronization.
- **Advanced Sync Features**: Full support for PDC, Attributes, Statistics, and Advancements.
- **Auto-Save System**: Background saving task to prevent data loss.
- **Exclusion System**: World and item blacklists for granular control.

### Changed
- **Modernized Version Support**: Dropped legacy support. Now exclusively supporting **1.20, 1.21, and 26.1+**.
- **Performance Optimizations**: GZIP compression and dedicated thread pools are now enabled by default.
- **Inventory Hashing**: Intelligent skip mechanics for unchanged data.

### Fixed
- All issues discovered during the Beta phase, including NMS fallback and thread safety.


## [26.4-BETA] - 2026-04-12
### Added
- **Storage Migrator**: Added a powerful tool to move data between any supported database backend (MySQL, MariaDB, PostgreSQL, MongoDB).
- **Zipped Backup System**: Added `/pds backup export/import` for portable data management and safety.
- **Legacy Migration Support**: Added specialized support to migrate data from the old PlayerDataSync version to the new Reloaded format.
- **Vault Economy Sync**: Full synchronization for player balances across the network.
- **Advanced Sync Features**: Added support for Persistent Data Containers (PDC), modern Attributes, Statistics, and Advancements.
- **Auto-Save System**: Automated background saving of all online players to prevent data loss on server crashes.
- **Exclusion System**: Added blacklists for specific worlds and items (by material) to prevent them from being synchronized.
- **Management Commands**: Added `/pds reload` for hot-reloading connections and `/pds migrate` for data transfers.
- **Real-time Feedback**: Configurable chat messages for players during synchronization events.
- **Debug Mode**: Detailed logging for easier troubleshooting in complex environments.

### Changed
- **Modernized Version Support**: Dropped legacy support for Minecraft 1.8 through 1.19. Now exclusively supporting **1.20, 1.21, and 26.1+**.
- **Massive Performance Boost**:
    - Integrated **GZIP Compression** for serialized data, reducing storage size and network load by up to 90%.
    - Introduced **Dedicated Thread Pools** for all database I/O to ensure the main server thread is NEVER blocked.
    - Added **Inventory Hashing** to skip redundant database writes if data hasn't changed.
- **Simplified Architecture**: Refactored the core logic into a cleaner multi-module system.
- **Banner**: Updated the startup console banner for a premium look.

### Fixed
- Fixed internal `Attribute` constant name changes between 1.20 and 1.21.
- Fixed `NoClassDefFoundError` occurring when specific version modules were missing.
- Fixed thread safety issues in MongoDB and SQL storage handlers.
- Fixed reflection issues for `PersistentDataContainer` compatibility across 1.20/1.21.

## [26.4.1-ALPHA] - 2026-04-11
- Initial test release for modern Minecraft versions.
- Dropped legacy NMS handlers.
- Refactored build system to Gradle Kotlin DSL.
