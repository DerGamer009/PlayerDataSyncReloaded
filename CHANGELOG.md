# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [26.4-Release] - 2026-04-12

### Added
- **Redis Sync Engine**: Fully implemented Redis Pub/Sub for near-instant synchronization across server networks.
- **Admin Utilities**: Added manual `/pds save <player>` and `/pds load <player>` commands for administrative control.
- **Modern Support Focus**: Optimized synchronization for Minecraft 1.20, 1.21, and experimental Paper versions (26.1).
- **Config-Driven Sync**: Added toggles to enable/disable specific data synchronization (Inventory, Stats, PDC, etc.).

### Changed
- **Dropped Legacy Support**: Removed all code and modules related to Minecraft versions 1.8 through 1.19 to improve performance and maintainability.
- **Code Optimization**: Refactored the internal version handling system for better stability on modern Paper/Folia environments.

## [26.4.1-ALPHA] - 2026-04-11

### Added
- **Initial Reload Release**: Complete rewrite and modernization of the sync engine.
- **Java 25 Support**: Leveraged the latest Java features for performance.
- **Multi-Database Support**: Added drivers for MySQL, MariaDB, PostgreSQL, and MongoDB.
- **Folia Compatibility**: Support for region-based multithreading.
- **AES Encryption**: Secure player data storage and transmission.
- **Expanded Sync Options**: Comprehensive data tracking including PDC and Attributes.
- **New Command System**: Base structure for `/playerdatasync`.
- **Improved Config**: New structured `config.yml`.

### Dependencies
- **HikariCP (4.0.3)**: Connection pooling for SQL databases.
- **MariaDB Java Client (2.7.12)**: Drive for MariaDB/MySQL.
- **PostgreSQL (42.2.27)**: Drive for PostgreSQL.
- **MongoDB Driver Sync (4.11.1)**: Driver for MongoDB.
- **Jedis (3.10.0)**: Client for Redis synchronization.
- **Gson (2.10.1)**: JSON serialization for complex data.
- **bStats (3.1.0)**: Plugin metrics and statistics.
- **Shadow (9.4.1)**: For creating fat-jars with relocated dependencies.
- **Run-Paper (3.0.2)**: Development environment for testing.
