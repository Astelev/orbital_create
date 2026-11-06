# High-altitude flight: experimental implementation

Minecraft 1.21.1 / NeoForge 21.1.256. Integration targets Sable 2.0.6,
Create 6.0.10 and Create Aeronautics 1.3.2. SimulatedExtra 0.3-beta is included
in the integration test run, but is not a dependency. AeroWorks and AeroUtils
remain independent. The Sable temporary-chunk optimization requires Cruising Speed
on the server and participating clients; other clients keep normal terrain tickets.

## Behavior

- In the Overworld, the **lowest point of the entire constraint-connected assembly** must be strictly
  above Y=356. A taller world additionally requires clearance above its build
  height and Sable's vertical collision prediction margin.
- Sable's own player-inhabited and force-loaded-sublevel decisions determine
  which ships remain active. Their terrain force-load requests are deferred at
  altitude. An unattended ship without a Sable loading ticket retains Sable's
  normal unloading behavior.
- High-altitude passengers stop contributing vanilla terrain generation and
  simulation tickets. Chunk tracking and entity tracking remain active.
  Ground-level players and other chunk loaders retain normal terrain loading.
- Passenger and altitude checks use the ship's current logical pose. The
  passenger fallback tests the current and previous poses separately to cover
  within-tick movement without including unrelated players between them.
  The same fallback extends Sable's own player-inhabited check at altitude,
  preventing a stale cached AABB from unloading a fast ship with a passenger.
  Sable's setting that protects player-inhabited ships from unloading is still
  respected; CruisingSpeed does not force-enable it.
  Sky collision-ticket bounds also use the live pose so a rapid ascent cannot
  generate terrain using stale low-altitude coordinates.
  During raw entity movement, passenger checks use the updated position rather
  than the entity AABB that Minecraft refreshes later in that movement call.
- NeoForge's synchronous terrain lookup during movement is intercepted only for
  entities aboard eligible ships. It uses a real loaded chunk when available,
  otherwise a marked `TemporaryLevelChunk`.
- The server sends a dimension-specific temporary view to passengers. The client
  supplies marked empty chunks for missing columns in that view. Real loaded
  terrain always wins. These objects never enter real chunk storage or the
  chunk-generation pipeline and reject block writes and save flags.
- Temporary chunks do not represent generated world columns. They are not saved
  to region files, do not emit ordinary terrain `ChunkEvent.Load` notifications,
  and disappear when the view ends, the player moves away, or the level unloads.
  Each temporary cache retains at most 64 objects; avoiding disk persistence does
  not mean using zero RAM.

## Descent

When the live hull reaches the threshold, CruisingSpeed stops intercepting Sable's
terrain requests and the passenger's temporary view ends. Client placeholders
are discarded, ordinary player tickets return, and Sable's existing
`inhabitChunk` path adds its own tickets and requests real FULL chunks. Existing
blocks and player edits are never regenerated or replaced with air.

CruisingSpeed does not teleport, stop, reset, save or restore physics velocities.
There is no separate descent queue or manual collider upload. Sable's ordinary
chunk and collision handling owns the transition, including force-loaded ships.
The previous custom hold, early terrain prefetch and 256-column handoff limit
have been removed.

Sable 2.0.6's native request waits synchronously for a FULL chunk. This can stall
the server while missing terrain is generated; it is not an asynchronous pause
of only one ship. Physics does not advance during that wait, and CruisingSpeed does
not modify either linear or angular velocity. Normal gravity, drag, controllers
and collisions may still change velocity once physics resumes.

Sable's internal APIs are version-specific: the optional dependency accepts
exactly 2.0.6. Other versions require revalidation. The server config is
`<world>/serverconfig/cruising-server.toml`, section `highAltitudeFlight`:
`enabled = true`, `minAltitude = 356`. Disabling returns ordinary loading behavior.

## Current limits

- Experimental: automated server tests do not establish high-speed multiplayer
  or graphical-client performance. Test in a separate world before merging into
  an existing modpack.
- Only the Overworld is enabled. Flying without a Sable ship does not activate it.
- Unusual passenger trackers, custom generators and
  abrupt teleports require manual validation. Teleports and commands that request
  real terrain retain their normal behavior.
- Distant Horizons remains optional. DH 3.3.3 has a client flight adapter for
  coarse-first generation, gradual refinement and generation-gate diagnostics.
  See [Distant Horizons flight](distant-horizons-flight.md). Temporary objects
  avoid the ordinary terrain-load notification path. New LOD data still costs
  computation; the adapter cannot guarantee that every modpack keeps up.
- Vanilla player view generation resumes as a normal queue on descent. Other
  mods' explicit synchronous terrain requests are not globally intercepted.

## Validation

With JDK 21:

```sh
./gradlew build
./gradlew runGameTestServer
./gradlew -PintegrationRuntime runGameTestServer
```

The opt-in integration runtime obtains pinned Sable, Create and Aeronautics
releases from Modrinth. To include a separately supplied SimulatedExtra JAR:

```sh
./gradlew -PintegrationRuntime -PextraIntegrationMod=/absolute/path/simulatedextra-0.3-beta.jar runGameTestServer
```

JUnit tests cover threshold boundaries, taller dimensions, invalid bounds and
view coordinates.
GameTests check non-persistent temporary air and network serialization.
With Sable installed, additional tests exercise the actual injected request,
passenger eligibility, movement with deliberately stale cached bounds, and
native loading of initially missing terrain with a non-player force-load ticket.
The stale-bounds test also invokes Sable's actual ticket manager to ensure it
keeps the ship active without generating terrain.
The native-loading test compares all components of linear/angular velocity and
the pose immediately before and after the request, without stepping physics.

Manual acceptance checks still needed:

1. Fly a piloted ship rapidly above Y=356 into new terrain; compare tick timings
   against the feature disabled. Check Distant Horizons visuals separately.
2. Descend and verify native loading, terrain replacement, collisions and
   continued flight. Compare horizontal speed and rotation before/after loading.
3. Keep another player below the same X/Z; their terrain and edits must remain real.
4. Rotate a tall ship, change dimensions, disconnect/reconnect and change render
   distance. Check that temporary views do not survive these transitions.
5. Repeat with a Sable force-loaded ship, connected ships, AeroWorks and AeroUtils.

## Passenger pose-handoff protection

Native Sable attachment (including a tracked/containing vehicle) now counts as
crew before testing the current and previous hull bounds. Previously, temporary
terrain eligibility already trusted Sable tracking, but the ticket-manager
unload safeguard only checked geometry. A tracked player between asynchronously
sampled poses could therefore enable temporary air yet fail to protect the ship
from unloading. A GameTest reproduces this mismatch with unloaded terrain and
verifies that the ship stays active without generating terrain. Removing native
attachment outside both hulls removes the exemption again.

This does not pin players to a ship, modify their velocity, or override native
collision/contact decisions. It is not a full reproduction of the reported case
where a standing player falls off while the ship continues flying.

A client-only, read-only diagnostic observes native attachment loss while the
temporary sky view is active (including its exit tick). At most once every ten
seconds it writes `CruisingSpeed sky passenger contact lost` to the log, with ship
presence, native last-tracking ID, temporary view/chunk availability, position
change, on-ground state and velocity. Normal walking/jumping away can also
produce this record; the record alone does not prove an error. If the standing
passenger issue persists, the surrounding client log can distinguish loss of
ship tracking, loss of temporary chunks, and a native collision transition.

## Connected sublevels (1.0.1)

Cruising Speed tracks native Rapier constraints, including the rotary constraints
used by swivel bearings. A passenger or an existing Sable force-load ticket on
one member protects its connected sublevels from unloading. Unoccupied,
unconnected ships retain native unloading behavior. This protection also applies
during descent; altitude optimization requires every connected hull to clear the
threshold. Connections to static terrain or unknown body types conservatively
retain ordinary terrain loading.

Joint addition, removal, native breakage and body/level disposal invalidate the
connectivity cache. Hull minima and force-load decisions are shared within one
native ticket update; movement checks outside that update use live poses. No
persistent force-load tickets are added.

Native GameTests cover an occupied three-body rotary chain, an unrelated nearby
ship, descent with stale cached bounds, detachment, parallel joints, and a
force-loaded rotary assembly with unchanged linear/angular velocity. These tests
exercise the swivel bearing's native constraint backend, rather than its graphical
block interaction. In-game validation with the reported construction remains useful.
