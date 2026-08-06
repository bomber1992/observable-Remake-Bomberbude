# Observable - Remake V1.0.2

Observable - Remake V1.0.2 by Bomberbude.de for Minecraft 26.1.2 and NeoForge 26.1.2.74 or newer compatible 26.1.2 builds. It provides tick profiling, in-world lag visualization, the Bomberbude profile service and optional AE2 grid-tick profiling.

## Target environment

- Minecraft **26.1.2**
- NeoForge **26.1.2.74 or newer within the 26.1.2 line**
- Java **25**

No separate Kotlin support mod is required. Kotlin and kotlinx.serialization are bundled as NeoForge Jar-in-Jar libraries.

## Preserved functionality

- TPS profiling with configurable duration and optional stack sampler
- Per-entity, block entity, scheduled block and fluid tick timings
- Sign and hanging-sign targets are intentionally excluded from profiling results
- Profiling traces, diagnostics, upload support and local JSON export fallback
- Public scanner-readable profile upload endpoint with no embedded API key
- Profiling mappings packaged as a JAR resource; no runtime GitHub download
- Compressed and chunked client/server result transport for large profiles
- Permission checks, allow/deny commands and profiling commands
- Profile screen, settings screen and key binding
- Dimension-aware heat-map overlay for entities and blocks; block visibility ignores vertical distance
- Horizontal block-distance, entity-distance, minimum-rate, normalization and maximum-count filters
- Through-wall boxes and billboard timing labels
- Teleport/result commands from the original server command set
- English and Russian translations

The old private-renderer reflection was replaced by Minecraft 26.1's native gizmo renderer. This preserves the overlay behavior while avoiding unstable private rendering internals.


## Profile data and network behavior

Completed profiles are uploaded to `https://obs.bombersbude.de/api.php?action=add`.
The endpoint contains no embedded key. Profile payloads are validated and rate-limited
by the service. See `PROFILE-DATA-AND-UPLOADS.md` for the exact data scope.

Method mappings are loaded from a resource inside the built JAR. Gradle obtains the
MCP mapping JSON at build time and packages it; the running mod no longer downloads
that file from GitHub. If the mapping download is unavailable during a build, an empty
offline fallback is packaged and stack traces remain usable without friendly method
remapping.

## Build

```bash
./gradlew clean build
```

The optional AE2 compile dependency is resolved from the standalone `api` classifier without
transitive dependencies. NeoForge itself is supplied exclusively by ModDevGradle, preventing
Gradle from selecting both `modDevApiElements` and `universalJar` for the same component.

The output JAR is written to `build/libs/`.

## Installation

Place the built Observable JAR in the dedicated server's `mods` folder. Players do **not** need Observable on their clients and may join with a compatible unmodded client.

Installing Observable on a client is optional. A client with the mod receives the profiling GUI, overlay and in-game result display. Start Minecraft and the server with Java 25.

## Validation status

The project compiles against the minimum supported NeoForge build **26.1.2.74**. Generated mod metadata accepts NeoForge versions from **26.1.2.74 inclusive up to, but not including, 26.1.3**. The optional-client distribution preserves the complete profiler, commands, GUI, overlay, mixins and resources from the full port. Its NeoForge payload registration is optional, and every network send is guarded by negotiated-channel availability.

The correction package was integrity-checked entry by entry. A fresh Java 25 compilation and runtime boot against every supported NeoForge build was not possible in the packaging environment; see the supplied validation report for the exact scope of verification.

## License, copyright and upstream

Observable - Remake is distributed under the Mozilla Public License 2.0
(MPL-2.0).

**Copyright (c) 2026 Bomberbude.de** applies to the modifications and original
additions authored by Bomberbude.de for this remake. It does not replace or
claim the copyright of the original Observable source code or other
third-party contributions. Existing upstream notices remain in effect.

See these files for the complete terms and attribution:

- `LICENSE` — complete MPL-2.0 license text
- `COPYRIGHT` — concise Bomberbude.de copyright notice
- `NOTICE.md` — copyright scope, upstream attribution and project links

Remake repository: `https://github.com/bomber1992/observable-Remake-Bomberbude`

Original repository: `https://github.com/tasgon/observable`

Original profile website: `https://observable.tas.sh/`

## Server-only installation / optional client

The mod may be installed on the dedicated server without requiring players to install it.
Observable networking is registered as optional and server-to-client packets are only sent to
connections that negotiated the Observable channel. Players without the mod can join normally.
Clients with Observable installed keep the GUI, overlay and in-game result display. Server console
commands and profiling continue to work independently of client installation.

## Release artifact

The standard build produces:

```text
Observable-Remake-V1.0.2-NeoForge-26.1.2.74.jar
```

The internal mod ID remains `observable` so existing configuration, commands, packets and mixins remain compatible.

