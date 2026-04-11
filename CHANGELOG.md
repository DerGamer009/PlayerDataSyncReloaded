# Changelog

All notable changes to PlayerDataSyncReloaded will be documented in this file.

## [26.4-Release] - 2026-04-12
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

## [26.4.1-ALPHA] - 2026-04-11
- Initial test release for modern Minecraft versions.
- Dropped legacy NMS handlers.
- Refactored build system to Gradle Kotlin DSL.
