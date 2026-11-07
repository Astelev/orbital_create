# Coarse coverage starvation and obsolete requests (1.1.4)

The submitted trace repeatedly reaches `progressive[pending/stages]=128/...`.
At 21:31:36–21:31:46 Moscow time, DH has 86 waiting tasks but zero active ones;
its generation gate is allowed while cruise refinement is parked. The logical
request limit prevented the renderer from submitting more positions, including
new coarse coverage. This is an admission deadlock, not evidence of slow GPU
rendering. The trace also contains camera-speed spikes while player movement is
small, which can restart the refinement quiet-time policy.

## Bounded reserve

Only when normal admission is blocked (or the deferred logical pool is full),
a separate coverage lane can submit coarse sources. It uses at most eight pending
sources per provider, checks at most four candidates per 250 ms pulse, and runs
on the **existing native World Gen Queue worker**. No generation threads are
added. Native updater/cache backpressure, world-generation bounds, world topology
and worker limits still apply. Only parked refinement is excluded from the
waiting-count check for this reserve. Ordinary fine admission remains unchanged.

Nearby sources start at initialDataDetail; progressively larger sources cover
outer rings up to the current client DH view radius. On a server without a client
view object the configured preview radius is the limit. Reserved sources retain
coarse priority even beyond the near-preview band. Tiny periodic worlds reduce
the starting detail so sources fit the canonical shores. Completed publication
keys are cached (512 maximum) to avoid re-requesting sources before native DB
updates become visible. No pooled terrain data is retained by this cache.

The lane does not keep generating extra coverage during ordinary healthy queue
admission. Existing reserve work is maintained/pruned until it completes.
A 200 ms wall-time camera motion sample prevents a short correction after several
client ticks at one rendered pose from producing an artificial fast-flight spike.
The renderer's native distance/detail selection is unchanged.

## What can leave the queue

DH's queue is a set of requests keyed by section position, not strict FIFO. Queue
selection considers coarse tiers, distance and Cruising's travel priority.
Native quadtree movement removes requests for outgoing regions and can prune an
overfull waiting queue. Ordinary native pruning removes **waiting** tasks; it
does not interrupt tasks already executing.

The progressive coordinator now counts consumers of every requested parent and
fine leaf. Cancellation of an obsolete logical request releases those references.
When the last consumer leaves, its orphan waiting stages can also be removed.
Other logical siblings, direct native consumers and reserve coverage consumers
are protected. A stage already running is allowed to finish, and its result can
still enter DH's cache. Completed publication metadata remains reusable.

This cancels unnecessary **work**, not saved LODs or world chunks. It does not
clear the DH database or invalidate existing GPU geometry. `queuedChunks` is an
estimate of area represented by tasks; a coarse source covering many chunk-sized
areas does not mean all of those are full Minecraft chunks being loaded.

Diagnostics add `preview=N` to the provider snapshot, plus `preview.submitted`,
`preview.completed`, `preview.cancelled_or_failed` and `preview.error` counters.
Normal cancellation is separated as `progressive.cancelled`; actual exceptional
failures retain `progressive.failed`. Queue-to-result times still include waiting.

## Validation

- 54 unit tests include shared-parent cancellation, published coverage reuse,
  periodic bounds and camera sampling without false speed spikes.
- 9 Sable/Create/Aeronautics GameTests preserve the staff world-frame fix,
  connected sublevels, altitude safety and movement behavior.
- Native DH client tests fill all 128 deferred slots and model 10000 parked native
  tasks. New coarse sources are still admitted and published, stay bounded at
  eight, and reach an outer view ring. Direct native consumers of a shared parent
  survive cancellation of its logical child.
- Actual native queue checks confirm use of its queue worker, eligible versus
  parked counts, priority for far coverage, pause/resume without discarding fine
  jobs, and that ordinary pruning does not cancel an in-progress task.
- Native quadtree detail checks remain unchanged in flight. Both standalone DH
  and paired around_planet/Sodium/Sable/Create/Biome API runtimes are exercised.

User modpack generation time still depends on CPU/worldgen and existing running
work. The patch addresses starvation and cancellation bookkeeping; it does not
claim a measured reduction in full generation time on the user's machine.
