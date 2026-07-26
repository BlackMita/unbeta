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
