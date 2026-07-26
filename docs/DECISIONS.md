# Decision log

## 2026-07-26 — Phase 1 scope locked

| # | Decision | Rationale |
|---|---|---|
| D1 | World height stays −64→320. No clamping to 0–128. | Highest-risk compat change for a 24-mod pack, lowest gameplay payoff. Closed, not deferred. |
| D2 | 1.9 combat revert delegated to Nostalgic Tweaks. | It already ships old combat mechanics. Duplicating it buys a mixin conflict. Core ships the rule key only. |
| D3 | Advancements → old achievements deferred to Phase 2. | Phase 2 replaces achievement notifications with XP-orb lore splashscreens anyway. Building a throwaway UI twice is waste. |
| D4 | Empty b1.8-style villages are in Phase 1 scope. | 1.20.1 villages are jigsaw structures full of 1.14 blocks. A gate cannot fix this; a custom template set is required. Largest single Phase 1 task. |
| D5 | The End is removed hard, not made inert. | Stronghold portal rooms must generate without frames. Requires a `StrongholdGenerator` mixin. "Decorative ruins" explicitly rejected. |
| D6 | Code is generated for the user rather than specified. | Deliverables are working files. Every file must be marked compile-verified or not. |

### Consequences for the manifest

- `unbeta:world_height_clamp` → `keep` (1.20.1 behaviour retained)
- `unbeta:attack_cooldown`, `unbeta:sweep_attack`, `unbeta:advancements` → `defer`
- `minecraft:the_end` → `remove`, mechanisms include `stronghold_portal_room_mixin`
- `minecraft:village_savanna` / `_snowy` / `_taiga` → `remove` (b1.8 has plains and desert only)

### Open, still needs a call

- **Frozen Zombies vs. snow.** Beta 1.8 removed snowy biomes entirely; they returned in 1.0.0.
  Phase 2 wants mobs tied to "biomes that snow". Pick one before Phase 2 mob work:
  (a) allow 1.0-era snowy biomes as an exception, (b) re-enable them in Moderner Beta's preset,
  (c) tie the mob to snow-layer presence or altitude instead of biome.

## 2026-07-26 — Generated-block gate disabled (lush caves deferred)

**Decision:** `replaceGeneratedGatedBlocks` defaults to **false**. The generated-block
gate (chunk-load scan that swaps gated blocks to stone) is shipped but off.

**Why:** Scanning blocks on every chunk load — even with a section palette pre-check —
stalled the server thread badly enough to freeze world saving twice during testing.
It also turned gated tall plants (tall_grass, sunflower, lilac, peony, rose_bush — all
correctly 1.7+ removals) into ugly stone stubs rather than removing them cleanly.

**Consequence:** Lush-cave vegetation (moss, glow berries, azalea) still generates
underground. Those blocks remain gated at the item/recipe/loot/creative level, so they
are unobtainable and drop nothing — they are just visible in the wild. Accepted as a
known Phase 1 limitation.

**Phase 2 plan:** redo this at world-generation time (a mixin on chunk generation, or a
custom biome-source that never injects lush caves), where the swap is free instead of a
per-load scan. At that point gated non-solid blocks (plants) should become AIR, and
gated solid blocks (deepslate/sculk) should become STONE — the stone-stub bug was from
treating all gated blocks the same.

**Manifest note:** the plant removals are CORRECT. Beta 1.8 had 1-block grass, fern,
dandelion and poppy (all kept). The 2-block tall_grass, large_fern, and the tall
flowers (sunflower/lilac/peony/rose_bush) are all 1.7 additions and are correctly
removed — they were only ugly because of the stone swap, not because the gate was wrong.
