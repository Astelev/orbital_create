# Occupied holding-column retirement — 1.1.6-diagnostic

Sable has two distinct unload decisions: physics ticket updates and retirement
of the native holding column when an ordinary terrain chunk unloads. Earlier
Cruising crew/rotary protection covered the first path only. Temporary high
flight can retire ordinary columns under an occupied ship, so the second path
could serialize/remove it and interrupt the native seat.

During `SubLevelHoldingChunkMap.processUnload`, treat an occupied high connected
assembly as protected in the existing force-loaded-membership decision. This
adds no actual load ticket and does not pin terrain. Native seat roots count via
their plot, even before entity tracking catches up. Standing/jumping crew and
connected rotary children count as before. Descent still resumes real terrain
loading; this guard only prevents the occupied body from being retired during
that transition. Empty ships unload normally and crew departure releases the
guard immediately.

Diagnostics remain enabled. `CRUISING-FLIGHT` adds `holdingProtected`, `mounted`
and `vehicle`. `CRUISING-NATIVE-REMOVE` observes actual client/server native
removals, with ship UUID, altitude and reason, bounded to eight per level/side
per 100 ticks. It never blocks ordinary removal.

Validation: 55 unit tests; 10 native Sable/Create/Aeronautics tests; 2 standalone
server tests. A real holding-column retirement at Y=1500 keeps an occupied
rotary group and a Create seat with its rider, but retires a nearby empty body.
It also verifies no new terrain/load tickets and normal retirement after the
crew leaves. FakePlayer intentionally refuses riding, so this server fixture
uses standing player crew and a native seated pig. A combined Around Planet
alpha.9 client fixture separately exercises a real seated network player through
the same retirement path, then ascent, space visuals and atmospheric return.
