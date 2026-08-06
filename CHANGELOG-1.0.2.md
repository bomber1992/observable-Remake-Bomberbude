# Observable - Remake 1.0.2

## Bug fixes

- Fixed block overlays disappearing when the player was far above or below the profiled area.
- Block overlay distance now uses X/Z distance only; vertical Y distance no longer hides results.
- The block overlay limit now keeps the highest-impact entries instead of the first traversed chunks.
- Added the existing maximum block overlay count to the client settings screen.
- Fixed the normalization checkbox so it reflects the current configured value.

## Profiling changes

- Signs and hanging signs are no longer profiled or included in client results and uploaded profiles.
- Vanilla `minecraft:sign` and `minecraft:hanging_sign` targets are excluded.
- Modded block-entity identifiers ending in `_sign` are excluded as well.

## Compatibility

- Minecraft 26.1.2
- NeoForge 26.1.2.74 or newer compatible 26.1.2 builds
- Java 25
