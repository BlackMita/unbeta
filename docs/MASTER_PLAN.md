# UNBETA — Master Plan

**Project:** Make Minecraft Java 1.20.1 (Fabric) play like Java **Beta 1.8** ("Adventure Update Part 1", Sept 2011), without breaking the ~30 coexisting Fabric mods it ships alongside — then build **Phase 2 ("Unbeta 1.7.3")**, a layer of original content, on top of that gate instead of fighting it. End goal: Unbeta is the centerpiece mod of a modpack bundling ~30 mods + 4 datapacks.

**What this document is:** the single source of truth and **handoff/catch-up brief.** If you are a fresh developer or a fresh LLM session with no memory of this project, read this top to bottom and you can continue the work. It reflects what has actually been **built and verified**, not just what was planned.

**Repo:** `github.com/BlackMita/unbeta` (branch `main`).

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

**Phase 2: IN PROGRESS.** Three features shipped (Nether removal, no daylight burning, obsidian fire). The architecture bet — "Phase 2 flips rules without editing Phase 1" — is proven working.

| Area | State |
|---|---|
| Rule engine + precedence + diagnostics (`/unbeta audit/why/rules`) | ✅ done |
| Recipe / loot / creative gates | ✅ done (~897 recipes removed) |
| Spawn gate (biome tables + entity-load catch-all) | ✅ done (16 b1.8 mobs remain) |
| Dimension gate (generic, parameterized) | ✅ done — End removed, Nether then removed in Phase 2 |
| System reverts (swimming, breeding, vine-climb, enchanting, brewing) | ✅ done |
| Worldgen: structure gating, feature gating | ✅ done (16 structures, ~40 features) |
| Terrain (Moderner Beta Beta-1.8 preset) | ✅ delegated, working |
| Compatibility (~30 mods + 4 datapacks, Prism) | ✅ verified, namespace scoping proven empirically |
| **Phase 2:** Nether removed / no daylight burn / obsidian fire | ✅ done |
| **Phase 2:** everything else (see §7) | 📋 planned |

---

## 2. What Beta 1.8 was (the keep-list reference)

Released **Sept 14, 2011**. Everything from **1.0.0 onward is out of scope**. Full block/item/version tables live in the keep-list (`tools/b18_keeplist.json`) and manifest; the essentials:

**16 mobs (the entire keep roster):** Pig, Cow, Sheep, Chicken, Squid, Wolf, Zombie, Skeleton, Creeper, Spider, Cave Spider, Slime, Enderman, Silverfish, Ghast, Zombie Pigman. (Vanilla 1.20.1 has ~80 mob types — the gap is the bulk of the spawn-gating work.)

**Also absent (removed):** breeding & baby animals, villagers/trading, iron/snow golems, mooshrooms, all fish mobs, bats, and everything from 1.0.0+.

**Biomes:** Forest, Plains, Desert, Swamp, Extreme Hills, Taiga, River, Ocean. **No snow/ice biomes** (removed in b1.8, returned in 1.0.0) — flagged as a Phase 2 conflict for "Frozen Zombies," see §7.

**The b1.8 Nether** was netherrack, soul sand, glowstone, lava, ghasts, zombie pigmen — nothing else. (Phase 2 removes the Nether entirely; see §7.)

Verification protocol: the keep-list is authoritative; when in doubt, check minecraft.wiki's Beta 1.8 tables. Never trust a remembered content list — generate from the running game (`/unbeta audit`).

---

## 3. The three load-bearing rules (detail)

### 3.1 Gate, never unregister
1.20.1 freezes registries after bootstrap; they're synced to clients and referenced by saved chunks and every mod. Deleting `minecraft:villager` crashes. Instead every removed thing stays a valid ID but is unreachable via up to six layers: no recipe, no loot, no spawn, no worldgen, no creative entry, (optionally) no use. Most things need 2–3. Removals are **reversible at runtime by config** — which is exactly what Phase 2 exploits.

### 3.2 Namespace scoping — the compatibility keystone
Every gate checks the ID's namespace and returns "allowed" for anything not in the gated set (default: only `minecraft`). **Never** an allowlist over all namespaces. Verified empirically: after loading ~30 mods, `/unbeta audit` showed every `cloudboots:`, `immersive_paintings:`, `moderner_beta:` entry as `removed=false`. This is *the* property that makes the whole project viable.

### 3.3 Two modules
`unbeta-core` = gate engine + vanilla rules. `unbeta-content` = Phase 2 content, depends on core, talks to it via the `unbeta:rules` entrypoint and public API. Content never mixins what core mixins.

---

## 4. Architecture as-built

### 4.1 The rule engine
Every behavior is a named rule: `<kind>/<namespace>.<path>` (e.g. `entity/minecraft.villager`, `system/unbeta.enchanting`, `dimension/minecraft.the_nether`). **`true` = "Unbeta removed/disabled it."** For additive Phase 2 features the same boolean is read as an on-switch (documented at each such mixin).

Precedence chain (later wins):
```
manifest.json  <  config/unbeta/core.json  <  mod overrides (unbeta:rules entrypoint)  <  config/unbeta/overrides.json
```
Phase 2 removes the Nether with one line in `content/.../ContentRules.java`: `ctx.set(RuleKey.of(ContentKind.DIMENSION, new Identifier("minecraft","the_nether")), true)`. No core edits. **This is proven working.**

Public API (semver'd, Phase 2 depends on it): `UnbetaApi.rules().isRemoved(...)`, `.isDimensionRemoved(...)`, `.isSystemDisabled(...)`, `.isGatedNamespace(...)`, `.resolve(RuleKey)` (provenance, powers `/unbeta why`).

### 4.2 The manifest (source of truth)
`core/src/main/resources/unbeta/manifest.json` — 2443 entries. **Generated, never hand-edited:** `python3 tools/generate_manifest.py <audit.csv>` inverts `tools/b18_keeplist.json` against a `/unbeta audit` registry dump. To change what's gated, edit the keep-list and regenerate. The inversion emits explicit `minecraft:` IDs, preserving rule #2.

### 4.3 Diagnostics (build habits — they solved most bugs)
`/unbeta audit` (dumps all registered content + gate status to CSV), `/unbeta why <id>` (provenance — which precedence layer won), `/unbeta rules`, `/unbeta features` (dumps placed-feature IDs), `/unbeta reload` (re-reads overrides.json only). CSVs land in `core/run/`.

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
| Worldgen features | `BiomeModifications` REMOVALS + `removeFeature(RegistryKey)` (wrap in try/catch — it **throws** on unknown keys) |
| Creative menu | `ItemGroupEvents.MODIFY_ENTRIES_ALL` → removeIf |
| Systems (enchanting, brewing, combat timing, daylight burn) | `@Inject(at=HEAD/RETURN, cancellable=true)` mixin, each reading a rule. **Never `@Overwrite`.** |
| Dimensions | Portal-traversal cancel + eviction + activator-item gating; **never delete the dimension** |
| Textures / models / sounds | **Not our job** — resource pack (Golden Days). *Exception:* genuinely new Phase 2 content (e.g. obsidian fire textures) ships its assets in `unbeta-content`, since there's no vanilla asset to override. |

**Mixin hygiene:** `@Inject` HEAD/RETURN cancellable is the default; `@Overwrite` banned; never mixin rendering/chunk/lighting classes (Sodium/Iris live there). Every injection's first act is a rule check + early return.

**One owner per subsystem:** terrain → Moderner Beta; lighting/fog/animations/old-combat → Nostalgic Tweaks; textures → Golden Days; **content existence → unbeta-core**; new content → unbeta-content. If two owners claim a behavior, the *other* mod wins and Unbeta deletes its version.

---

## 6. Deviations from the original plan (brisk — for historical context)

The original Phase 1 plan and reality diverged in a few places. All intentional; noting them so nothing surprises a future reader:

- **Config is `.json`, not `.json5`.** No functional difference.
- **End removal (was "D5, hard"):** portal *travel* + eye + eviction are done, but the **stronghold portal ROOM still generates inert** (frames present, non-functional). The "strip the frames from `StrongholdGenerator`" layer was deferred — it's entangled with Phase 2's plan to replace strongholds with custom dungeons anyway. So D5 is *functionally* met (unreachable End) but not *cosmetically* (ruins remain).
- **Obsidian fire became a new block,** not a recolor-of-vanilla-fire. Vanilla's fire system is built around fire-being-multiple-blocks (soul fire is a separate block), so a new `ObsidianFireBlock` follows the grain instead of fighting it.
- **`GeneratedBlockGate` (lush-cave block scan) shipped but is DISABLED.** A chunk-load block scan stalled world saving twice. Lush caves still generate (gated, so no drops / not in creative) — deferred to Phase 2, where the glowmud chain (§7) gives them a *purpose*.
- **Deepslate below y=0** persists — consequence of keeping 1.20 world height (decision D1: height stays −64→320). Accepted.
- **Datagen pipeline** was lighter than planned — structure/feature gating uses direct JSON overrides + `BiomeModifications` rather than a full Gradle datagen system. Works fine.

**Decisions still in force:** D1 (world height stays −64→320), D2 (1.9 combat delegated to Nostalgic Tweaks), D3 (advancements→achievements deferred), D4 (empty b1.8 villages — *still unbuilt*, villages currently trimmed to plains+desert but use some 1.14 blocks), D5 (End hard-removed — see above), D6 (code generated for the user, not specs).

---

## 7. Phase 2 — "Unbeta 1.7.3"

### 7.1 Done
- ✅ **Nether removed** — dimension gate + eviction; ghast/zombified_piglin/netherrack/soul_sand gated off. Proved the whole rule-override architecture.
- ✅ **No hostile daylight burning** — zombies (via `burnsInDaylight`) and skeletons (via `isAffectedByDaylight`, instanceof-scoped) don't ignite in sun.
- ✅ **Obsidian fire** — igniting obsidian yields permanent, random-colored (red/yellow/green/blue/purple), **silent, smokeless** fire. Obsidian-exclusive; regular orange fire unchanged and can't appear on obsidian. New `ObsidianFireBlock` + `FireColor` enum + `AbstractFireBlockMixin` reroute + client cutout render + 5 procedural animated textures (placeholder-quality, swappable with zero code change).

### 7.2 The Lighting Tech-Tree (the unifying design idea)

With the Nether gone, light becomes a **progression**. This braids together lush-cave leftovers, glowstone, obsidian fire, and a torch rework into one system.

```
Torches (temporary, durability) → Flinted Obsidian (permanent colored fire ✅) → Glowstone (permanent block)
                                                                                        ↑
                              Glowmud (sand + glow berries) → smelt → Glowstone → breaks into glowstone dust
                                              ↑
                                   Glow berries (re-added, from lush caves)
```

**Tier 0 — Reworked Torches** 💭
- **Unstackable; use durability** as burn-life. Durability **ticks down even while PLACED** — the key technical requirement, and a deliberate precedent: the same "count down on a placed/stored item across save-load" machinery is what **creeper inventory bombs** need. Build once (the persisted tick scheduler, §8), reuse.
- Crafted **UNLIT**. **Can't be hand-slapped off** — a placed torch is "mined" off like a fence (takes a short moment, not instant). This is deliberate: it lets you *hit* a placed torch once (to extinguish it, or to light another torch from it) **without** knocking it off the wall.
- Interaction matrix (left-click / hit): hitting certain blocks **lights** it (e.g. gravel); hitting others **extinguishes** (e.g. dirt); rain/water **extinguish**; hitting wood/leaves **ignites those blocks** flint-style; hitting an **enemy** does NOT extinguish — it **sets the enemy on fire** (high-risk melee weapon: you burn your own light's clock for fire damage).
- **"Wicking"** extends life: consumes a stick, stick gets shorter, torch lasts longer.
- **Natural/world torches** (village torches etc.) are **immediately replaced with Unbeta UNLIT torches** → a lit torch in the world is ALWAYS a player action (world lighting becomes a readable trace of presence).
- Impl: add Unbeta's own torch alongside vanilla during dev, swap over when solid. Needs the persisted scheduler (§8) for the placed-durability tick.
- Open Qs: burn duration (held vs placed)? behavior at zero durability (drop or go dark)? full block-interaction lists.

**Tier 1 — Flinted Obsidian** ✅ (obsidian fire, done). Slots in as mid-tier permanent light.

**Tier 2 — Glowstone via a new chain** 💭
- **Glowmud** = **sand + glow berries** (mirrors TNT's sand+gunpowder recipe shape). **Glowmud emits light via the dynamic-light datapack** already in the modpack (a designed dependency, not custom code). Glowmud is **gravity-affected** like sand/gravel and **drops itself** when mined.
- **Glowmud → smelt → Glowstone block.** Glowstone **breaks into glowstone dust** as vanilla does — and since the Nether (dust's wild source) is gone, **mining Glowstone is now the only dust source.** So glowstone dust stays in the game; the whole glowstone economy reroutes through the player.
- **Glow berries re-added** as collectable food + the glowmud ingredient. **Re-enable via `overrides.json` rule flip, NOT a keep-list edit** — keeps the manifest an honest beta record and frames the berry as intentional new content. Gives lush caves a reason to exist.

### 7.3 The rest of the Phase 2 roadmap (from the "Unbeta 1.7.3" vision)

**Obsidian (finish the set):** blast-immunity (all obsidian = mineable bedrock); portal-disable (mostly moot now Nether's gated); mining rules (needs Diamond Pick *or* class-item "Miner's Flint Pick" for a drop — **cross-dependency on the class system**).

**Mobs:** no mob spawners (rule flip); zombie regen + Husk/Drowned/**Frozen** variants (**Frozen conflicts with b1.8 having no snow biomes** — resolve: allow 1.0-era snow biomes, re-enable in Moderner Beta, or tie to snow-layer/altitude); skeletons leave **re-animating bone piles** (needs scheduler); all spiders → cave spiders + wall/ceiling crawl (nyfsspiders mod may cover the crawling); creeper plants an **inventory bomb** (shares timer machinery with torches); new mobs **Siren / Will o' Wisp / Genius** (full entity registration — obsidian fire proved we can register new content).

**World:** dungeons replace strongholds (undecided: repurpose `StrongholdGenerator` vs new jigsaw structures; floating-island dungeons need custom structure code; ties to deferred D5 stronghold work).

**Items/systems:** rail & powered-rail recipe buffs + abandoned surface rails; minecarts with new physics; **Clamboxes** (block entity that "cooks" pearls); **Tree of Life / Life Blocks** (teleporting, indestructible-except-by-Wand connected structures — the hardest thing in the vision, needs `UnbetaWorldState`); **Origins-style class system** at world creation + **Flint tools** (needs `PlayerDataService`); XP orbs → lore splashscreens (ties to deferred D3).

---

## 8. Foundation services still needed (build before dependents)

**Not yet built.** Each unlocks multiple features; retrofitting later means touching working code.

- 📋 **Persisted tick scheduler** (survives save/load) — **highest leverage.** Unlocks torch burn-out, creeper-bomb timer, and skeleton bone-pile re-animation. Build this next when resuming feature work.
- 📋 **`PlayerDataService`** (persistent per-player NBT) — unlocks class system, Flint tool stats, creeper-bomb tracking.
- 📋 **`UnbetaWorldState`** (typed persistent cross-chunk storage) — unlocks Life Blocks (Wand UUID ↔ Life Block set identity model).

---

## 9. Dev environment & workflow

### 9.1 Environment
```
Project:   /home/blackmita/Desktop/Minecraft-mod-dev/unbeta   (the clean, GitHub-tracked project)
Testbed:   /home/blackmita/Desktop/Minecraft-mod-dev-PlusAllMods/unbeta   (disposable copy for mod testing)
OS:        Ubuntu 24.04 / Linux Mint
Java:      OpenJDK 21 (compiles to release 17 — this is correct, don't "fix")
Gradle:    wrapper 8.14.4;  Loom 1.10.5  (Loom 1.6 does NOT work with Gradle 8.14)
Minecraft: 1.20.1;  Yarn 1.20.1+build.10;  Loader 0.15.11 (dev);  Fabric API 0.92.2
```
Real play/testing: **Prism Launcher**, a normal 1.20.1 Fabric instance (loader 0.19.3 is fine — the built jars require ≥0.15).

### 9.2 Build / run / deliver
- Build both modules: `./gradlew build`
- Run dev client: `./gradlew :core:runClient` (auto-loads content module via a `runtimeOnly project(':content')` line in `core/build.gradle`)
- `./gradlew :core:genSources` (note the `:core:` prefix — bare `genSources` fails on this dual-module setup) decompiles Minecraft into a sources jar for reading real API. That sources jar is at:
  `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/…-sources.jar`
- Distributable jars: `core/build/libs/unbeta-core-0.1.0.jar` + `content/build/libs/unbeta-content-0.1.0.jar` (ignore the `-sources` ones). Both go into a Prism instance's mods folder alongside Fabric API.
- **Delivery loop with the user:** the assistant packages changed files as a zip preserving repo-relative paths; user runs `unzip -o ~/Downloads/X.zip -d <project>` then `./gradlew build`. **For single-file edits, prefer a paste-into-terminal python snippet over a zip** — a single-file zip clobbers prior hand-edits to that file (this caused a regression). GitHub push needs a Personal Access Token with `repo`+`workflow` scopes.

---

## 10. Working practices (the hard-won lessons — do not skip)

- **⚠️ NEVER trust a remembered 1.20.1 API name.** Verify against the user's jars before writing code that depends on it. For Fabric API: `javap` the module jar. For Minecraft's own (obfuscated) classes: read the decompiled **sources jar** (grep it) or just build and let the compiler name the wrong method. Real mistakes this caught: `LootTableEvents.REPLACE` has 5 params not 4; `ServerEntityEvents` is in `event.lifecycle.v1` not `entity.event.v1`; `ServerChunkEvents` has no `CHUNK_GENERATE`; fire rendering needs vanilla's exact `multipart` blockstate + `template_fire_*` models; `AbstractFireBlock` requires an `isFlammable` override. Every feature so far had ≥1 wrong-name guess — the verification step is not optional.
- **Generate lists from the running game, never from memory** — the manifest came from `/unbeta audit`, the feature list from `/unbeta features`. Guessed lists had wrong/nonexistent entries every time.
- **Make gates fail soft** — anything reading user config wraps per-item work in try/catch (e.g. `removeFeature` throws on a bad key). A content gate must never crash the game over a config typo.
- **Don't scan blocks on chunk load** — it froze world-saving twice. Worldgen block-swaps must happen at generation time, not per-load.
- **`overrides.json` is the HIGHEST precedence layer** — it outranks even Phase 2 content. A stale example line in it once silently countermanded the Nether removal; `/unbeta why` pinpointed it instantly. The shipped stub now has an empty rules object (footgun fixed).
- **Datapacks: test in Prism, not the dev client.** The Loom dev client fails to load world datapacks ("Missing metadata" despite valid packs). This is a dev-harness quirk, not a real incompatibility — the same packs load fine in Prism. Don't re-investigate it.
- **Commit at every known-good state.** `git add . && git commit -m "…" && git push`.

---

## 11. Known cosmetic quirks (not bugs)

- **Audible-but-invisible gated mobs** — camels/cats/goats make a sound the tick before the spawn catch-all discards them. Harmless; fixable later by suppressing spawn sound or catching a tick earlier.
- **Lush caves generate** underground (gated: no drops, not in creative). Deferred; the glowmud chain gives them purpose.
- **Deepslate below y=0** — consequence of D1. Accepted.
- **Obsidian-fire textures are procedural placeholders** — pure asset swap to improve, no code.
- **Empty villages use some 1.14 blocks** — D4 (custom b1.8 village template set) is still unbuilt; the largest untackled Phase 1-scope task.

---

## Appendix — one-paragraph brief for a collaborating LLM

> `unbeta-core` is a Fabric mod for Minecraft 1.20.1 (Java 17, Yarn mappings, Fabric API 0.92.2, Loom 1.10.5) that makes vanilla 1.20.1 present only content that existed in Java Beta 1.8, while leaving all non-`minecraft` namespace content from ~30 other mods untouched. Content is never unregistered — it is gated (no recipe/loot/spawn/worldgen/creative entry) via named rules resolved through a precedence chain (manifest → core config → mod overrides → user overrides), so `unbeta-content` (Phase 2) flips any rule without editing core. All mixins are cancellable `@Inject` guarded by a rule check; `@Overwrite` is banned; rendering/chunk/lighting classes are off-limits. Terrain is Moderner Beta's job, visuals Nostalgic Tweaks', textures Golden Days'. Phase 1 is done and verified compatible with ~30 mods + 4 datapacks in Prism; Phase 2 has removed the Nether, stopped hostile daylight burning, and added permanent colored obsidian fire. Verify every 1.20.1 API name against the user's decompiled sources jar before writing code — every feature so far had at least one wrong-name guess.
