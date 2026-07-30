PlayerDataSyncReloaded — bundled platform JARs
==============================================

This JAR is the Paper / Purpur / Folia plugin (see plugin.yml at the archive root).

Inside the "bundled/" folder you will find the other loaders as separate JAR files
(same build, same version — copy them out and install on the matching platform):

  bundled/playerdatasync-velocity.jar  → Velocity: place in the Velocity plugins folder
  bundled/playerdatasync-fabric.jar  → Fabric:  place in the mods folder (or Modrinth "Fabric" file). Built from :fabric-versions:* (see gradle.properties pds.fabric.bundle).
  bundled/playerdatasync-forge.jar   → Forge:   place in the mods folder (or Modrinth "Forge" file). Built from :forge-versions:* (pds.forge.bundle).

Modrinth: upload the Paper file from this archive as the plugin; for Fabric/Forge/Velocity,
upload the extracted file from bundled/ (Modrinth does not read nested JARs as loaders).

Paper 1.21.x remapper ("Unsupported class file major version 69"):
  - Remove any OLD jar from plugins/ (e.g. PlayerDataSyncReloaded-26.5.5-SNAPSHOT.jar). Only ONE PDS jar should exist.
  - Install the jar whose name matches your build (see gradle.properties "version", e.g. PlayerDataSyncReloaded-26.6-Release.jar).
  - Rebuild with ./gradlew :plugin:clean :plugin:build after pulling the repo so bytecode is Java 21 + MR layers stripped from the shaded jar.
