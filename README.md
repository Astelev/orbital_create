# Aeronautics: Cruising Speed

A standalone NeoForge mod for fast travel and distant terrain in Minecraft 1.21.1.
Mod ID: `cruising`. Development target: **NeoForge 21.1.256**, Java 21.

With **Distant Horizons 3.3.3**, Cruising Speed fills missing terrain with a cheap,
biome-colored surface. Static biome-height estimates, optional smoothing and
closed boundary skirts provide a rough silhouette until real LODs are ready.
It does not run the terrain generator to predict surface height. Ready DH and
Minecraft terrain takes over through a world-space ownership mask and depth.

Optional DH adaptations use coarse detail during high flight, refine after
slowing down, and combine distance with camera-travel direction for waiting
LOD generation/retrieval/mesh tasks. They also support creative/elytra cameras
above the configured altitude, without Sable, Create or Aeronautics.

**Sable 2.0.6** additionally enables the existing server/client high-altitude
ship optimization with temporary air chunks. Connected sublevels, including swivel
bearing assemblies, share passenger protection and use their lowest hull for
altitude eligibility. Create, Aeronautics, AeroWorks and
AeroUtils are not required. The mod registers no blocks, items or creative tabs.

## Configuration

Open **Mods → Cruising Speed → Config**. English and Russian labels are provided.
The client file is `config/cruising-client.toml`, section `distantHorizonsFlight`:

| Setting | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master switch for client DH integration and the biome surface |
| `modifyDistantHorizonsBehavior` | `true` | Allow detail changes, motion-pause bypass and queue priority changes |
| `adaptAboveY` | `356` | Activate DH flight adaptations above this camera Y, independently of Sable |
| `fallbackPlane` | `true` | Show the independent biome-based distant surface |
| `smoothFallbackTerrain` | `true` | Smooth height transitions; disabling retains closed stepped cells |
| `fallbackBiomeHeights` | `true` | Use static biome-height estimates; disabling gives a flat surface |
| `fallbackBiomeColors` | `true` | Sample biome colors in singleplayer |
| `fallbackBrightness` | `0.55` | Surface brightness before world lighting |
| `nearbyGenerationPriority` | `true` | Prefer nearby generation tasks |
| `forwardGenerationPriority` | `true` | Add camera-travel weight to distance |

The existing detail, refinement delays, neutral color/height and diagnostics
settings remain available. To retain vanilla DH behavior but keep the biome
surface, set `modifyDistantHorizonsBehavior=false`. To disable the surface,
set `fallbackPlane=false`. No Distant Horizons configuration file is overwritten.

The separate server file `cruising-server.toml`, section `highAltitudeFlight`,
controls Sable temporary-chunk optimization (`enabled`, `minAltitude`). It is
stored in the world's `serverconfig` directory, independently of client options.

## Installation and migration

Replace the previous AeroSpace JAR with the new Cruising Speed JAR. Do not install
both versions together. The old template example block, item and creative tab
are removed. This is a new mod namespace; old `aerospace-client.toml` settings
are not imported automatically. If needed, copy their values into the new file
before launch; missing new settings receive their defaults.

The visual module can be installed client-side. On remote servers without the
world seed it cannot independently sample biomes, so unknown surface cells stay
neutral. Sable temporary-chunk optimization requires the mod on both server and
client; its optional channel is checked before suppressing real player tickets.
Fake simulation players do not need a network channel.

See [DH behavior and rendering](docs/distant-horizons-flight.md) and
[Sable flight support](docs/high-altitude-flight.md) for scope and limitations.

## Development

```sh
./gradlew build
./gradlew runGameTestServer
```

Output: `build/libs/cruising-1.0.0.jar`.
Optional runtime checks:

```sh
./gradlew -PdhJar=/absolute/path/DistantHorizons-3.3.3-1.21.1.jar runClient
./gradlew -PintegrationRuntime -PdhJar=/absolute/path/DistantHorizons-3.3.3-1.21.1.jar runClient
```

`integrationRuntime` adds the pinned Sable/Create/Aeronautics test dependencies;
they are not bundled in the mod. Development smoke-test code is not in the JAR.
