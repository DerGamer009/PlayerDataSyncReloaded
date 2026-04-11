# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [26.4.1-SNAPSHOT] - 2026-04-11

### Added
- **Initial Reload Release**: Complete rewrite and modernization of the sync engine.
- **Java 25 Support**: Leveraged the latest Java features for performance.
- **Multi-Database Support**: Added drivers for MySQL, MariaDB, PostgreSQL, and MongoDB.
- **Redis Pub/Sub Sync**: Implemented ultra-fast synchronization for high-traffic networks.
- **Folia Compatibility**: Support for region-based multithreading.
- **AES Encryption**: Secure player data storage and transmission.
- **Expanded Sync Options**:
    - Inventory and Ender Chest.
    - Health, Food, and Experience.
    - Potion Effects.
    - Advancements and Statistics.
    - Game Mode and Flight status.
    - Location tracking across instances.
    - Attributes and Persistent Data Container (PDC) data.
- **New Command System**: Improved `/playerdatasync` (aliases: `/pds`, `/pdasync`).
- **Improved Config**: New structured `config.yml` with clear documentation for all settings.

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
