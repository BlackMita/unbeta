# UNBETA — Master Plan, Phase 1

**Project:** Make Minecraft Java 1.20.1 (Fabric) feel like Java Beta 1.8 "Adventure Update Part 1", without breaking ~two dozen coexisting 1.20.1 Fabric mods, and in a way that Phase 2 ("Minecraft Unbeta 1.7.3") can build on top of instead of fighting.

**Status:** planning document / action plan. Written to be handed to a human developer *or* pasted into another LLM as a specification.

---

## 0. How to use this document

This document is the **shared brief**. If you hand it to another LLM, the useful prompt shape is:

> "Read the attached UNBETA master plan. You are implementing `[section X]`. Follow the architecture in §4 and the mechanism decision table in §5. Do not invent new removal mechanisms. Output: `[files]`."

Three things in here are load-bearing and must not be silently changed by a downstream collaborator:

1. **Nothing is ever unregistered.** (§4.2)
2. **Every rule is namespace-scoped to `minecraft:` unless explicitly listed.** (§6.3)
3. **`unbeta-core` never contains Phase 2 content. It only contains the gate machinery and the vanilla rules.** (§10)

Break any of those three and Phase 2 becomes a rewrite instead of an addition.

### 0.1 Locked decisions (2026-07-26)

These supersede any softer language elsewhere in this document.

| # | Decision | Effect |
|---|---|---|
| D1 | **Practical strictness.** Gate content only. | World height stays −64→320. §5.9 is closed, not deferred. |
| D2 | **Combat revert is not ours.** | §5.7 is delegated to Nostalgic Tweaks. `unbeta-core` ships the rule key and no implementation. |
| D3 | **Advancements → achievements is deferred to Phase 2.** | Phase 1 may disable advancement *toasts* if trivial, but builds no replacement UI. |
| D4 | **Empty b1.8-style villages are in scope for Phase 1.** | Custom village template set. Largest single task; see §9. |
| D5 | **The End is removed hard, not made inert.** | Stronghold portal rooms must not generate with frames. Eye of Ender and End Portal Frame are removed content, not decorative. See §5.6. |
| D6 | **Code is generated for the user.** | Deliverables are working files, not specs. Every generated file must be marked as compile-verified or not. |

---

## 1. Scope and design goals

### 1.1 What "Phase 1 done" means

A 1.20.1 Fabric instance where a player who knows Beta 1.8 can play for several hours and not encounter anything that didn't exist in September 2011 — *except* for the deliberate quality-of-life and atmosphere mods you chose (AppleSkin, WTHIT, AmbientSounds, Presence Footsteps, Immersive Paintings, etc.), which are allowed to be visibly modern.

That distinction matters. You are not making a "1.20.1 is now beta 1.8" total conversion. You are making a **content gate**: vanilla content is filtered down to the b1.8 set, and everything from any other namespace passes through untouched. That single rule is what makes twenty-four unrelated mods survive contact with this project.

### 1.2 Non-goals for Phase 1

- Not reimplementing beta terrain generation (Moderner Beta does it, correctly, already).
- Not reimplementing beta lighting/fog/animations/sounds (Nostalgic Tweaks does it).
- Not shipping textures (Golden Days resource pack does it).
- Not adding any new content. Zero. Phase 1 only subtracts and reverts.
- Not touching the Nether. Beta 1.8 **has** the Nether. Removing it is a Phase 2 decision, and Phase 1 must merely make that flip cheap.

### 1.3 The three deliverables of Phase 1

| Artifact | What it is | Repo location |
|---|---|---|
| `unbeta-core` | Fabric mod: the rule engine, the gates, the mixin hooks, the audit commands | `/core` |
| `unbeta-data` | A generated datapack (recipes, loot, structures, tags), built by Gradle datagen from the manifest | generated into `/core/src/main/generated`, shipped inside the jar |
| `unbeta-pack` | The reproducible client instance (packwiz), pinning exact versions of all ~24 mods | separate repo, or `/pack` |

---

## 2. Research: what Beta 1.8 actually was

Released **September 14, 2011**. Part 1 of the Adventure Update; Part 2 was release 1.0.0 in November 2011. Everything from 1.0.0 onward is out of scope for us — including several things people misremember as "beta."

### 2.1 What Beta 1.8 introduced (keep all of this)

**Mobs (new):** Cave Spider (mineshaft spawners, poisons), Enderman, Silverfish (from monster eggs in strongholds).

Notable b1.8-era Enderman behavior, which is *not* modern behavior: it burns in daylight, it takes damage from water, it can pick up most full blocks (not a short whitelist), and it does **not** teleport away from incoming arrows.

**Blocks (new):** Stone Bricks + Mossy + Cracked, Monster Egg (infested) blocks, Brick Slab/Stairs, Stone Brick Slab/Stairs, Glass Pane, Iron Bars, Fence Gate, Vines (**not climbable in b1.8**), Mushroom Blocks, Melon, Melon/Pumpkin Stems.

**Items (new):** Raw Chicken, Cooked Chicken, Raw Beef, Steak, Melon Slice, Melon Seeds, Pumpkin Seeds, Rotten Flesh, Ender Pearl (**inert — cannot be thrown, has no use**).

**Systems (new):** Hunger bar. Sprinting (double-tap forward). Experience orbs (**no use for XP yet**). Creative mode and creative flight. Status effects exist but only three are reachable: Regeneration (golden apple), Poison (cave spider), Hunger (raw chicken 30% / rotten flesh 80%).

**Structures (new):** Villages (plains + desert, **completely uninhabited — no villagers exist**), Mineshafts with chests, Strongholds (**no End portal room — it didn't exist yet**), Ravines, Huge Mushrooms.

**Changes:** Food restores hunger, not health; all food stacks except mushroom stew; 1.6s eating animation. Bow must be charged; damage scales with charge. Unarmed damage dropped to half a heart. Golden apple gives Regeneration instead of 10 hearts. Shears collect grass and vines. Player-placed leaves don't decay. Chests got a 3D model with open/close animation. Clouds moved to the top of the map and stopped clipping through blocks. Zombies drop rotten flesh instead of feathers. Skeletons hold bows properly. Dynamic-ish colored lighting (torches warm, night skylight blue). Multiplayer server list. One new achievement: Sniper Duel.

### 2.2 Biomes in Beta 1.8

Forest, Plains, Desert, Swamp, Extreme Hills, Taiga, River, Ocean. New fractal biome code — biomes are large and flat except Extreme Hills.

**Snow and ice biomes were removed in Beta 1.8** and did not return until 1.0.0. Taiga generated without snow.

> ⚠️ **Phase 2 conflict, flagged early:** your document specifies "Frozen Zombies from Minecraft Dungeons added for biomes that snow." In a strict b1.8 world there are no snowy biomes. You will need to either (a) accept 1.0-era snowy biomes as an exception, (b) configure Moderner Beta's Beta 1.8 preset to re-enable snowy biomes, or (c) tie Frozen Zombies to altitude/snow-layer presence rather than biome. Decide this before Phase 2 mob work starts.

### 2.3 The full b1.8 mob roster (this is your keep-list)

Passive: Pig, Cow, Sheep, Chicken, Squid, Wolf.
Hostile: Zombie, Skeleton, Creeper, Spider, Cave Spider, Slime, Enderman, Silverfish, Ghast, Zombie Pigman.

That's **16 mobs**. Vanilla 1.20.1 has roughly 80 entity types that are mobs. The gap is the single biggest chunk of Phase 1 work.

Also absent in b1.8 and therefore removed: **breeding of any kind** (animals no longer despawn in b1.8, which is what makes them capturable, but you cannot breed them), baby animals, villagers, iron golems, snow golems, mooshrooms, all fish entities (fishing produces the fish item, but no fish mob exists), bats, and every mob added from 1.0.0 onward.

---

## 3. The delta: what leaves 1.20.1

This section is the source material for the manifest (§4.4). Organized by the update that introduced the content, because that's the least error-prone way to produce an exhaustive list and the easiest way for a collaborator to verify a chunk at a time.

### 3.1 Systems to remove or revert

| System | Action | Notes |
|---|---|---|
| The End dimension | **Remove** (see §5.6) | Did not exist in b1.8 |
| The Nether | **Keep** | Exists in b1.8. Gate exists but defaults ON |
| Enchanting (table, XP cost, all enchantments) | Remove | 1.0.0 |
| Brewing, potions, glass bottles, splash potions | Remove | 1.0.0. Only Regen/Poison/Hunger effects survive, from their b1.8 sources |
| Villagers, trading, raids, pillager outposts | Remove | Villages stay, empty |
| Anvils, grindstones, smithing tables, netherite | Remove | 1.4 / 1.14 / 1.16 |
| Off-hand slot, dual wielding, shields | Remove | 1.9 |
| Attack cooldown + sweep attack | **Revert to instant** | 1.9. Restore b1.8 combat feel |
| Elytra, tridents, riptide, totems | Remove | 1.9 / 1.13 / 1.11 |
| Swimming, crawling, sprint-swim | Remove | 1.13 |
| Advancements + recipe book + knowledge book | Remove/disable | 1.12. Replace with the b1.8 achievement toast (Nostalgic Tweaks may cover the screen; verify) |
| Spawn eggs | Remove | 1.1 |
| Adventure/Spectator game modes | Remove from cycle | 1.3 / 1.8 |
| Breeding, baby mobs, animal following food | Remove | Post-b1.8 |
| World height −64→320 | **Optionally** clamp to 0→128 | Purist toggle; see §5.9 risk note |

### 3.2 Content to remove, by introducing update

Condensed. The manifest carries the exact registry IDs.

- **1.0.0** — End + dragon + end stone/obsidian pillars, Nether fortress + blaze + nether wart + magma cube, villagers, mooshroom, snow golem, brewing, enchanting, eye of ender, ghast tear, blaze rod/powder, glowstone dust recipes tied to potions.
- **1.1** — spawn eggs, beach biomes (ignore, worldgen is Moderner Beta's problem).
- **1.2** — jungle biome + jungle wood + cocoa, ocelot, iron golem, **plank/stair/slab wood variants** (b1.8 has one plank type visually and only oak/cobble/brick/stone-brick stairs), redstone lamp, hardened clay, tall grass changes.
- **1.3** — emerald, trading, ender chest, tripwire, desert/jungle temples, writable books, adventure mode.
- **1.4** — wither, wither skeleton, bat, witch, anvil, item frame, beacon, carrot/potato/beetroot-adjacent farming, cobblestone wall, flower pot, nether star.
- **1.5** — hopper, dropper, comparator, redstone block, daylight sensor, nether quartz + quartz blocks, weighted pressure plates, activator rail, TNT/hopper minecarts.
- **1.6** — horse/donkey/mule, lead, carpet, hay bale, coal block, name tag.
- **1.7** — mesa/roofed forest/savanna/etc biomes, acacia + dark oak, stained glass, red sand, podzol, packed ice, the expanded flower set, fishing loot overhaul.
- **1.8** *(release 1.8, not beta)* — slime block, banner, armor stand, prismarine + ocean monument + guardian, granite/diorite/andesite, iron trapdoor, red sandstone, rabbit, endermite, coarse dirt, mutton, barrier.
- **1.9** — see systems table; plus end cities, shulkers, chorus fruit, beetroot, igloo, grass path, frost walker.
- **1.10** — polar bear, husk, stray, magma block, nether wart block, red nether brick, bone block, structure block, fossils.
- **1.11** — llama, shulker box, observer, totem, woodland mansion + evoker + vindicator + vex, exploration maps.
- **1.12** — parrot, concrete + concrete powder, glazed terracotta, advancements, recipe book.
- **1.13 (Update Aquatic)** — **all of it**: dolphin, turtle, all fish, drowned, phantom, trident, coral, kelp, sea pickle, seagrass, buried treasure, shipwreck, ocean ruin, conduit, blue ice, bubble columns, debug stick, swimming.
- **1.14** — village rework + all villager professions, pillager/ravager/raid, bamboo, panda, cat, fox, scaffolding, barrel, smoker, blast furnace, lectern, loom, stonecutter, composter, grindstone, cartography table, fletching table, smithing table, bell, campfire, lantern, sweet berries, crossbow, wandering trader, sign rework.
- **1.15** — bee, beehive, honey.
- **1.16 (Nether Update)** — every Nether biome and its blocks, netherite, piglin/brute, hoglin, zoglin, strider, soul fire/soul soil, target, respawn anchor, lodestone, chain, blackstone, basalt, crying obsidian, bastion, ruined portal, twisting/weeping vines, nether gold ore. **The b1.8 Nether is netherrack, soul sand, glowstone, lava, ghasts and zombie pigmen. Nothing else.**
- **1.17** — copper (all forms), amethyst, deepslate (all forms), axolotl, glow squid, goat, dripstone, candle, tinted glass, lightning rod, powder snow, spyglass, glow item frame, moss/azalea, sculk sensor.
- **1.18** — cave/cliff worldgen (Moderner Beta supersedes), aquifers, ore distribution.
- **1.19** — deep dark, warden, sculk family, ancient city, allay, frog/tadpole, mangrove, mud, echo shard, recovery compass, goat horn, chest boat.
- **1.20** — cherry grove + cherry wood, sniffer, archaeology (brush, suspicious sand/gravel, pottery sherds, decorated pot), camel, bamboo wood set, hanging signs, calibrated sculk sensor, armor trims, trail ruins, netherite upgrade template.

### 3.3 Verification protocol

Do **not** trust this list — or any LLM's expansion of it — as final. Before implementation, run one verification pass per bucket against the Minecraft Wiki's Beta 1.8 block/item tables and the version history pages, and record the result in the manifest as `verified: true|false` with a source URL. There are perhaps a dozen genuinely ambiguous items (lily pads, cobwebs, chainmail obtainability, lapis, sponge, exact slab/stair set) where memory is unreliable and the wiki is authoritative. The audit command in §5.10 exists partly to make this pass mechanical.

---

## 4. Architecture

### 4.1 Two mods, one engine

```
unbeta-core   (Phase 1)  — the rule engine + vanilla gates. Ships the removal datapack.
unbeta-content(Phase 2)  — depends on core. Adds mobs, blocks, items, classes, dungeons.
```

`unbeta-content` never mixins anything `unbeta-core` already mixins. It talks to core through core's public API. This is the whole reason Phase 1 exists as a separate artifact.

### 4.2 The prime directive: gate, never unregister

Minecraft 1.20.1 freezes its registries after bootstrap. Registries are also synchronized to the client and referenced by every other mod, by every saved chunk, and by every datapack. Deleting `minecraft:villager` from the entity registry does not give you a beta game; it gives you a crash log.

So: **every removed thing still exists in the registry and is still a valid ID.** It is simply unreachable:

1. It cannot be crafted (recipe gone).
2. It cannot drop (loot table gone).
3. It cannot spawn (spawn entry removed / spawn cancelled).
4. It cannot generate (structure/feature disabled).
5. It cannot be seen in creative or in EMI/JEI (entry hidden).
6. Optionally, it cannot be used if somehow obtained (use event cancelled).

Six layers. Most things need two or three. This also means removals are **reversible at runtime by config**, which is exactly what Phase 2 needs when it wants some of them back.

### 4.3 The rule engine

Everything the mod does is expressed as a **rule** with a stable string key.

```
unbeta:entity/remove/minecraft.villager
unbeta:item/hide/minecraft.netherite_ingot
unbeta:system/enchanting
unbeta:system/attack_cooldown
unbeta:dimension/the_end
unbeta:dimension/the_nether
unbeta:mob/daylight_burn
```

Rule values resolve through a fixed precedence chain — this is the single most important mechanism for Phase 2 compatibility:

```
1. manifest default        (baked into unbeta-core at build time)
2. core config file        (user edits: config/unbeta/core.json5)
3. content-mod overrides   (registered by any mod via the `unbeta:rules` entrypoint)
4. user override file      (config/unbeta/overrides.json5 — always wins)
```

Phase 2 therefore removes the Nether by shipping, in `unbeta-content`, a one-line rule override: `unbeta:dimension/the_nether = false`. No Phase 1 code changes. No mixin duplication. That is the test of whether Phase 1 was designed correctly.

The public API surface, kept deliberately small:

```java
public interface UnbetaRules {
    boolean isEnabled(RuleKey key);
    boolean isAllowed(EntityType<?> type);
    boolean isAllowed(Item item);
    boolean isAllowed(Block block);
    boolean isDimensionAllowed(RegistryKey<World> world);
    void registerOverride(String modId, RuleKey key, boolean value);
}
```

Plus a handful of events that Phase 2 will need and that only Phase 1 should ever mixin for:

```java
MobBehaviorEvents.MODIFY_GOALS      // fired at tail of MobEntity#initGoals
MobBehaviorEvents.MODIFY_ATTRIBUTES
SpawnEvents.SUBSTITUTE              // "when vanilla wants to spawn X here, spawn Y instead"
EntityDeathEvents.REPLACE_DROP      // return a block/entity instead of item drops
BlockBehaviorEvents.BLAST_RESISTANCE_OVERRIDE
UnbetaTickScheduler                 // delayed world/item callbacks, persisted across save/load
UnbetaWorldState                    // typed persistent per-world storage
```

Those last four exist in Phase 1 *unused or barely used*, purely because Phase 2 needs them and retrofitting them later means editing Phase 1's mixins — which is precisely the coupling this architecture is designed to avoid. Building them now costs maybe two days. Retrofitting them costs the project.

### 4.4 The manifest — one JSON to rule them

`/core/src/main/resources/unbeta/manifest.json` (or split per category). Single source of truth. Datagen reads it. The runtime gates read the compiled form. A collaborating LLM can be handed this file alone and asked to extend it.

```json
{
  "schemaVersion": 1,
  "entries": [
    {
      "id": "minecraft:villager",
      "kind": "entity",
      "action": "remove",
      "mechanisms": ["spawn_gate", "creative_hide", "structure_dependency"],
      "introducedIn": "1.0.0",
      "phase": 1,
      "verified": true,
      "source": "https://minecraft.wiki/w/Java_Edition_1.0.0",
      "note": "Villages still generate, empty, as in b1.8"
    },
    {
      "id": "minecraft:iron_bars",
      "kind": "block",
      "action": "keep",
      "introducedIn": "b1.8",
      "phase": 1,
      "verified": true
    },
    {
      "id": "minecraft:the_nether",
      "kind": "dimension",
      "action": "keep",
      "phase": 1,
      "phase2Action": "remove",
      "note": "Phase 2 flips this via rule override, not code change"
    }
  ]
}
```

The `phase2Action` field is optional documentation, not behavior. It exists so that a collaborator reading the manifest can see where Phase 2 is going and avoid designing something that blocks it.

---

## 5. Removal mechanisms — the decision table

For each category, exactly one preferred mechanism. Collaborators should not improvise alternatives.

| Category | Mechanism | Why |
|---|---|---|
| Recipes | Datagen: emit an override JSON with a `fabric:load_conditions` block that can never be satisfied | Silent, data-driven, no code, respects other mods' recipes |
| Loot tables | `LootTableEvents.MODIFY` in code, filtered by table namespace | Survives other mods editing the same tables; datapack overwrite does not |
| Mob spawn entries | Fabric `BiomeModifications` + `ModificationPhase.REMOVALS` + `removeSpawnsOfEntityType` | Does not overwrite biome JSONs, so it composes with Moderner Beta and any biome mod |
| Spawns that slip through (spawners, structures, breeding) | `SpawnEvents.SUBSTITUTE` / cancel in a `ServerEntityEvents.ENTITY_LOAD` guard | Catch-all safety net |
| Structures | Datagen: override `worldgen/structure_set/*.json` with an empty `structures: []` | Cleanest disable, datapack-level, survives worldgen mods |
| Worldgen features (ore, plants, decorations) | `BiomeModifications` removals by feature key | Same reason as spawns |
| Creative menu / EMI / JEI | `ItemGroupEvents.MODIFY_ENTRIES_ALL` → `entries.getDisplayStacks().removeIf(...)`; plus EMI/JEI plugin if those are installed | Cosmetic layer, must be separate from functional layer |
| Item use, if obtained anyway | `UseItemCallback` / `UseBlockCallback` cancel, gated behind `strictMode` | Off by default; only for purist runs |
| Systems (enchanting, brewing, combat timing) | Targeted `@Inject(at = HEAD, cancellable = true)` mixins, each reading a rule key | Never `@Overwrite`. Never `@Redirect` in hot paths |
| Dimensions | Portal-ignition + travel cancellation + activator item gating (§5.6) | Never delete the dimension |
| Textures / models / sounds | **Not our job** — resource pack | Keeps the mod jar free of `minecraft:` asset overrides that would collide with Golden Days |

### 5.6 Removing the End specifically

Do not delete `minecraft:the_end`. Instead:

1. Gate `EnderEyeItem#useOnBlock` — eyes never fill frames.
2. Gate `EndPortalFrameBlock` interaction and `EndPortalBlock#onEntityCollided`.
3. Cancel `ServerPlayerEntity#moveToWorld` when the destination is `the_end` and the rule is off; if a player somehow loads inside it, teleport them to their spawn point and log it.
4. Mixin `StrongholdGenerator`'s portal-room piece to place stone bricks where the frames would go. This is the one place where b1.8's "stronghold without a portal room" needs code, because stronghold pieces are generated in Java, not from NBT templates. **Per D5 this is required, not optional** — "leave the inert frames as decorative ruins" is explicitly rejected. The portal room's silverfish spawner is also removed, which lines up with Phase 2's "mob spawners REMOVED from game" anyway.
5. Remove the ender dragon, shulkers, endermites, chorus plants, end cities, elytra, dragon egg, end crystal, and end stone from the item/entity gates as normal manifest entries.

The exact same five steps, with `NetherPortalBlock` and `FlintAndSteelItem`, become Phase 2's Nether removal. **Write the dimension gate generically in Phase 1** — parameterized by dimension key, portal block, and activator item — and Phase 2 is a config change.

### 5.7 Reverting 1.9 combat

`PlayerEntity#getAttackCooldownProgress` forced to `1.0F`, sweep attack suppressed in `PlayerEntity#attack`, off-hand slot rendering and interaction disabled, shields removed as an item. Check whether Nostalgic Tweaks already ships this before writing it — if it does, delete your version and declare Nostalgic Tweaks the owner (see §6.2).

### 5.8 Hunger and food

Keep. Hunger is a Beta 1.8 feature; this trips people up constantly. Keep sprinting too. Keep XP orbs (with no use). AppleSkin showing saturation is a deliberate modern QoL exception.

### 5.9 World height — CLOSED (decision D1)

Beta 1.8 was 0-127. Clamping 1.20.1 is possible (Moderner Beta even ships a datapack that raises min Y to 0 and restores pre-1.18 ore generation), but it is the single highest-risk change for mod compatibility, and **decision D1 rules it out**. World height stays -64 to 320. Do not reopen this without re-testing the whole pack.

If a purist build is ever wanted, it belongs in `unbeta-pack` as an optional datapack, never in `unbeta-core`.

### 5.10 The audit commands — build these first

```
/unbeta audit            → dumps every registered item/block/entity/structure with its gate status to a CSV in the run dir
/unbeta why <id>         → prints which rule(s) affect that ID and where the value came from in the precedence chain
/unbeta rules            → lists all rules and resolved values
```

`/unbeta audit` output, committed to the repo after each build, becomes a **diff-able artifact**. Add a mod, re-run, diff the CSV: you instantly see what the new mod added and whether your gates ate any of it. This turns "did I break the other twenty-three mods" from a vibe into a test. Build these in the first week; everything else gets easier afterward.

---

## 6. Coexisting with the other mods

### 6.1 Recommended mod slate

Your six, plus the ones that carry the "feel" you'd otherwise have to write yourself:

| Mod | Role | Notes |
|---|---|---|
| Fabric API | dependency | pin one version for the whole pack |
| **Moderner Beta** | terrain, biomes | Fork of Modern Beta, actively maintained, supports 1.20.1 Fabric, and **ships a Beta 1.8 preset**. Prefer this over the original Modern Beta. Requires Cloth Config |
| **Nostalgic Tweaks** | lighting, fog, sky, animations, old mob spawning, old combat feel, old screens, C418 music | 2.0 line targets 1.20.1. 400+ toggles. Requires Architectury API + Cloth Config |
| **Golden Days** (resource pack) | beta textures/sounds | Not a mod; keeps `minecraft:` assets out of your jar |
| AppleSkin | QoL | accepted modern exception |
| WTHIT | QoL | accepted modern exception |
| AmbientSounds, Presence Footsteps | atmosphere | accepted modern exception |
| Immersive Paintings | content | **your own content mod, in a non-`minecraft` namespace → passes the gate untouched** |
| Sodium (+ Iris) | performance | see §6.4 |
| Mod Menu, Cloth Config, Architectury | plumbing | |

### 6.2 One owner per subsystem

Write this into `CONTRIBUTING.md` and enforce it in review:

| Subsystem | Owner |
|---|---|
| Terrain, biome shape, caves, ore distribution | Moderner Beta |
| Lighting, fog, sky, particles, item rendering, animations, screens | Nostalgic Tweaks |
| Textures, sounds, music | Golden Days |
| **Content existence: what can spawn, drop, craft, generate** | **unbeta-core** |
| New content | unbeta-content (Phase 2) |

If two owners claim the same behavior, the *other* mod wins and `unbeta-core` deletes its version. Your mod's value is the gate, not duplicated nostalgia.

### 6.3 Namespace scoping — the compatibility keystone

Every gate must be written as a predicate over the registry ID's **namespace**:

```java
if (!"minecraft".equals(id.getNamespace()) && !config.gatedNamespaces.contains(id.getNamespace())) {
    return ALLOW;   // not ours to judge
}
```

Consequences, all of them good:

- Immersive Paintings' paintings are never hidden.
- Moderner Beta's biome entries are never stripped.
- Phase 2's `unbeta:siren` is never eaten by Phase 1's hostile-mob removal rule — because Phase 1 removes *listed vanilla mobs*, not "everything not on the b1.8 list."

That last point is worth restating because it is the most common way a project like this fails. **Never write "remove all hostile mobs except [b1.8 list]".** Always write "remove [explicit list of vanilla IDs]." An allowlist over all namespaces is a time bomb that goes off the moment you add a mod.

`config/unbeta/core.json5` exposes `gatedNamespaces: ["minecraft"]` so a user can opt an additional mod into gating if they want.

### 6.4 Mixin hygiene

- `@Inject(at = @At("HEAD"), cancellable = true)` as the default. `@Overwrite` is banned. `@Redirect` and `@ModifyVariable` require justification in the PR.
- Every injection's first line reads a rule and returns early if disabled. No unconditional behavior.
- Do not mixin rendering, chunk building, or lighting classes. Sodium and Iris live there and will not forgive you. If you think you need to, you actually need a Nostalgic Tweaks setting.
- Keep mixins in two configs: `unbeta.mixins.json` (common) and `unbeta.client.mixins.json`, with `"client"` scoped properly so a dedicated server doesn't try to load client mixins.
- Default mixin priority (1000) unless you have a documented reason.
- Declare compat modules conditionally:

```java
if (FabricLoader.getInstance().isModLoaded("modernbeta")) { ModernBetaCompat.init(); }
```

### 6.5 `fabric.mod.json` contract

```jsonc
// core
"depends": { "fabricloader": ">=0.15.0", "minecraft": "~1.20.1", "java": ">=17", "fabric-api": "*" },
"recommends": { "moderner-beta": "*", "nostalgic_tweaks": "*" }

// content (Phase 2)
"depends": { "unbeta-core": ">=1.0.0" }
```

Java 17 for 1.20.1. (Java 21 only becomes the requirement at 1.20.5+.)

---

## 7. Repository and GitHub plan

### 7.1 Monorepo, Gradle multi-project

```
unbeta/
├── settings.gradle              # include ':core', ':content'
├── build.gradle                 # shared loom + java config via subprojects{}
├── gradle.properties            # minecraft_version, yarn_mappings, loader_version, fabric_version
├── gradle/wrapper/              # committed
├── core/
│   ├── build.gradle
│   └── src/main/
│       ├── java/net/yourname/unbeta/core/
│       │   ├── UnbetaCore.java
│       │   ├── api/            # PUBLIC — semver'd, Phase 2 depends on this
│       │   ├── rules/          # RuleKey, RuleRegistry, precedence resolution
│       │   ├── gates/          # Entity, Item, Block, Recipe, Structure, Dimension
│       │   ├── events/         # MobBehaviorEvents, SpawnEvents, TickScheduler, WorldState
│       │   ├── command/        # audit, why, rules
│       │   ├── compat/         # modernbeta, nostalgic, emi, jei — all conditional
│       │   ├── datagen/        # reads manifest.json, emits the datapack
│       │   └── mixin/
│       └── resources/
│           ├── fabric.mod.json
│           ├── unbeta.mixins.json
│           ├── unbeta.client.mixins.json
│           └── unbeta/manifest.json      # THE source of truth
├── content/                      # empty scaffold in Phase 1, real in Phase 2
├── docs/
│   ├── MASTER_PLAN.md            # this document
│   ├── BETA_1_8_REFERENCE.md     # §2 expanded, with wiki citations
│   ├── PHASE2_CONTRACT.md        # §10 expanded
│   └── audits/                   # committed /unbeta audit CSVs, one per release
└── .github/
    ├── workflows/build.yml
    ├── workflows/release.yml
    └── ISSUE_TEMPLATE/removal.yml
```

`content` depends on `core` in Loom with:

```gradle
dependencies {
    implementation project(path: ":core", configuration: "namedElements")
}
```

(The `namedElements` configuration is the Loom-specific bit that trips people up — a plain `project(":core")` will fail to remap.)

### 7.2 Branches

`main` = always buildable. `phase-1` = integration branch until Phase 1 ships, then merge and tag `v1.0.0`. `phase-2` opens after. Feature branches named `remove/1-13-aquatic`, `system/combat-revert`, `gate/dimensions`.

### 7.3 GitHub Issues as the removal checklist

One issue per bucket from §3.2 (`remove/1.13 Update Aquatic`), using an issue template that forces the author to fill in: registry IDs touched, mechanism used (must be from §5's table), manifest entries added, audit-diff attached. Labels: `bucket:1.13`, `mechanism:datagen`, `mechanism:mixin`, `risk:compat`, `phase:1`, `phase:2`.

A GitHub Project board with columns `Manifest → Implemented → Verified in-game → Audited` gives you a genuine progress bar across ~40 buckets, and gives a collaborating LLM a well-scoped unit of work ("do issue #23").

### 7.4 CI

`build.yml` on every push and PR: set up JDK 17, `./gradlew build`, upload `core/build/libs/*.jar` and `content/build/libs/*.jar` as artifacts. Fail the build if datagen output differs from what's committed (`./gradlew runDatagen && git diff --exit-code`) — that keeps the manifest and the generated datapack honest.

`release.yml` on tag `v*`: build, create a GitHub Release, attach jars, optionally publish to Modrinth with the Minotaur Gradle plugin.

### 7.5 Distributing the instance: packwiz + GitHub Pages

Put the pack in `unbeta-pack` (its own repo). packwiz pins the exact file hash of every one of the ~24 mods. Publish the packwiz index via GitHub Pages; players (or you, on a second machine) point packwiz-installer at the URL and get a byte-identical instance. When you bump a mod version, the diff is a one-line commit and everyone updates.

This matters more than it sounds. "It works on my machine" in a 24-mod pack is not debuggable. A pinned, hash-verified pack is.

### 7.6 What to hand a collaborating LLM

Give it: `docs/MASTER_PLAN.md` (this file) + `core/src/main/resources/unbeta/manifest.json` + the one issue. Do **not** give it the whole repo. Ask for: manifest entries + one gate class + one test note. Then run `/unbeta audit`, diff, and review.

---

## 8. Phase 1 milestones

| # | Milestone | Acceptance test |
|---|---|---|
| M0 | Toolchain: Loom project builds, both subprojects, CI green, jar loads into a vanilla-plus-Fabric-API instance | `./gradlew build` passes; game boots |
| M1 | Rule engine + config + precedence chain + `/unbeta rules` | A rule flipped in `overrides.json5` visibly changes behavior at runtime |
| M2 | `/unbeta audit` and `/unbeta why` | CSV emitted for a stock 1.20.1 instance, committed as baseline |
| M3 | Manifest v1 populated and verified for entities and dimensions | `verified: true` on every entity entry with a wiki source |
| M4 | Datagen pipeline: manifest → recipe/structure/tag removals | Removed recipes absent from recipe book; ancient cities/monuments/mansions do not generate |
| M5 | Entity gate: 16 mobs remain, all others unspawnable | 3 in-game hours across all biomes, audit shows zero non-b1.8 vanilla spawns |
| M6 | Item/block gate + creative hiding + loot filtering | Creative menu contains only b1.8 content plus other mods' content |
| M7 | Dimension gate: End removed **hard** (D5), Nether intact | Strongholds generate with no portal room; eyes/frames unobtainable; Nether fully playable with only b1.8 content |
| M8 | System reverts: enchanting, brewing, trading, off-hand, spawn eggs, breeding (**not** combat, **not** advancements - D2/D3) | Manual checklist |
| M8b | Empty b1.8-style villages (D4): custom plains + desert template set, oak/cobblestone only, no villagers, no 1.14 workstations | Ten villages surveyed, zero post-b1.8 blocks |
| M9 | Compat pass: full 24-mod pack, audit diff reviewed, Moderner Beta 1.8 preset + Nostalgic Tweaks tuned | Pack boots, 2-hour playtest, no crash, no gate eating a mod's content |
| M10 | Tag `v1.0.0`, publish pack, freeze `unbeta-core` API | — |

Do M0–M2 before touching any content. The audit tooling is what makes M3–M9 tractable.

---

## 9. Risks and open questions

- **Nostalgic Tweaks overlap.** It may already implement half of §5.7 and §5.8-adjacent behavior. Audit its 400+ options *before* writing any system-revert mixin. Every overlap you avoid is a conflict you never debug.
- **Moderner Beta's Beta 1.8 preset vs. your structure gates.** Both touch structure placement. Test together early (M4), not at M9.
- **Empty villages.** 1.20.1 villages are jigsaw structures full of 1.14 blocks (barrels, lecterns, composters, bells). A b1.8 village is oak and cobblestone, uninhabited. You will likely need a custom village template set rather than a gate — budget for this, it's the largest single non-obvious task in Phase 1.
- **Advancement→achievement replacement** is UI work and may be better solved by disabling advancements entirely in Phase 1 and letting Phase 2's "Experience Orbs replace achievement notifications" system fill the gap. Consider deferring.
- **World height** — see §5.9. Default off.
- **Multiplayer config sync.** If you ever run this on a server, rules must sync client-side or the client will render creative menus and portals inconsistently. Design the config as sync-capable from day one even if you only play single-player.

---

## 10. The Phase 2 contract

This is the section that answers "will Phase 1 support what I actually want to build." For each item in your *Unbeta 1.7.3* document, the Phase 1 capability it depends on:

| Phase 2 feature | Required Phase 1 capability | Built in Phase 1? |
|---|---|---|
| Nether removed | Generic parameterized dimension gate (§5.6) | ✅ built for the End, reused |
| Obsidian: no portal, permanent colored fire, blast-immune, faster mining | `BlockBehaviorEvents.BLAST_RESISTANCE_OVERRIDE`, block-state hooks, tag `unbeta:blast_immune` | ✅ hooks exist, unused |
| Rail/powered-rail recipe buffs, abandoned surface rails | Recipe datagen pipeline (additive, same as removal) + structure datagen | ✅ |
| Minecarts with new physics | Entity behavior mixin — **new Phase 2 mixin**, no Phase 1 conflict | n/a |
| No hostile daylight burning | `unbeta:mob/daylight_burn` rule, default ON in Phase 1 | ✅ rule exists |
| Mob spawners removed | Block gate + loot/structure datagen | ✅ |
| Zombie regen, Husk/Drowned/Frozen variants | `SpawnEvents.SUBSTITUTE` + `MobBehaviorEvents.MODIFY_GOALS` + `MODIFY_ATTRIBUTES` | ✅ built, barely used |
| Skeleton bone piles that re-animate | `EntityDeathEvents.REPLACE_DROP` + `UnbetaTickScheduler` (persisted 60s timer) | ✅ |
| All spiders become cave spiders, wall-crawling | `SpawnEvents.SUBSTITUTE` + navigation override | ✅ substitution; navigation is new Phase 2 code |
| Creeper plants a bomb in your inventory | Inventory service + per-item persisted timer + `UnbetaTickScheduler` | ✅ scheduler; inventory manipulation is thin Phase 2 code |
| Siren, Will o' Wisp, Genius | Entity registration helper + spawn registration that is **not** eaten by Phase 1 gates | ✅ **guaranteed by namespace scoping, §6.3** |
| Dungeons replace strongholds | Structure gate (disable stronghold set) + structure datagen for new sets | ✅ |
| Clamboxes (block entity + "cooking" pearls) | Block entity registration helper + recipe type registration | ✅ helpers exist |
| Tree of Life, Life Blocks, Wands, teleporting structures | `UnbetaWorldState` (typed, persistent, cross-chunk) + indestructible-block hook | ✅ built in Phase 1 specifically for this |
| Classes chosen at world creation; Flint tools | Player-persistent data service + first-join event + per-class stat override hook | ⚠️ **player-data service should be added to Phase 1** — see below |
| XP orbs → lore splashscreens | Notification system (the same one that could replace advancements) | ⚠️ deferred; see §9 |

**Two additions to the Phase 1 scope, justified purely by Phase 2:**

1. A `PlayerDataService` (persistent per-player NBT with a typed key API). Phase 2's class system, Flint tool stat overrides, and creeper-bomb tracking all need it. Adding it in Phase 1 is a day; retrofitting it means touching Phase 1 mixins.
2. `UnbetaWorldState`. Life Blocks are the most technically demanding thing in your Phase 2 document — a set of blocks that teleport as a connected group, are indestructible except by their originating Wand, and persist across sessions. That needs real persistent world state with a stable identity model (Wand UUID ↔ Life Block set). Build the storage layer in Phase 1; build the Life Block logic in Phase 2.

Everything else in your Unbeta 1.7.3 document is additive content that sits cleanly on top of the Phase 1 gate, provided §6.3 is honored.

---

## Appendix A — one-paragraph brief for a collaborating LLM

> We are building `unbeta-core`, a Fabric mod for Minecraft Java 1.20.1 (Java 17, Fabric API, Yarn mappings) that makes vanilla 1.20.1 present only the content that existed in Java Beta 1.8 (September 2011), while leaving all non-`minecraft` namespace content from ~24 other Fabric mods completely untouched. Content is never unregistered — it is gated: no recipe, no loot, no spawn, no worldgen, no creative entry. Every gate is a named rule resolved through a precedence chain (manifest default → core config → other mods' overrides → user override), so a future Phase 2 mod can flip any rule without editing this mod. All mixins are cancellable `@Inject` at HEAD guarded by a rule check; `@Overwrite` is banned; rendering, chunk-building, and lighting classes are off-limits. Terrain is owned by Moderner Beta, visuals/animations by Nostalgic Tweaks, textures by the Golden Days resource pack — do not duplicate their work.
