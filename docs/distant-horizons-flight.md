# Distant Horizons flight LODs

Client integration for **DH 3.3.3 / Minecraft 1.21.1**. Camera height above
`adaptAboveY` (default 356) activates the optional flight adaptations without
Sable, Create or Aeronautics; a server-authorized Sable sky view also qualifies.
`modifyDistantHorizonsBehavior=false` disables those adaptations independently
of the biome surface. The surface works throughout the Overworld, including
normal ground-level play. Sable's server-side temporary-chunk support is optional.

## Progressive generation; native render detail

Since 1.1.0, Cruising no longer imposes a coarse floor on DH's rendering tree.
Ready terrain keeps DH's normal distance/quality selection, even during fast
flight. Unavailable terrain is generated through shared 8→4→2→1 block surface
stages. Native data ownership, merging and parent retention remain with DH.
Progressive generation works near the ground too; flight altitude only controls
motion-pause bypass and travel-priority adaptations.

The new `config/cruising-common.toml` contains:

```toml
[dhProgressive]
enabled = true
initialDataDetail = 3
previewRadiusBlocks = 1024
maxDeferredRequests = 128
```

Restart after changes. `initialDataDetail` is 1..4 (2..16-block columns), radius
128..4096 blocks, deferred limit 16..512. Existing surface stages are shared;
refinement starts after native publication. This improves first coverage, while
extra rough stages may add total generator work. It does not cache every noise
sample or reuse full chunk-generation intermediates. CHUNKS_ONLY, custom/API
generators and remote queues retain the native path. The client master/modify-DH
switches also disable the progressive adapter locally; the common switch applies
on dedicated servers as well. DH remains optional and is never bundled.

With around_planet 0.10.1+, Cruising owns scheduling even when disabled, so the
legacy experiment cannot start a second queue. Install both updated JARs together
if using the previous around_planet experimental version. Optional topology
lookup preserves periodic X priority and avoids ancestor sources crossing the
canonical seam. Neither mod becomes a required dependency of the other.

Bounded `[CRUISING-PROGRESSIVE]` counters report stage submissions/publications,
queue selection and queue-to-result wall time every 30 seconds during work.
These wall times include waiting, not only generation CPU time. For an A/B test,
compare fresh regions at the same seed/settings and leave the game unpaused.

DH's `pauseIfMovingQuickly` gate is bypassed only during this sky view, including
stationary flight. Its generation-plan setting, server-load
protection and queue backpressure remain in force. Use `SURFACE_THEN_CHUNKS` or
`SURFACE_ONLY` to benefit from rough surface generation; `CHUNKS_ONLY` still
requires the chunk generator and `DISABLED` remains disabled.

The client config is `config/cruising-client.toml`, section
`distantHorizonsFlight`:

| Setting | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master switch for client integration and surface |
| `modifyDistantHorizonsBehavior` | `true` | Allow progressive generation, motion-pause and queue adaptations |
| `adaptAboveY` | `356` | Camera height for standalone DH flight adaptation |
| `smoothFallbackTerrain` | `true` | Smooth height transitions; disable for closed stepped tiles |
| `coarseDetail` | `4` | New-generation floor during sky cruise, capped at initialDataDetail; ready render detail is unchanged |
| `fastSpeed` | `20.0` | Speed that postpones new fine generation |
| `quietSeconds` | `5` | Quiet delay before admitting finer new work |
| `refineSeconds` | `10` | Interval for admitting finer new work |
| `diagnostics` | `true` | F3 status and a log line at most every 30 seconds |
| `fallbackPlane` | `true` | Draw a temporary surface in DH's terrain pass |
| `fallbackHeight` | `60` | Height of the temporary plane |
| `fallbackColor` | `0x718a54` | Neutral RGB color until a biome sample is available |
| `fallbackBrightness` | `0.55` | Surface brightness multiplier, before world lighting |
| `fallbackBiomeColors` | `true` | Budgeted independent biome sampling in singleplayer |
| `nearbyGenerationPriority` | `true` | Rank queued DH tasks by distance to their bounds during sky flight |
| `forwardGenerationPriority` | `true` | Add camera-travel weight to generation, retrieval and mesh-request ordering |
| `fallbackBiomeHeights` | `true` | Raise surface tiles using a static biome-height table |

## Temporary visual floor

The temporary surface is inserted into **DH's terrain target**
after opaque LODs and before SSAO, transparency, fog and compositing. It is a
world-space tiled surface above the configured Y=60 base, projected directly with DH's forward view/projection
matrix and its exact camera position, just like native terrain buffers. No inverse
screen-ray reconstruction or separately sampled Minecraft camera is involved.
The rasterizer writes the actual depth of that surface into the DH render buffer.
Smoothed tile tops and boundary skirts are cached in a GPU vertex buffer, refreshed
with the biome palette rather than rebuilt on each frame. Outside the sampled
area, a neutral base plane continues to the DH far distance. Native DH fog and far fading therefore see its proper distance
and height. Surface colors are multiplied by DH's open-sky lightmap, so the plane
responds to day/night, weather and the lightmap's other adjustments. The uploaded
vanilla lightmap uses block light on X and sky light on Y; the surface samples
block=0, sky=15, so daylight exposure does not act as emissive block light. Biome tints
are reduced to 80% saturation and 55% brightness by default to better match
textured LOD terrain; `fallbackBrightness` controls the brightness multiplier.

Inside the sampled area, a 16-block world-space ownership atlas masks the
approximate surface wherever DH already has enabled, uploaded terrain data or
ordinary client terrain columns are loaded. This includes empty sky pixels above
that terrain: camera depth alone cannot tell that an estimated biome tile belongs
to an already-ready part of the world. Owned tiles retain their table samples only for smoothing adjacent cells; their
rendered surface remains masked. Closing skirts are generated from the unknown
side of the actual ownership boundary.

DH coverage is copied from the existing 64×64 column render source during mesh
building, then attached to the **exact buffer container after its GPU upload**.
It is not attached merely to a section position: a pending rebuild cannot claim
coverage on behalf of an older displayed buffer. The mask reads enabled sections
before camera-frustum culling, so looking up or viewing from the ground does not
resurrect a fallback over ready ground. A partial section masks only complete
16-block footprints; its missing data still has fallback coverage. Closed/old
buffer metadata uses weak keys and is released with those buffers. No database,
height, biome, or world-generation query is added for DH ownership.

Unknown footprints use ordinary forward/reversed **DH depth testing**, rather
than always being composited behind every LOD pixel. A nearer fallback in a
missing area can occlude farther real terrain; closer real terrain still wins.
Real vanilla geometry retains its separate pixel-depth protection. Beyond the
bounded atlas, only the neutral base backdrop remains and it keeps the
conservative clear-DH-pixel mask. Tile tops are one-sided and risers face outward,
so looking from inside/below cannot show their underside as a green ceiling.

Real client columns are tracked by load/unload events and non-generating cache
validation. Temporary air does not count as real terrain. Ownership masks refresh
with the palette, normally within 200 ms; their GPU atlas is 528×528 for the
current 8,448-block-wide surface area. The biome/height grid remains 64 blocks.
There is no production CPU depth readback, and no fake data is saved in Minecraft
chunks or the DH database. Native DH fog and compositing handle the resulting
surface depth normally.

Standard OpenGL forward/reversed depth is supported, including reversed DH with
conventional Minecraft depth. A shared vanilla/DH depth texture or a target whose
depth attachment differs from DH's active depth texture is skipped. Shader-pack
compatibility beyond this standard path is not established.

## Independent biome colors

In singleplayer, a low-priority background worker directly samples the integrated
server's biome source and climate sampler at the plane height. This **does not**
request chunks or run the terrain's final-density or surface-generation passes.
The bounded palette has 132×132 cells of 64 blocks, about 4.2 km in each direction.
Missing colors are sampled nearest-first, starting under the camera.

At most one batch runs at a time, with no queued backlog. Each client tick can
start up to 64 samples with a cooperative 2 ms budget. A single ongoing lookup
cannot be interrupted; subsequent lookups are deferred once the budget expires.
Neutral cells remain visible immediately if sampling cannot keep up with flight.
The small color texture refreshes at most five times a second, except when the
camera moves the grid or the world changes. F3 reports known palette cells.

Oceans/rivers use water color; deserts/beaches use sand or stone; badlands use a
terracotta tint; cold biomes use snow; other biomes use their grass color. These
are coarse biome colors, not a prediction of individual blocks or terrain height.
The integration can also reuse existing DH biome observations. Multiplayer
clients cannot independently sample an undisclosed world seed: without available
samples, their cells remain neutral. No seed request or server protocol is added.

The palette survives descent and exit from temporary sky mode. It clears on
world exit and dimension change. In-flight jobs
carry a world epoch, so old-world results cannot color a new world.

## Approximate biome heights

The same biome observation now supplies color **and** a static height offset.
There is no second biome query, heightmap lookup, chunk request or terrain-density
pass. These are deliberately chosen visual estimates: Minecraft 1.21.1 does not
provide a universal average-height field for each biome, and terrain can vary
substantially inside one biome.

With the default base of Y=60:

| Biome group | Surface Y |
| --- | --- |
| Known oceans, rivers, beaches | World sea level (normally 63) |
| Unknown biomes/samples | Configured base (60) |
| Swamps | 64 |
| Plains, desert, savanna | 68 |
| Forest, jungle, taiga | 76 |
| Badlands, old-growth taiga | 84 |
| Hill-tagged biomes | 92 |
| Savanna plateau, wooded/eroded badlands | 100 |
| Meadow / cherry grove | 116 / 124 |
| Other mountain-tagged biomes | 172 |

Known water/coast samples use the integrated server's actual sea level, rather
than assuming the neutral plane is the sea surface. Signed offsets also allow a
sea below a custom neutral base, without generating terrain.

Exact table entries take precedence over generic mountain/hill tags. Modded
biomes can use those tags; otherwise they stay at the neutral base. Unknown
samples also stay at the base until the worker supplies their biome. Heights are smoothed using shared corners averaged from neighboring table
samples, with four triangles around each cell's original center height. Adjacent
cells meet continuously without vertical steps, and isolated peaks retain their
biome height. Water/coast corners are pinned to the water samples, keeping water
flat and allowing neighboring land to slope down to the same edge.

A cached skirt is built at every 16-block boundary between fallback and ready
DH/vanilla coverage. It starts at the exact interpolated top edge and extends
below the world's minimum Y (at least 256 blocks below the neutral base). Its
ownership/color coordinates come from the missing column's center, rather than
the exact edge: otherwise the ownership mask would discard the closing wall.
Triangle interpolation used for skirt endpoints matches the GPU top triangles,
so slopes and skirt tops cannot separate. Only ownership/outer boundaries need
skirts; internal transitions between unknown neighboring cells are smooth.

This is array arithmetic over existing biome samples, not terrain generation or
heightmap sampling. Geometry still refreshes with the palette, not each frame.
Normal DH/vanilla depth still masks all of this approximate geometry. Set
`fallbackBiomeHeights=false` to return to a flat surface.

## Nearby generation priority

DH 3.3.3 already sorts retrieval requests by distance. Its generation queue,
however, ranks a task using **distance to the section's center divided by its
detail level**. A coarse, large section can receive a discount, while a section
covering the player can be penalized because its center is far away.

During sky flight, the optional adapter ranks waiting generation tasks by
horizontal distance to the section's **bounds**, without the coarse discount.
A section containing the target comes first; equal distances prefer the smaller
section. This operates on DH's current generation target, so it follows movement.
The forward preference is added to distance, rather than replacing it. Horizontal
camera velocity supplies direction; camera look direction and ship identity are
not used. At least 5 blocks/s of horizontal movement is required; stops, flight
exit and teleport-sized jumps clear the heading. The first 512 blocks retain pure
distance ordering. Beyond that, the effective distance is:

`distance - 0.6 × alignment × (distance - 512)`

Here alignment is the cosine toward camera travel, between -1 and 1. An equally
distant forward section is favored over one behind; sufficiently close sections
still win. The modifier applies to waiting generation tasks, data retrieval, and
mesh/build-upload request ordering. It does not preempt an already running job or
reorder DH's internal GPU driver queue. A sort snapshots its heading and enable
state so a turn/stop cannot invalidate the comparator midway through sorting.
At rest, retrieval/mesh ordering returns to DH's native behavior, while the
existing near-first generation adaptation remains active during sky flight.

Already running jobs finish normally, and native generation/load limits remain
in force. Leaving sky flight restores DH's original ranking. This cannot make
all nearby LODs appear immediately: generation, data processing and mesh building
still take time and have their own queues.

## Why generation may stop while stationary

Inspection of the supplied DH JAR found no requirement for an actual terrain
chunk under the player in its client quadtree/request path. The client path
tests that a player and render state exist, then requests data using camera X/Z.
The rough generator uses the server seed/density functions rather than a loaded
player chunk. This is static evidence, not a reproduction of every modpack stall.

DH does have multiple independent generation gates:

- Camera speed above 20 blocks/second when `pauseIfMovingQuickly` is enabled.
- In singleplayer, `limitIfServerUnhealthy` requires mean tick time **below
  25 ms** and p99 tick time **below 50 ms**. Stable 20 TPS alone does not satisfy
  this stricter condition. Minimizing rendering or pausing the local world may
  change the observed timing and allow background work to resume.
- Chunk-to-LOD builder and data-processing queue backpressure.
- A disabled generation plan, read-only DH data, or simply no pending tasks.

While flying, F3 includes `CruisingSpeed LOD` with the current minimum cell width and
the latest DH generation-gate result:

| Gate | Meaning |
| --- | --- |
| `ALLOWED` | DH permitted work; this does **not** prove tasks are running |
| `SERVER_LOAD` | The native server-health gate blocked work |
| `CHUNK_BACKLOG` | The native chunk-builder backlog blocked work |
| `UNKNOWN` | No recent gate observation, for example an empty generation queue |

The next line shows integrated-server mean/p99 times. These metrics deliberately
match DH's calculation. The adapter does not disable health protection to hide
load; compare the status before/after minimizing the window to diagnose that
case. Multiplayer server timing is not available to the client.

## Development verification

Build and unit tests: `./gradlew build`.

With the supplied DH JAR available locally:

```sh
./gradlew -PintegrationRuntime -PdhJar=/absolute/path/DistantHorizons-3.3.3-1.21.1.jar runClient
```

The development-only smoke source set verifies the actual transformed DH
quadtree, motion-pause override, native gate diagnostics and exit restoration:

```sh
./gradlew -PintegrationRuntime -PdhJar=/absolute/path/DistantHorizons-3.3.3-1.21.1.jar \
  -PdhClientSmoke -PquickPlayWorld=CruisingSpeed-DH-Smoke runClient
```

Prepare a disposable singleplayer save named `CruisingSpeed-DH-Smoke` first. The
smoke test exits the client when successful and logs `CRUISING_SPEED_DH_SMOKE PASS`.
Its source set is not included in the published mod JAR. Tests are not a
performance benchmark: flight speed, world generator, DH render distance, CPU
budget and modpack load determine whether new surface can keep up in practice.

The client smoke test also reads back pixels from small offscreen buffers:
missing terrain is filled; existing vanilla depth, existing DH depth, an upward
view, and a camera below the plane remain unchanged. Written DH depth is
unprojected to verify both the Y=60 base and a raised Y=92 tile. Ground views from below raised tiles, a masked real-terrain footprint, culling
state restoration, normal/reversed depth and an asymmetric 16×16 lightmap are checked: the open
night sky is dark while emissive block-light entries remain bright. This catches
accidental swapping of the sky/block axes.
The test verifies the transformed native generation, retrieval and mesh comparators,
including turns and a stable in-progress sort, then verifies that the native surface hook continues drawing after the
temporary sky view is disabled, without clearing the palette. These tests do not benchmark an entire flight or guarantee
compatibility with shader packs.

World-space coverage regression checks include complete/partial native render
sources, negative coordinates, coverage at chunk resolution, metadata belonging
to distinct GPU buffers, and a live upload through the transformed DH code. GPU
probes verify that a ready footprint masks sky while its unknown neighbor remains
visible, and that a closer fallback can occlude farther DH depth in both depth
directions. Sea-height checks cover the standard sea level and a neutral base
above the sea.

Smoothing/skirt regression checks verify shared ramp edges, preserved cell-center
heights, flat water, outward winding and skirt depths below terrain. GPU probes
view an ownership boundary from the ready-terrain side and verify that its skirt
fills the gap despite the ready-area mask, in normal and reversed depth modes.

## Configuration controls and migration

The new mod ID is `cruising` and the client file is
`config/cruising-client.toml`. The template common config, example items/blocks
and creative tab have been removed. The NeoForge config screen includes English
and Russian labels. The separate server Sable settings remain in
`<world>/serverconfig/cruising-server.toml`.

`modifyDistantHorizonsBehavior=false` leaves the independent surface enabled but
restores native DH detail selection, motion-pause behavior and queue ordering.
`fallbackPlane=false` disables the surface and its independent biome sampler.
`smoothFallbackTerrain=false` produces flat biome cells and closed internal
steps while retaining the ownership-boundary skirts. `fallbackBiomeHeights=false`
uses the neutral base plane instead of biome-height estimates.

Replace the old AeroSpace JAR rather than loading both versions. Old
`aerospace-client.toml` settings can be copied into the new filename manually;
new settings default automatically. No DH config file is modified.

## 1.1.0 validation

- 48 unit tests pass, including copied publication/cancellation/retry/priority
  regressions and unchanged render detail during active flight.
- Native DH 3.3.3 client passes alone, then with around_planet 0.10.1, Sodium
  0.8.13, Sable 2.0.6, Create and Fabric Biome API: shared 8/4/2/1 publication,
  priority over 2048+ waiting tasks, and disabled-mode native bypass.
- Real quadtree distance/detail queries at 1, 100, 1000 and 10000 blocks return
  the same values with flight active and inactive. The global render-floor hook
  was removed, rather than scheduling regeneration of already available LODs.
- Paired fixture detects the active 512-block circumference, rejects an ancestor
  spanning beyond its canonical bounds and confirms the old scheduler is off.
- All 8 native server GameTests pass with Sable and without DH, including connected
  sublevels and high-altitude retention. DH and smoke fixtures are not bundled.

## 1.1.2 flight generation guard

The render floor remains removed. The previous speed/quiet-time policy now
controls only admission of NEW queued DH work: native queue reduction skips
tasks finer than the current generation floor, capped at initialDataDetail.
Waiting tasks stay queued, in-flight tasks finish, and existing LOD data and
render detail are unchanged. After slowing, finer work resumes progressively;
leaving sky adaptation clears the guard. This restores a distinction lost in
1.1.0, when removing render coarsening also allowed immediate fine generation
during fast sky flight. It is not proof of the exact user's physical loading
failure; that path needs the new flight eligibility trace.

Every five seconds above Y320, [CRUISING-FLIGHT] records server enable/threshold,
protocol availability, sky eligibility, tracked/selected ship, live hull and
connected-assembly minimum Y, external anchors, and Sable terrain tickets
skipped/allowed. Plot tickets are excluded from the terrain counters and remain
native. Eligibility is still strict: the entire connected assembly must clear
the altitude/collision margin; no safety checks were relaxed.

A new real-client Sable fixture verifies protocol negotiation, temporary client
view, native player ticket suspension and no real terrain under the crew. It
then moves the ship/player through three fresh columns without generating them.
Native queue regression verifies that fine jobs wait without cancellation,
resume after leaving cruise, and that ready quadtree detail stays unchanged.
