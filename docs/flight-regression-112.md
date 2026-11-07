# Flight and generation follow-up, 1.1.2-diagnostic

The reported terrain wait started in 1.1.0. The Sable terrain exemption, passenger
recognition, altitude policy and movement fallback did not change between 1.0.1
and 1.1.1. However removing global LOD coarsening also removed the delay before
new fine generation. The restored policy is now generation-only: existing native
render detail remains untouched, waiting fine requests are not discarded, and
active work is not interrupted. This addresses that demonstrated policy change;
it does not establish the user's exact physical loading failure.

The submitted trace has only three initial samples at player Y≈436, followed by
samples below 356. Its DH `active=true` flag is a client camera-height flag,
not proof of server-side crew eligibility. It lacks server core enable/threshold,
protocol, hull/assembly minima and terrain ticket information. Those now appear
in bounded [CRUISING-FLIGHT] rows every 5 seconds above Y320.

Summed diagnostic CPU from the user trace (CPU seconds, not session wall time):

| Phase | CPU seconds | Calls |
| --- | ---: | ---: |
| DH 3D noise workers | 66.3 | 1533 |
| DH feature groups | 51.9 | 528 |
| DH surface groups | 26.0 | 652 |
| chunk-to-LOD conversion | 13.9 | 4943 |
| DH biome workers | 4.7 | 1533 |
| block lighting | 4.1 | 5439 |

Rough surface self CPU is also substantial: about 399 s at detail 1, 214 s at
detail 2 and 50 s at detail 3. Sources cover different areas; these totals cannot
be read as a per-chunk comparison. Ordinary Minecraft noise generation is logged
separately (about 83 s) and must not be confused with DH worker totals. Caller
and worker inclusive totals overlap; the table deliberately avoids double-counting
calling noise/biome stages with their worker measurements.

Validation includes native queue pause/resume without cancellation, native render
detail unchanged, 48 unit tests, 8 Sable GameTests, and a real network-player
fixture with Sable/DH. A held test body keeps the crew physically near the deck
while checking temporary client view and native ticket suppression; movement
through three fresh target columns does not generate their terrain. This is a
controlled fixture, not reproduction of the user's aircraft/modpack.
