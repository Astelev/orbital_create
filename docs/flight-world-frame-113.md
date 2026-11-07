# Staff world-frame joints and sky eligibility (1.1.3)

The follow-up log establishes the failure directly: at a hull minimum around
Y435, with core enable=true, threshold=356 and protocol channel=true, the single
ship is rejected with assemblyMinY=-Infinity / externalAnchor=true. The earlier
camera adaptation flag and generation-throttle change did not diagnose this
server-side failure.

ConnectedFlightAssembly treated a null endpoint of any native constraint as an
unknown physical anchor, invalidating the assembly's altitude. Inspection of
Simulated 1.3.2 (bundled with Aeronautics 1.3.2) confirms the staff's drag session
uses addConstraint(null, ship, FreeConstraintConfiguration(...)); its lock uses
the same endpoint pattern with FixedConstraintConfiguration. Null is a world-space
joint frame, not another physical hull. Treating it as unknown terrain prevented
sky eligibility while the staff control/lock existed.

Null-ended links are now excluded from physical-body graph traversal. They do
not merge otherwise unrelated ships, and their native handles/motors are not
modified or removed. All real linked sublevels still contribute their live hull
minimum; real unsupported physical bodies still disqualify eligibility. The
altitude/build-height/collision margin checks are unchanged. Diagnostic output
adds worldFrameJoints to distinguish mathematical world frames from unknown
physical bodies.

Regression coverage uses native Sable Free and Fixed constraints with the same
null-first pattern as the staff: a high occupied ship stays eligible without
loading the ground, while lowering it immediately disables the exemption. Existing
connected rotary/sublevel tests remain. The real network-player fixture now has
a native staff-style Free joint, checks the temporary client view and player
ticket suppression, and moves through three fresh columns without generating
terrain. No reproduction of the entire user modpack is claimed.

Validation: all 48 unit tests and all 9 native GameTests pass with the full
Sable/Create/Aeronautics integration runtime. The real-client fixture records
worldFrameJoints=1, externalAnchor=false and skyMode=true, then passes movement
without loading the ground. Both native Free drag and Fixed lock forms are
covered; lowering the hull still disables the terrain exemption.
