# Changelog

All notable changes to PlayerDataSyncReloaded will be documented in this file.

## [26.12-Alpha.3] - 2026-11-27
### Fixed (data loss and duplication)
- **An unchanged inventory skipped the whole save.** XP, health, food, ender chest, effects, advancements, balance and location changed without the inventory were not saved, also on quit and in the save handshake, so the next server rolled them back. A save is now only skipped when the whole record is unchanged.
- **Quick rejoin on the same server** read the record before this server's quit save had landed, applied the older record and later saved it over the newer one. The load now waits for that save.
- **Reconnect during a load** applied the record to the old player object and marked the new session as loaded without its data. Loads are now tied to the session and applied to the current player object (on Fabric a respawn replaces it).
- **A failed apply** (for example an unreadable inventory) left the local inventory in place, counted as loaded and saved it over the record. It is now a failed load with the usual retries, and the player stays unloaded.
- **Inventories from another platform** (Paper record on Fabric and the other way round, or Fabric 1.20/1.21 NBT on another version) were silently ignored and then overwritten. Reading now fails with a clear message.
- **Fabric records set Paper players to walk speed 0, no air, no flight and balance 0**: keys Fabric does not capture are now marked as not synced. The same for servers without a Vault economy: their 0 balance is no longer applied elsewhere.
- **Paper: actions before the load lands** (dropping items, picking up, inventory clicks, interaction, damage, hunger) are cancelled until the stored data is applied (`sync.lock_while_loading`, default `true`). If the load fails for good the lock is lifted, saves stay refused.
- **Spoofed `pds:sync` messages**: Velocity no longer forwards the channel from clients, Paper and Fabric ignore `load:` once the player is loaded, and own saves are not reapplied.
- **MySQL/MariaDB**: the `data` column is now `LONGTEXT` (was `TEXT`, 64 KB), existing tables are widened on start. Large inventories failed to save.
- **Shutdown on Fabric**: disconnect saves after the shutdown save no longer race with it and get refused.

### Fixed (Paper)
- Paper 1.21 and 1.21.1 hung every load (`Attribute.MAX_HEALTH` does not exist there). Attributes are now resolved at runtime on every version, and stored attribute names from 1.20, 1.21 and 26.x are understood by each other.
- Attributes are applied before health, advancements before experience; health is clamped to the maximum including modifiers.
- Health scale is only turned on if it was on, and only turned off by records that store that it was off.
- Players already online when the plugin is enabled (`/reload`, plugin managers) are loaded.
- Health is applied after effects and items, so health boost and armor count towards the maximum.
- `softdepend: [Vault]`, so the economy is found when Vault loads after the plugin.
- PDC sync never worked (it called a method the API does not have). It now stores the container's NBT and merges it on load.
- Excluded items are kept in the inventory instead of being deleted on load.
- The handler choice parses the version: 26.1, 26.1.3+ and 26.3+ no longer fall back to the 1.21 handler.
- `api-version` is `1.20` again, so the plugin loads on 1.20.x; the wrong `folia-supported` flag is removed.
- `/pds reload` reloads players whose load had not finished instead of saving their local data; a failed reload disables the plugin.
- `/pds save` and `PlayerDataSyncAPI#forceSave` report after the save finished; `/pds saveall` is no longer subject to the autosave cooldown.
- Update check: only a newer version counts, with connect and read timeouts.
- The save handshake closes the open inventory first, so items on the cursor are saved.

### Fixed (Fabric)
- The adapters only start on the versions they are built for: 1.20.1, 1.21.4, 26.1.x and 26.2.x. 1.20.2+ and 1.21.5+ crashed on the first save.
- Saturation and game mode are applied; health is applied after attributes and clamped.
- A single unreadable item is skipped instead of losing the whole inventory; a failing save keeps the stored record instead of storing no inventory.
- `storage.type` defaults to `mariadb` (`sqlite` was never supported and now fails with a clear message). `security.encryption_key` is read on Fabric too.
- The save handshake always answers, even if the save throws or the database is not reachable.

### Fixed (Velocity)
- Answers carry a request id, so a late answer from an earlier switch no longer releases the next one. Switches another plugin denied no longer trigger a save.

### Fixed (other)
- `storage.type: postgresql` is accepted; unknown types fail with a clear message instead of connecting to MySQL.
- A wrong or missing encryption key no longer falls back to old legacy columns (which could apply ancient data); the load fails.
- Encryption keys with non-ASCII characters work.
- The Fabric `playerdatasync.properties` is read as UTF-8.
- One unreadable record no longer stops a backup export.
- NaN or infinite values (from other plugins) no longer make a record unwritable.
- Excluded worlds match between Paper names and Fabric dimension ids.

### Tests
- New tests for the record fingerprint, quick rejoin, failed apply, stale sessions, economy, shutdown, world exclusions and version parsing (22 in total).

## [26.12-Alpha.2] - 2026-11-26
### Fixed
- **Disabled sync toggles overwrote player values (#7)**: a `sync.*` key that is off stored a placeholder (`SURVIVAL`, 20 health, 0 experience, ...) and every server applied it, e.g. `sync.game_mode: false` set players to Survival on every join and ignored `force-gamemode`. Records now carry `notSynced`; handlers on Paper and Fabric skip those keys and the keys turned off on the loading server. Placeholders stay in the record for older servers.
- `sync.statistics: false` also stopped advancements from being applied.
- The Vault balance of a record saved with `sync.economy: false` (stored as 0) is no longer written.

### Added
- **Fabric (#6)**: location sync (`sync.location`) on all four lines, and `/pds status|info|save|load|saveall` (alias `/playerdatasync`, permission level 2).
- `WorldNames`: Paper and Fabric translate `world`/`world_nether`/`world_the_end` and `minecraft:overworld`/`the_nether`/`the_end` into each other for location restore.
- Tests for the toggle behavior and the world name mapping.

## [26.12-Alpha.1] - 2026-11-26
### Added
- **Stale-save check for MongoDB**: `MongoStorage#saveChecked` stores `pds_saved_at` / `pds_saved_by` in each document and only writes over an unstamped document, its own, or the one it applied, like SQL since 26.11-Stable.
- **Fabric gets the 26.11 fixes**: the stale-save check, the recheck after a join and retried loads now run on Fabric too (the 26.11-Stable release had no Fabric build).
- **Fabric answers the save handshake**: after the proxy's `save:` the mod saves and replies `saved:<uuid>`, so switches away from Fabric backends no longer wait for `save_ack_timeout_ms`.
- **`/pds status`**: new line `Newer record elsewhere: refused saves=<n> | reloaded after join=<n>`.
- **`/pds info`**: new line `Applied record saved by: <server> | Refused saves since join: <n>`.
- **`server_name`** (config): stored in every record as `savedBy`; empty means `port <server port>`.
- **Discord**: event `save_refused` (`discord.events.save_refused`, default `true`) when a save is refused because another server stored a newer record.

### Fixed
- **Fabric could not read the proxy's messages**: `pds:sync` used a length-prefixed string codec, while Velocity and Paper send raw UTF-8. The payload is now read and written as raw bytes on every Fabric line.
- **Update notice**: the update check took the newest Modrinth entry of any platform, so Paper servers reported `26.11-Velocity-Stable` as a new version. It now only looks at Paper entries, and release servers are only pointed to releases.
- **MongoDB with a wrong encryption key**: a record that cannot be decrypted is now a failed load instead of "no record", so the server no longer overwrites it with its local data.

### Build
- Project reconstructed from the 26.10-ALPHA.001 jars (Paper, Velocity, Fabric) onto the 26.8 sources: the 26.9 and 26.10 changes are ported into the source tree.
- New module `:fabric` builds the single Fabric jar (bootstrap + all `:fabric-versions:*` adapters, each in its own package `fabric.<line>`). The Paper jar no longer embeds Velocity and Fabric under `bundled/`; `pds.fabric.bundle` / `pds.forge.bundle` are gone.
- JUnit tests for the server-switch logic in `common` (`SyncManagerSwitchTest`).

## [26.11-Stable] - 2026-11-26
### Fixed
- **Saves no longer overwrite a newer record from another server.** Records are stamped (`pds_saved_at`, `pds_saved_by` columns); a server writes only if the stored record is still the one it loaded, otherwise it keeps the stored record, logs `Refused to save data for <name>: …` and loads the newer one.
- **Recheck after join** (`sync.recheck_after_join_ms`, default `3000`): looks once more for a newer record from another server.
- **SQL read errors** count as a failed load and are retried up to three times (1, 2, 4 s) instead of being treated as "no record".

## [26.10-ALPHA.001] - 2026-09-10
### Added
- **Save handshake** (Velocity): before a switch the proxy sends `save:` and waits for `saved:<uuid>` at most `save_ack_timeout_ms` (`plugins/playerdatasync/playerdatasync.properties`, default `2000`).
- **`/pds info <player>`**.
- Paper and Fabric save every online player on shutdown and on `/pds reload`.
- Redis subscription reconnects every 5 s after a connection loss.
- One Fabric jar for 1.20.x, 1.21.x, 26.1 and 26.2 with its own libraries; separate Velocity jar.

### Fixed
- `/pds reload` in 26.9 refused all saves for players online during the reload.
- Leaving on the death screen stored 0 health and killed the player on the next server.
- A failed encryption stored the record unencrypted; it now fails the save.
- The unchanged-inventory check compares the full serialized inventory instead of a hash code.

## [26.9-Release] - 2026-09-10
### Fixed
- Loads are queued per player, saves are refused until the stored record has been applied, records carry `savedAt`, and a failed SQL save counts as failed.

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
