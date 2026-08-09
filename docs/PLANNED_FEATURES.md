# UNBETA — Planned Features (living document)

Updated 2026-08-09 to reflect all Phase 2 work completed to date.

**Status legend:** ✅ done · 🔨 in progress · 📋 planned · 💭 brainstorm (not fully specced)

---

## Phase 2 — Done

- ✅ **Nether removed** — dimension gate + eviction. Nether mobs + blocks gated.
- ✅ **No hostile daylight burning** — zombies and skeletons don't ignite in sun.
- ✅ **Obsidian fire** — permanent colored (5 colors), silent, smokeless. Obsidian-exclusive.
- ✅ **Persisted tick scheduler** — position callbacks + item countdowns. Survives save/load. Underpins all timed features. Proven with chest-bomb scenario.
- ✅ **Wood unification** — spruce/birch/oak logs all craft "Wood Planks" (oak). Oak products renamed to "Wood/Wooden" beta names. Paintbrush item will restore visual variety later.
- ✅ **Rail system** — 32 rails / 16 powered rails per craft. Minecart physics tuned: 1.3× speed, better turns/ascents, stronger boost, less friction.
- ✅ **Glow berries re-enabled** — cave vine drops restored, dynamic light level 12 held/dropped. Re-enabled via `ContentRules.java` (NOT keep-list), preserving Phase 1 accuracy.
- ✅ **Glowsand** — luminous gravity block (luminance 15), crafted from 4 sand + 5 glow berries (TNT checkerboard). Dynamic light level 15 when dropped. Smelts → Glowstone. Glowstone dust = only source now Nether is gone.

### The Lighting Tech-Tree ✅ COMPLETE

```
Glow Berry (dyn. light 12, food, stackable, from lush cave ceilings)
    ↓
Unlit Torch (dyn. light 6, luminance 4, stackable ×16, mines off wall slowly)
    ↓
Lit Torch (4min burnout, dyn. light 15, luminance 14, unstackable, burn bar)
    ↓
Unbeta Jack o'Lantern Lit (20min burnout, waterproof, dyn. light 15, luminance 15, unstackable, burn bar)
    ↓
Glowsand (permanent, gravity, luminance 15, dyn. light 15 when dropped, smelts → Glowstone)
    ↓
Glowstone (permanent luminance 15, breaks into glowstone dust — only dust source)
```

**Torches (✅ complete):**
- Two items: `unbeta:torch` (unlit, stackable ×16) and `unbeta:lit_torch` (lit, unstackable, burn bar).
- Crafted unlit (stick + coal/charcoal). Light: flint & steel on placed unlit; right-click lit torch on unlit; fire block; dual-wield. Extinguish: right-click lit with anything except flint or held unlit. Mines off wall (not instant hand-slap).
- Burns out → goes unlit (reusable forever, relight = full burn).
- Fluid contact extinguishes placed torches.
- **Great Torch Replacement:** all worldgen torches (ChunkRegionTorchMixin) + chest loot (mineshaft, savanna village) replaced with unlit Unbeta torch. Vanilla torch renamed "Vanilla Torch."
- **Pass 2 interactions (deferred):** gravel lights, dirt extinguishes, enemy-ignite weapon, wicking (stick extends life).

**Unbeta Jack o'Lanterns (✅ complete):**
- Two items: `unbeta:jack_o_lantern` (unlit, stackable ×16) and `unbeta:lit_jack_o_lantern` (lit, unstackable, burn bar).
- Crafted: carved pumpkin + unlit torch → unlit JoL; carved pumpkin + lit torch → lit JoL (state transfers).
- Burns out in 24000 ticks (1 full Minecraft day+night) → goes unlit. Relight = full burn.
- Waterproof: stays lit underwater. Cannot be relit when submerged (water directly above).
- **Great JoL Replacement:** vanilla JoL recipe overridden; zero worldgen/loot instances found. Vanilla JoL renamed "Vanilla Jack o'Lantern."

**Two-item burnable pattern (canonical template for future items):**
Lit state = which item it IS (not NBT). NBT stores `NBT_BURNOUT_AT` (absolute deadline) + `NBT_FULL` (bar denominator). ONE RULE: relight = full burn, extinguish discards remaining. Dropped expired items: discard + respawn as new entity (dynamic lights datapack caches score on entity ID). Submerged check: `!world.getFluidState(pos.up()).isEmpty()`.

---

## Remaining Phase 2 Roadmap

### 🔨 Next up: Abandoned Rails (worldgen)

Two distinct rail types, build in this order:

**Tunnel run (build first — simpler):**
- Straight 3×3 air tunnel carved through terrain, running for 50–200 blocks.
- Rail along the floor with dilapidation gaps (10–15% chance to skip a rail).
- Rare unlit Unbeta wall torch on tunnel walls (maybe 5% of blocks).
- Tunnel is defined by its air, not its decoration.
- More common than villages or dungeons.

**Surface trail (deferred — harder):**
- Rails that wind along terrain surface, following hills up and down.
- Variable length, dilapidated, torches along them irregularly.
- Requires a custom Feature that pathfinds along the surface Y level.

**Dungeon loot ties:**
- **Hookshot** (from modpack) = central reward for underground dungeons.
- **Hover boots** (from modpack) = central reward for floating island dungeons.

### 📋 Enderman Visual + Behavior Rework

- Eyes + idle/teleport particles → **blue-navy** (not purple — End is gone, purple feels wrong).
- Ender pearl trail + ender dragon particles → same blue-navy recolor.
- Remove water damage.
- Remove daylight burning (same mixin pattern as zombie/skeleton).
- **Light-based protection:** aggroed enderman teleports AWAY (not damaged) when in luminance ≥15. Makes lit areas the only safe zone. Interacts directly with the lighting tech-tree — torches/JoL/glowsand become defensive tools.

### 📋 Obsidian (finish the set)
- Blast immunity.
- Portal-disable (moot but completes the vision).
- Mining rules: requires Diamond Pick OR class-item "Miner's Flint Pick" for a drop. **Cross-dependency on class system.**

### 📋 Mobs (rewrites & additions)
- **No mob spawners** — rule flip.
- **Zombie regen + variants** — Husk / Drowned / Frozen. **Flagged conflict:** no snow biomes in b1.8; Frozen variant needs resolution.
- **Skeleton bone piles** — re-animates after delay via scheduler (scheduler is ready).
- **All spiders → cave spiders + wall crawl** — `nyfsspiders` mod may cover crawling.
- **Creeper inventory bomb** — countdown on a held item (scheduler ready, needs `PlayerDataService`).
- **New mobs:** Siren, Will o' Wisp, Genius.

### 📋 Dimensions / World
- **Dungeons replace strongholds** — custom jigsaw or repurposed `StrongholdGenerator`. Player builds structures with `/structure save`; assistant wires into worldgen.
- **Floating island dungeons** (above ground) — more complex custom structure code.

### 📋 Items / Blocks / Systems
- **Clamboxes** — furnace that operates *only when submerged* (water directly above, no fuel needed). Uses same `getFluidState(pos.up())` submerged check as jack o'lanterns. "Cooks" pearls into something useful.
- **Paintbrush** — recolors/paints wood blocks, restoring visual variety of spruce/birch without requiring separate plank types.
- **Tree of Life / Life Blocks** — teleporting, indestructible-except-by-Wand connected structures. Hardest item in the vision. Needs `UnbetaWorldState`.
- **Origins-style class system** at world creation + **Flint tools**. Needs `PlayerDataService`.
- **XP orbs → lore splashscreens** (ties to deferred D3 — advancements→achievements).
- **Torch pass 2 interactions** — gravel lights, dirt extinguishes, enemy-ignite weapon, wicking (stick extends torch life, stick gets shorter).

---

## Foundation Services

| Service | State | Unlocks |
|---|---|---|
| Persisted tick scheduler | ✅ BUILT | Torches ✅, JoL ✅, creeper bombs, bone piles |
| `PlayerDataService` (per-player NBT) | 📋 not built | Class system, Flint tools, creeper-bomb tracking |
| `UnbetaWorldState` (cross-chunk persistent) | 📋 not built | Life Blocks (Wand UUID ↔ Life Block set) |

---

## Known Cosmetic Quirks (not bugs, low priority)

- **Audible-but-invisible gated mobs** — sound fires the tick before discard. Harmless.
- **Lush caves generate** — now serve as glow berry source. Intentional.
- **Deepslate below y=0** — D1 accepted.
- **Obsidian-fire textures are procedural placeholders** — pure asset swap to improve.
- **Empty villages use 1.14 blocks** — D4 (custom b1.8 village templates) still unbuilt. Largest Phase 1 gap.
- **Torch/JoL textures are placeholder art** — swappable, no code change.

---

## Compatibility Status (verified 2026-08-02, Prism)

- ✅ ~30 Fabric mods run alongside Unbeta — nothing crashes.
- ✅ 4 datapacks (Attrition, daycount, dynamiclights, triggerVarnish) load and coexist.
- ✅ Namespace scoping verified — gates leave all non-`minecraft` content untouched.
- ⚠️ Dev client (`runClient`) does NOT load world datapacks. Use Prism for all datapack testing. Known harness limitation — do not re-investigate.
