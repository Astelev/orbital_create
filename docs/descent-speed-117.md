# Descent speed threshold — 1.1.7-diagnostic

High-altitude terrain exemptions now remain active during slow descent. Both
player temporary chunks and Sable terrain-ticket suppression use the connected
assembly's mass-weighted native vertical velocity in blocks/second. With the
default limit, speeds down to and including -40 remain eligible; below -40 normal
terrain loading resumes immediately. Slowing to the limit restores eligibility
without a latch. The whole hull must still clear the existing altitude threshold.

The server/world config adds `highAltitudeFlight.maxDescentSpeed = 40.0`, adjustable
from 0 to 10000. Existing saves get the default automatically. Around Planet's
explicit space-cell exclusions remain compatible. No change to native physics,
ship velocity, orbital mechanics or DH generation is included.

Regression coverage includes the exact boundary, invalid data, a custom limit,
and a real Sable ship under a world-frame joint: descent at 39 blocks/s must not
generate terrain, descent at 41 must restore real terrain, and stopping must
restore cruise eligibility.
