# Descending ships retain real terrain — 1.1.5-diagnostic

A ship above the high-altitude threshold used to suppress terrain requests even
while descending after an Around Planet reentry. This could replace newly loaded
real player chunks with temporary air chunks while native physics also skipped
terrain tickets.

Before either exemption is applied, Cruising now reads the connected assembly's
mass-weighted mean native vertical linear velocity. Negative velocity ends the
exemption immediately; a 0.0001 blocks/s tolerance handles numerical rest. A
player walking inside the ship or a rotor's individual movement does not define
the assembly's motion. Invalid native data conservatively keeps ordinary loading.
The decision is live and has no persistent latch; ascent and hover are eligible
again as soon as descent stops. Passenger/constraint protection remains active
while normal Sable terrain loading resumes. No server config change is required.

Around Planet space cells keep their existing explicit exclusion. For atmospheric
return use Around Planet 0.11.0-alpha.8, whose default ascent threshold is 3000,
with atmospheric braking before native handoff.

Verified: 55 unit tests; 2 standalone server tests; 9 native server tests with
Sable/Create/Aeronautics, including actual terrain loading under a high descending
ship with a native world-frame joint. Connected rotary-child and staff-lock
regressions remain included. Combined Around Planet client checks use Sodium,
Distant Horizons and Simple Clouds and verify real chunks on seated descent.
