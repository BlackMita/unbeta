# UNBETA — Master Plan

**Project:** Make Minecraft Java 1.20.1 (Fabric) play like Java **Beta 1.8** ("Adventure Update Part 1", Sept 2011), without breaking the ~30 coexisting Fabric mods it ships alongside — then build **Phase 2 ("Unbeta 1.7.3")**, a layer of original content, on top of that gate instead of fighting it. End goal: Unbeta is the centerpiece mod of a modpack bundling ~30 mods + 4 datapacks.

**What this document is:** the single source of truth and **handoff/catch-up brief.** If you are a fresh developer or a fresh LLM session with no memory of this project, read this top to bottom and you can continue the work. It reflects what has actually been **built and verified**, not just what was planned.

**Repo:** `github.com/BlackMita/unbeta` (branch `main`). Latest commit: `cf8a68a`.

---

## 0. How to use this document

If handing this to a collaborating LLM, the useful prompt shape is:

> "Read the attached Unbeta master plan. You are implementing `[feature]`. Follow the architecture in §3–4 and the mechanism table in §5. Do not invent new removal mechanisms. Verify every 1.20.1 API name against the user's jars (§9.2) before writing code. Output working files."

**Three rules are load-bearing and must never be silently broken:**

1. **Nothing is ever unregistered.** Content is *gated* (no recipe/loot/spawn/worldgen/creative entry), never deleted from registries. (§3.1)
2. **Every gate is namespace-scoped to `minecraft:`.** Never "remove all X except the beta list" — always "remove this explicit list of `minecraft:` IDs." This is what keeps ~30 other mods working. (§3.2)
3. **`unbeta-core` contains only gate machinery + vanilla rules. Phase 2 content lives in `unbeta-content`,** which talks to core through its public API / rule entrypoint. (§3.3)

Break any of these and Phase 2 becomes a rewrite instead of an addition.

---

## 1. Current status snapshot

**Phase 1: COMPLETE and verified.** A 1.20.1 Fabric client that plays like Beta 1.8 — beta-only creative menu, mobs, dimensions, systems, and worldgen — running cleanly alongside ~30 mods and 4 datapacks in a real Prism instance.

**Phase 2: IN PROGRESS.** Substantial content shipped. The lighting tech-tree is complete. Rail system buffed. Wood unification done. The architecture bet — "Phase 2 flips rules without editing Phase 1" — is thoroughly proven.

| Area | State |
|---|---|
| Rule engine + precedence + diagnostics (`/unbeta audit/why/rules`) | ✅ done |
| Recipe / loot / creative gates | ✅ done (~897 recipes removed) |
| Spawn gate (biome tables + entity-load catch-all) | ✅ done (16 b1.8 mobs remain) |
| Dimension gate (generic, parameterized) | ✅ done — End + Nether removed |
| System reverts (swimming, breeding, vine-climb, enchanting, brewing) | ✅ done |
| Worldgen: structure gating, feature gating | ✅ done (16 structures, ~40 features) |
| Terrain (Moderner Beta Beta-1.8 preset) | ✅ delegated, working |
| Compatibility (~30 mods + 4 datapacks, Prism) | ✅ verified |
| **Phase 2:** Nether removed / no daylight burn / obsidian fire | ✅ done |
| **Phase 2:** Persisted tick scheduler | ✅ done |
| **Phase 2:** Lighting tech-tree (torches, JoL, glowsand, glowstone, glow berries) | ✅ done |
| **Phase 2:** Wood unification + beta naming | ✅ done |
| **Phase 2:** Rail recipe buffs + minecart physics | ✅ done |
| **Phase 2:** Great Torch Replacement + Great JoL Replacement | ✅ done |
| **Phase 2:** everything else (see §7) | 📋 planned |

---

## 2. What Beta 1.8 was (the keep-list reference)

Released **Sept 14, 2011**. Everything from **1.0.0 onward is out of scope**. Full block/item/version tables live in the keep-list (`tools/b18_keeplist.json`) and manifest; the essentials:

**16 mobs (the entire keep roster):** Pig, Cow, Sheep, Chicken, Squid, Wolf, Zombie, Skeleton, Creeper, Spider, Cave Spider, Slime, Enderman, Silverfish, Ghast, Zombie Pigman.

**Also absent (removed):** breeding & baby animals, villagers/trading, iron/snow golems, mooshrooms, all fish mobs, bats, and everything from 1.0.0+.

**Biomes:** Forest, Plains, Desert, Swamp, Extreme Hills, Taiga, River, Ocean. **No snow/ice biomes.**

**The b1.8 Nether** was netherrack, soul sand, glowstone, lava, ghasts, zombie pigmen — nothing else. (Phase 2 removes the Nether entirely.)

Verification protocol: the keep-list is authoritative; when in doubt, check minecraft.wiki's Beta 1.8 tables. Never trust a remembered content list — generate from the running game (`/unbeta audit`).

---

## 3. The three load-bearing rules (detail)

### 3.1 Gate, never unregister
1.20.1 freezes registries after bootstrap. Instead every removed thing stays a valid ID but is unreachable via up to six layers: no recipe, no loot, no spawn, no worldgen, no creative entry, (optionally) no use. Removals are **reversible at runtime by config** — which is exactly what Phase 2 exploits.

### 3.2 Namespace scoping — the compatibility keystone
Every gate checks the ID's namespace and returns "allowed" for anything not in the gated set (default: only `minecraft`). **Never** an allowlist over all namespaces. Verified empirically across ~30 mods.

### 3.3 Two modules
`unbeta-core` = gate engine + vanilla rules. `unbeta-content` = Phase 2 content, depends on core, talks to it via the `unbeta:rules` entrypoint and public API. Content never mixins what core mixins.

---

## 4. Architecture as-built

### 4.1 The rule engine
Every behavior is a named rule: `<kind>/<namespace>.<path>`. **`true` = "Unbeta removed/disabled it."**

Precedence chain (later wins):
```
manifest.json  <  config/unbeta/core.json  <  mod overrides (unbeta:rules entrypoint)  <  config/unbeta/overrides.json
```

Public API: `UnbetaApi.rules().isRemoved(...)`, `.isDimensionRemoved(...)`, `.isSystemDisabled(...)`, `.isGatedNamespace(...)`, `.resolve(RuleKey)`.

### 4.2 The manifest (source of truth)
`core/src/main/resources/unbeta/manifest.json` — 2443 entries. **Generated, never hand-edited:** `python3 tools/generate_manifest.py <audit.csv>` inverts `tools/b18_keeplist.json` against a `/unbeta audit` registry dump.

### 4.3 Diagnostics
`/unbeta audit`, `/unbeta why <id>`, `/unbeta rules`, `/unbeta features`, `/unbeta reload`. CSVs land in `core/run/`.

### 4.4 Persisted tick scheduler (foundation service — BUILT)
Two subsystems in `core/.../sched/`:
- **Position callbacks** — `UnbetaScheduler.schedule(world, pos, ticks, handlerId)`. Stored in `ScheduledTaskState` (PersistentState). Survives save/load. Handler registered by string ID.
- **Item countdowns** — burnout timestamp stamped into ItemStack NBT (`NBT_BURNOUT_AT`). Checked each tick in player inventory + nearby containers. Fires handler with stack's current location.
- Both proven working including chest-bomb scenario and persist-across-reload.

---

## 5. Removal mechanisms — the decision table

One preferred mechanism per category. Do not improvise alternatives.

| Category | Mechanism |
|---|---|
| Recipes | Mixin `RecipeManager` apply-tail, rebuild set minus gated outputs |
| Loot | `LootTableEvents.REPLACE` (v2 — **5 params**), filtered by namespace |
| Mob spawns | `BiomeModifications` REMOVALS + `removeSpawnsOfEntityType` |
| Spawns that slip through | `ServerEntityEvents.ENTITY_LOAD` guard, `discard()` gated MobEntity |
| Structures | Override `data/minecraft/worldgen/structure_set/*.json` with empty `structures: []` |
| Worldgen features | `BiomeModifications` REMOVALS + `removeFeature(RegistryKey)` (wrap in try/catch) |
| Creative menu | `ItemGroupEvents.MODIFY_ENTRIES_ALL` → removeIf |
| Systems | `@Inject(at=HEAD/RETURN, cancellable=true)` mixin, reading a rule. **Never `@Overwrite`.** |
| Dimensions | Portal-traversal cancel + eviction + activator-item gating; **never delete the dimension** |
| Worldgen block swap | `ChunkRegion.setBlockState` mixin — intercepts during generation only, not runtime |

**Mixin hygiene:** `@Inject` HEAD/RETURN cancellable is the default; `@Overwrite` banned; never mixin rendering/chunk/lighting classes.

---

## 6. Deviations from the original plan (brisk)

- **Config is `.json`, not `.json5`.** No functional difference.
- **End removal (D5):** portal travel + eviction done, stronghold portal room still generates inert. Deferred.
- **Obsidian fire became a new block**, not a recolor.
- **`GeneratedBlockGate` shipped but DISABLED** — chunk-load scan froze world-saving.
- **Deepslate below y=0** persists — D1 accepted.
- **Glow berries:** Phase 1 gated them; Phase 2 re-enables via `ContentRules.java` (NOT keep-list).
- **Wood unification:** all logs → "Wood Planks" (oak). Distinct plank colors deferred to paintbrush item.
- **"Glowmud" renamed to "Glowsand"** during implementation. The planned feature is fully built under that name.

**Decisions still in force:** D1 (world height stays −64→320), D2 (1.9 combat → Nostalgic Tweaks), D3 (advancements deferred), D4 (empty b1.8 villages — still unbuilt), D5 (cosmetically incomplete), D6 (code generated for user).

---

## 7. Phase 2 — "Unbeta 1.7.3"

### 7.1 Done

- ✅ **Nether removed** — dimension gate + eviction; nether mobs/blocks gated.
- ✅ **No hostile daylight burning** — zombies + skeletons don't ignite in sun.
- ✅ **Obsidian fire** — permanent colored (5 colors), silent, smokeless. Obsidian-exclusive.
- ✅ **Persisted tick scheduler** — position callbacks + item countdowns, both survive save/load.
- ✅ **Wood unification** — all logs → "Wood Planks" (oak). Oak products renamed to beta "Wood/Wooden" names.
- ✅ **Rail system** — 32 rails / 16 powered rails per craft. Minecart physics: 1.3× speed, better turns/ascents, stronger boost, less friction.

### 7.2 The Lighting Tech-Tree ✅ COMPLETE

```
Glow Berry (held/dropped: dynamic light 12, food, stackable, caves)
    ↓
Unlit Torch (dynamic light 6, luminance 4 placed, stackable ×16, mines off slowly)
    ↓
Lit Torch (4min burnout, dynamic light 15, luminance 14, unstackable, burn bar, Great Replacement done)
    ↓
Unbeta Jack o'Lantern Lit (20min burnout, waterproof, dynamic light 15, luminance 15, unstackable, burn bar)
    ↓
Glowsand (permanent, gravity-affected, luminance 15, dynamic light 15 when dropped, smelts → Glowstone)
    ↓
Glowstone (permanent luminance 15, only dust source now Nether is gone)
```

**The two-item burnable pattern (canonical — reuse for all future burnable items):**
- Two registered items: UNLIT (stackable ×16, fixed model) and LIT (unstackable, burn bar, fixed model).
- Lit state = which item it IS, not NBT.
- NBT stores: `NBT_BURNOUT_AT` (absolute world-time deadline) + `NBT_FULL` (bar denominator).
- ONE RULE: lighting always gives a FULL burn; extinguishing discards remaining.
- Lighting = item swap (createLit/createUnlit helper methods).
- Place/mine carry burnoutAt verbatim — never re-anchor.
- Dropped expired items: **discard + respawn as new entity** (dynamic lights datapack caches score on entity ID; setStack doesn't reset it).

**Key interaction notes:**
- Submerged check: `!world.getFluidState(pos.up()).isEmpty()` = water directly above = submerged. Unlit JoL cannot be relit when submerged. Same check will gate Clambox operation.
- Dynamic lights: items registered in `data/dynamiclights/tags/items/mod_support/light_level/` tags. Our tags use `"required": false` so missing items don't crash.
- Glow berries re-enabled via `ContentRules.java` (not keep-list) + cave vine drops restored.
- Glowsand crafted: 4 sand + 5 glow berries (TNT checkerboard pattern).

**Great Replacements (both complete):**
- All worldgen/loot/recipe vanilla torches → Unbeta unlit torch. Vanilla torch renamed "Vanilla Torch."
- Vanilla JoL recipe → Unbeta JoL (lit or unlit based on torch used). Vanilla JoL renamed "Vanilla Jack o'Lantern."

### 7.3 The rest of the Phase 2 roadmap

**Abandoned rails (next worldgen feature):**
- **Tunnel run** (build first): straight 3×3 air tunnel, floor rail, dilapidation gaps, rare unlit wall torch. Custom `Feature` in `unbeta-content`.
- **Surface trail** (harder): terrain-following rail, up/down hills. Deferred until tunnel is proven.
- Hookshot = central loot for underground dungeons. Hover boots = central loot for floating island dungeons (both from existing mods in the modpack).

**Enderman rework (specced, not built):**
- Eyes + particles → blue-navy (particle swap mixin + texture edit).
- Remove water damage.
- Remove daylight burning (same mixin pattern as zombie/skeleton).
- Light-based protection: aggroed enderman teleports away (doesn't die) when in luminance ≥15. Makes lit areas the only safe zone — interacts with the lighting tech-tree.
- Ender pearl + dragon particles → same blue-navy recolor.

**Obsidian (finish the set):**
- Blast immunity; portal-disable; mining rules (cross-dep on class system).

**Mobs:**
- No mob spawners (rule flip).
- Zombie regen + Husk/Drowned/Frozen variants (Frozen conflicts with no-snow-biomes).
- Skeleton bone piles (scheduler ready).
- Creeper inventory bomb (needs PlayerDataService).
- New mobs: Siren, Will o' Wisp, Genius.

**World:**
- Dungeons replace strongholds. Player builds structures with `/structure save`; assistant wires into jigsaw worldgen.

**Items/systems:**
- **Clamboxes** — underwater-only furnace, operates only when `getFluidState(pos.up())` is non-empty (no fuel needed, just submersion).
- **Paintbrush** — recolors wood, restores visual spruce/birch variety.
- **Tree of Life / Life Blocks** (needs `UnbetaWorldState`).
- **Class system + Flint tools** (needs `PlayerDataService`).
- **XP orbs → lore splashscreens** (ties to D3).
- **Torch pass 2:** gravel-lights, dirt-extinguishes, enemy-ignite weapon, wicking.

---

## 8. Foundation services

| Service | State | Unlocks |
|---|---|---|
| Persisted tick scheduler | ✅ BUILT | Torches ✅, JoL ✅, creeper bombs, bone piles |
| `PlayerDataService` (per-player NBT) | 📋 not built | Class system, Flint tools, creeper-bomb tracking |
| `UnbetaWorldState` (cross-chunk persistent) | 📋 not built | Life Blocks, Tree of Life |

---

## 9. Dev environment & workflow

### 9.1 Environment
```
Project:   /home/blackmita/Desktop/Minecraft-mod-dev/unbeta   (clean, GitHub-tracked)
OS:        Ubuntu 24.04 / Linux Mint
Java:      OpenJDK 21 (compiles to release 17 — this is correct, don't "fix")
Gradle:    wrapper 8.14.4;  Loom 1.10.5
Minecraft: 1.20.1;  Yarn 1.20.1+build.10;  Loader 0.15.11 (dev);  Fabric API 0.92.2
```
Real play/testing: **Prism Launcher**, 1.20.1 Fabric instance (loader 0.19.3 fine — jars require ≥0.15).

### 9.2 Build / run / deliver
- Build: `./gradlew build`
- Dev client: `./gradlew :core:runClient`
- Sources jar: `./gradlew :core:genSources` → `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/…-sources.jar`
- Distributable: `core/build/libs/unbeta-core-0.1.0.jar` + `content/build/libs/unbeta-content-0.1.0.jar`
- Copy to Desktop: `cp core/build/libs/unbeta-core-0.1.0.jar content/build/libs/unbeta-content-0.1.0.jar ~/Desktop/`
- **Single-file edits: use paste-in python snippet, NOT a zip** — zips clobber hand-edits.
- **Datapacks: test in Prism only** — dev client never loads world datapacks (known harness quirk).

---

## 10. Working practices (hard-won — do not skip)

- **⚠️ NEVER trust a remembered 1.20.1 API name.** Always verify via sources jar or build + let compiler fail. Key past catches: `LootContextParameterSet.Builder` not `LootContext.Builder`; `getOptional` not `getNullable`; `VerticallyAttachableBlockItem` not `WallStandingBlockItem`; `net.minecraft.block.BlockEntityProvider` not `block.entity`; `CarvedPumpkinBlock` is the jack o'lantern base class; `getDefaultState()` is final in Block — use `setDefaultState()` in constructor instead.
- **Generate lists from the running game, never from memory.**
- **Make gates fail soft** — wrap per-item work in try/catch.
- **Don't scan blocks on chunk load** — froze world-saving twice.
- **`overrides.json` is HIGHEST precedence** — use `/unbeta why` to debug silent rule conflicts.
- **Commit at every known-good state.**
- **Two-item pattern for burnable items** — see §7.2. Lit and unlit are separate registered items.
- **Dropped expired items: discard + respawn** — never `setStack()` on a dynamic-lights-scored entity.
- **Submerged check:** `!world.getFluidState(pos.up()).isEmpty()` = water directly above.
- **`@ModifyConstant` for tuning numeric constants** (used successfully for minecart physics).

---

## 11. Known cosmetic quirks (not bugs)

- **Audible-but-invisible gated mobs** — sound fires the tick before discard. Harmless.
- **Lush caves generate** — gated drops/creative, now serve as glow berry source. Intentional.
- **Deepslate below y=0** — D1 accepted.
- **Obsidian-fire textures procedural** — swappable pure asset.
- **Empty villages use 1.14 blocks** — D4 unbuilt, largest Phase 1 gap remaining.
- **Torch/JoL placeholder art** — swappable, no code change.
- **Torch pass-2 interactions unbuilt** — gravel lights, dirt extinguishes, enemy ignite, wicking. Deferred.

---

## Appendix — one-paragraph brief for a collaborating LLM

> `unbeta-core` is a Fabric mod for Minecraft 1.20.1 (Java 17, Yarn mappings, Fabric API 0.92.2, Loom 1.10.5) that makes vanilla 1.20.1 present only content that existed in Java Beta 1.8, while leaving all non-`minecraft` namespace content from ~30 other mods untouched. Content is never unregistered — it is gated via named rules resolved through a precedence chain (manifest → core config → mod overrides → user overrides), so `unbeta-content` (Phase 2) flips any rule without editing core. All mixins are cancellable `@Inject` guarded by a rule check; `@Overwrite` banned; rendering/chunk/lighting classes off-limits. Phase 1 is done and verified compatible with ~30 mods + 4 datapacks in Prism. Phase 2 has shipped: Nether removal, no daylight burn, obsidian fire, a full lighting tech-tree (torches, jack o'lanterns, glowsand/glowstone, glow berries — all using the two-item burnable pattern), wood unification, rail buffs, and minecart physics tuning. The persisted tick scheduler (position callbacks + item countdowns) is built and underpins all timed features. Next up: abandoned tunnel worldgen, enderman visual/behavior rework. Always verify 1.20.1 API names against the user's decompiled sources jar before writing code — every feature so far had at least one wrong-name guess.
