Unbeta structure gating
=======================

These files OVERRIDE vanilla structure sets. This is the one place unbeta-core
deliberately overwrites minecraft: namespace data rather than composing with it -
it is the approved mechanism from MASTER_PLAN section 5 for structures, because
there is no event-based way to disable a structure set.

Important: structure SETS are not structures. One set can hold several structures
(minecraft:villages holds all five village types), which is why these files do not
map one-to-one onto the manifest's per-structure entries.

Untouched on purpose:
  minecraft:mineshafts  - Beta 1.8 has mineshafts (they were added in Beta 1.8 itself)
  minecraft:strongholds - Beta 1.8 has strongholds, without an End portal room.
                          The portal room still generates but is inert. Stripping the
                          frames is D5 layer 4, deliberately deferred.

Compatibility note: if another mod also overrides these files, last-loaded wins.
Moderner Beta controls terrain and biome placement, not structure sets, so the two
compose cleanly.
