# Chunk generation phase profile (Cruising 1.1.1-diagnostic)

Diagnostic build for DH 3.3.3 / Minecraft 1.21.1 NeoForge. Existing progressive
generation and native render quality are unchanged. Profiling defaults on in this
build; disable with `dhProgressive.profileChunkGeneration=false` in
`config/cruising-common.toml` and restart. Timers add diagnostic overhead.

## User test

1. Replace Cruising with this JAR. Keep the same modpack, DH quality, thread count
   and generation plan. Around Planet 0.10.1 can stay installed.
2. Enter a fresh region whose fine LODs have not already been generated. Let the
   coarse surface appear, then remain there unpaused for 2–3 minutes while fine
   generation continues. Reusing a fully generated area will mostly measure cache
   hits, chunk reads and conversion.
3. Exit the world normally to flush final counters. Send `logs/latest.log` before
   another launch overwrites it. Do not delete existing world or DH data.

Rows start with `[CRUISING-GEN-PROFILE]`; they are aggregated every 20 seconds
while work completes and flushed on server shutdown (including singleplayer).
No per-block/per-position logging and no profiler worker threads are added.

## Rows and interpretation

| Prefix | Measurement |
| --- | --- |
| `dh.step.StepStructureStart/Reference_neoforge` | DH structure starts and references |
| `dh.step.StepBiomes_neoforge` | biome stage including any future wait |
| `dh.step.StepNoise_neoforge` | 3D density/block-fill stage including any future wait |
| `dh.step.StepSurface_neoforge` | surface rules and material assignment |
| `dh.step.StepFeatures_neoforge` | decorations, ores, trees and feature placement |
| `dh_worker.biomes`, `dh_worker.noise_fill` | actual Minecraft work for tagged DH chunks, including offloaded CPU |
| `minecraft_worker.*` | corresponding ordinary Minecraft work, kept separate |
| `*_worker.carvers` | conventional carver calls when invoked; noise caves can be included in density filling instead |
| `dh.lighting`, `dh.block_lighting` | DH light propagation/baking |
| `dh.chunk_to_lod` | chunk column scanning and conversion into native LOD data |
| `dh.rough_surface.detail_N` | native rough surface generation at each data detail |
| `dh.batch`, `dh.generate_direct` | inclusive batch and direct generation, including halo/neighbour chunks |
| `async.disk_request` | time from NBT request to completion, including queue wait |
| `async.full_request`, `async.rough_request` | request to queue-result time; includes waiting and generation |

`n` is calls, not necessarily chunks: a DH stage processes a group and may skip
already complete chunks. Batches can need many neighbouring chunks to produce a
small output. `totalWallMs`, `avgWallMs`, `maxWallMs` are elapsed time. Parallel
work overlaps, so total elapsed durations can exceed the session duration.
`threadCpuMs` is CPU consumed by the measured thread, not the whole process.
`cpuSamples=0` means CPU was not measured, **not zero computational cost**.
`selfWallMs`/`selfThreadCpuMs` subtract nested measured work on the same thread.
Parent and child rows overlap; do not add all inclusive rows into one total.
Offloaded worker rows are not subtracted from the calling thread's waiting time.

A high stage wall time and low calling-thread CPU can mean offloaded work or
waiting; compare it with the worker rows. Weak per-chunk tags associate actual
noise/biome work with DH even when Minecraft uses another executor. Tags are
removed in `finally`, and never change chunk state or generation scheduling.
Other mods' custom generator implementations may not enter Minecraft's native
noise-worker methods; the encompassing DH stage is still timed.

This profiles terrain preparation, not OpenGL draw time. It does not separately
measure every mod's feature or asynchronous database compression/write worker.
If all generation phases are cheap but request latency is high, queue/backpressure
and subsequent data pipelines need the next investigation. Rough height queries
sample a surface; full density filling computes a 3D volume and can be expensive
even when the rough terrain silhouette is cheap.

## Development verification

`-PdhClientSmoke -PchunkProfileSmoke` uses an isolated test executor to invoke the native DH generator
in a noise-world fixture (independent of gameplay queue gates), generates details 3/2/1 and a full detail-zero
FEATURES batch, and asserts real noise and chunk-to-LOD hooks were observed.
Use `-PcompatModsDir=...` for the full fixture's mods. The fixture is separate
from user saves, and its smoke classes are excluded from the production JAR.

## Local sanity check

A real noise-world fixture (DH/Sodium/Sable/Create/around_planet) exercised full
FEATURES generation as well as rough details 3/2/1. Actual DH worker noise filling
recorded 96 calls and about 2535 ms of CPU, compared with about 1044 ms for the
surface stage, 583 ms for biome workers, 320 ms for chunk-to-LOD conversion and
262 ms for features. These are overlapping/cold fixture aggregates, not per-chunk
benchmarks or a finding about the user's modpack. The main purpose is validation
that offloaded CPU is attributed to DH correctly. The calling noise stage itself
used only about 11 ms of CPU while waiting ~2608 ms, demonstrating why measuring
only the caller would be misleading.

The fixture uses an isolated executor so gameplay queue gates cannot block a
synchronous smoke callback. Production executors and queue gates are unchanged.
