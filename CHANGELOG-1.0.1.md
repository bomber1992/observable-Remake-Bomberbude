# Observable - Remake 1.0.1

## Security and distribution changes

- Replaced the keyed upload URL with the public endpoint
  `https://obs.bombersbude.de/api.php?action=add`.
- Removed upload URL obfuscation and runtime string reconstruction.
- Removed the runtime download from `raw.githubusercontent.com`.
- Profiler MCP mappings are now packaged as a normal JAR resource during build.
- Added an explicit profile-data and upload disclosure.
- Added third-party mapping attribution.
- Updated the project website metadata to `https://obs.bombersbude.de/`.

## Compatibility

- Minecraft 26.1.2
- NeoForge 26.1.2.74 or newer compatible 26.1.2 builds
- Java 25
