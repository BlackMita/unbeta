# UNBETA — Session Handoff

**For:** a fresh LLM assistant with no memory of prior sessions.
**Project:** `github.com/BlackMita/unbeta` — a Fabric mod making Minecraft Java 1.20.1 play like Java Beta 1.8.
**Status at handoff:** Phase 1 functionally complete and stable. Phase 2 not started.
**Last commit:** `46370f2` on `main`.

---

## 0. Read this first

The user (BlackMita) is **not an experienced developer**. They are capable and quick, but this is roughly their second serious coding project. Practical implications:

- **Give literal, copy-pasteable commands.** Never write `THAT_PATH.csv` or `<your-file>` as a placeholder — substitute the real value. This caused real confusion.
- **One step at a time.** They will say "one step at a time" if you go too fast. Respect it.
- **They run everything; you have no machine access.** You write code, they install and build, they paste output back. Design around that loop.
- **They are on Linux Mint / Ubuntu 24.04**, project at `/home/blackmita/Desktop/Minecraft-mod-dev/unbeta`.

The user asked for this document specifically so a new session could continue without re-deriving everything. Trust it, but verify anything version-specific against their machine (see §7).

---

## 1. The three architectural rules — do not break these

These are load-bearing. Violating any one means large rework.

### Rule 1 — Nothing is ever unregistered
Minecraft 1.20.1 freezes registries after bootstrap; they are synced to clients and referenced by saved chunks and every other mod. Deleting `minecraft:villager` from the entity registry does not produce a beta game, it produces a crash log.

Content is **gated** instead. Six layers, most things need two or three:
1. No recipe 2. No loot 3. No spawn 4. No worldgen 5. Hidden from creative 6. (optionally) Use blocked

This also means every removal is **reversible at runtime by config**, which is exactly what Phase 2 needs.

### Rule 2 — Every gate is namespace-scoped to `minecraft:`
Every gate checks the registry ID's namespace first and returns "allowed" for anything that isn't in the gated set (default: only `minecraft`).

**Never write "remove all hostile mobs except the b1.8 list."** Always remove an *explicit list of vanilla IDs*. An allowlist over all namespaces would delete other mods' content today and Phase 2's own mobs tomorrow.

This was **verified empirically**: after installing Moderner Beta, `/unbeta audit` showed `moderner_beta:indev_stronghold  removed=false`. Other mods pass through untouched.

### Rule 3 — `unbeta-core` contains no Phase 2 content
Core holds only the gate machinery and vanilla rules. Phase 2 lives in the `content` module and talks to core through its public API.

---

## 2. How the rule engine works

Every gate is a named rule. Format: `<kind>/<namespace>.<path>`, e.g. `entity/minecraft.villager`, `system/unbeta.enchanting`, `dimension/minecraft.the_nether`.

**Semantics: `true` means "Unbeta has removed/disabled this."** So `dimension/minecraft.the_nether` is `false` in Phase 1 (Beta 1.8 *has* the Nether) and Phase 2 flips it to `true`.

Values resolve through a fixed precedence chain:

```
manifest.json  <  config/unbeta/core.json  <  other mods (entrypoint)  <  config/unbeta/overrides.json
```

Other mods register overrides via the `unbeta:rules` entrypoint implementing `net.unbeta.core.api.RuleProvider`. **This is how Phase 2 changes Phase 1's behaviour without editing Phase 1's code.**

Public API (stable, semver'd — Phase 2 depends on it):
```java
UnbetaApi.rules().isRemoved(ContentKind.BLOCK, id)
UnbetaApi.rules().isRemoved(EntityType<?> / Item / Block)
UnbetaApi.rules().isDimensionRemoved(RegistryKey<World>)
UnbetaApi.rules().isSystemDisabled("enchanting")
UnbetaApi.rules().isGatedNamespace(String)
UnbetaApi.rules().resolve(RuleKey)      // full provenance, powers /unbeta why
```

---

## 3. What exists and works

### Diagnostics (build these habits — they solved most problems)
| Command | Purpose |
|---|---|
| `/unbeta rules` | list every rule + resolved value |
| `/unbeta why <id>` | explain a decision incl. which precedence layer won |
| `/unbeta audit` | dump **every** registered item/block/entity/structure/dimension + gate status to CSV |
| `/unbeta features` | dump every registered **placed feature** ID to CSV |
| `/unbeta reload` | re-read `config/unbeta/overrides.json` |

CSVs land in `core/run/`.

### Gates
| Gate | File | Mechanism | Status |
|---|---|---|---|
| Recipe | `RecipeManagerMixin` + `RecipeGate` | mixin at `RecipeManager#apply` TAIL, rebuilds recipe set | ✅ removes ~897 recipes |
| Loot | `LootGate` | `LootTableEvents.REPLACE` (v2, **5 params**) | ✅ |
| Creative | `CreativeGate` | `ItemGroupEvents.MODIFY_ENTRIES_ALL` | ✅ |
| Spawn (primary) | `SpawnGate` | `BiomeModifications` REMOVALS phase | ✅ strips 59 entity types |
| Spawn (catch-all) | `SpawnCatchAll` | `ServerEntityEvents.ENTITY_LOAD`, discards gated `MobEntity` | ✅ |
| Dimension | `DimensionGate` + 3 mixins | generic, parameterised by dimension key | ✅ End removed, Nether intact |
| Feature | `FeatureGate` | `BiomeModifications` + `removeFeature` | ✅ strips ~40 placed features |
| Structures | 17 JSON overrides | `data/minecraft/worldgen/structure_set/*.json` | ✅ |
| Generated-block | `GeneratedBlockGate` | chunk-load scan → stone | ⛔ **shipped but DISABLED** (see §5) |

### The manifest is GENERATED, not hand-written
`core/src/main/resources/unbeta/manifest.json` (2443 entries) is produced by:

```bash
python3 tools/generate_manifest.py <audit.csv>
```

which inverts `tools/b18_keeplist.json` (265 blocks/items + 34 entities that Beta 1.8 had) against a `/unbeta audit` dump of the real 1.20.1 registry.

**To change what's gated, edit `tools/b18_keeplist.json` and re-run the generator. Do not hand-edit `manifest.json` — it will be overwritten.**

The inversion happens at *authoring* time and emits explicit `minecraft:` IDs. The runtime only ever consults that explicit list — never "is this on the keep-list?" — which is what preserves Rule 2.

---

## 4. Milestones completed

- **M0–M2** toolchain, rule engine + precedence chain, diagnostics
- **M4** recipe / loot / creative gates
- **M5** two-layer spawn gate
- **M6** item & block coverage (verified in-game — creative menu is beta-only)
- **M7 (core)** dimension gate, End hard-removed, Nether intact
- **M8 (partial)** swimming, breeding, vine-climbing, enchanting, brewing reverted
- **Worldgen** 16 structure sets disabled, villages trimmed to plains+desert, ~40 modern features stripped
- **M9 (partial)** Moderner Beta 3.0.0+1.20.1 installed, compat verified via audit diff

---

## 5. Known gaps — all deliberate, all documented

| Gap | Why it's open |
|---|---|
| **Lush caves still generate** | Placed by the biome carver, not a strippable placed feature. The gate that removed them (chunk-load block scan) **froze the game twice** by stalling the server thread, so it was disabled. Blocks remain gated (no drops, no creative) — just visible. |
| **Deepslate below y=0** | Direct consequence of decision D1 (keep 1.20 world height −64→320). Beta's floor was y=0; those 64 layers have no beta-authentic filler. |
| **Stronghold portal room generates (inert)** | D5 layer 4. Deferred, and entangled with Phase 2's plan to replace strongholds with custom dungeons. |
| **Off-hand slot still present** | Fiddly (needs packet mixin + slot blocking). Never attempted. |
| **Empty villages use 1.14 blocks** | D4 / M8b — the largest untackled Phase 1 task. Needs a custom village template set. |
| **Baby animals from natural spawns** | Breeding disabled, but ~5% of natural passive spawns are still babies. |

### Locked decisions (in `docs/DECISIONS.md`)
- **D1** World height stays −64→320. Closed, not deferred.
- **D2** 1.9 combat revert delegated to Nostalgic Tweaks. Core ships the rule key only.
- **D3** Advancements→achievements deferred to Phase 2.
- **D4** Empty b1.8-style villages are in Phase 1 scope (unfinished).
- **D5** The End is removed *hard*, not made inert.
- **D6** Code is generated for the user, not specified.

---

## 6. The four tasks the user wants done BEFORE Phase 2 officially begins

The user was explicit that Phase 2 doesn't start until these are handled.

### Task 1 — Add back ~24 preferred Fabric mods and check nothing breaks
This is the real **M9 compat pass**. Moderner Beta is already in (it was one of the two dozen).

Named so far: Immersive Paintings, AppleSkin, WTHIT (What The Hell Is That), AmbientSounds, Presence Footsteps, Moderner Beta. Ask for the full list.

**Method:**
1. Dev-mode mods go in `core/run/mods/`.
2. Add in **small batches** (3–5), not all at once, so a failure is attributable.
3. After each batch: `/unbeta audit`, then diff against the previous CSV:
   ```bash
   cd core/run
   diff <(cut -d, -f1,2 OLD.csv | sort) <(cut -d, -f1,2 NEW.csv | sort)
   ```
4. Critical check — confirm no other mod's content got gated:
   ```bash
   awk -F, 'NR>1 && $3!="minecraft" {print $1, $2, "removed="$5}' NEW.csv
   ```
   Every non-`minecraft` row must show `removed=false`.
5. Also recommended by the master plan but not yet installed: **Nostalgic Tweaks** (owns lighting/fog/animations/old combat — D2 delegates to it) and the **Golden Days** resource pack (beta textures).

**Watch for:** mixin conflicts at startup, and performance. Note Sodium/Iris if used — `unbeta-core` deliberately avoids rendering/chunk-building/lighting mixins to stay compatible.

### Task 2 — Confirm zombie villagers cannot appear
The user heard one in an abandoned village several builds ago and it was never investigated. **This is an open, unverified bug.**

What we know:
- `minecraft:zombie_villager` **is** marked `remove` in the manifest.
- `SpawnCatchAll` discards gated entities on `ENTITY_LOAD` — and `ZombieVillagerEntity` **is** a `MobEntity`, so it should be caught.
- Precedent: a `/summon minecraft:goat` produced the "Summoned new Goat" log line but **no goat persisted** — the catch-all worked.
- **Likely explanation:** the entity spawned, played a sound, and was discarded in the same tick — the same one-tick window seen with the goat. Audible but harmless.
- **Secondary issue:** abandoned/"zombie" villages with cobwebs are a **1.10+** feature. Beta 1.8 villages were plain and empty, not spooky. Even with no zombie villagers, the *abandoned village* variant is anachronistic and should probably be suppressed as part of D4/M8b.

**Diagnostic plan:**
```
/summon minecraft:zombie_villager ~ ~ ~
```
Listen and watch. Then check the log. If it's discarded, this is a non-issue (sound only). If it *persists*, the catch-all has a real gap and needs investigation.
Set `verboseGateLogging: true` in `config/unbeta/core.json` to log every discard.

### Task 3 — Add the user's QoL datapacks (incl. a dynamic-light datapack)
Datapacks go in `core/run/saves/<world>/datapacks/` (per-world) or can be loaded globally.

**Things to check, in priority order:**
- **Technical entities.** The manifest marks `item_display`, `block_display`, `text_display`, `interaction`, and `armor_stand` as `remove`. Many QoL datapacks are built on these. **Good news:** `SpawnCatchAll` only discards `MobEntity` instances, and none of those are `MobEntity` — so they should survive. But their **items** are gated (no creative entry, no recipe), which could matter if a pack expects the player to obtain one. `marker` is already kept.
- **If a datapack breaks**, the fix is a rule override, not a code change. Add to `config/unbeta/overrides.json`:
  ```json
  { "rules": { "entity/minecraft.armor_stand": false } }
  ```
  Then `/unbeta reload`.
- Dynamic light specifically: if it's the datapack kind it likely uses display entities or markers; if it's the *mod* kind (LambDynamicLights) it belongs in Task 1 instead. Clarify which the user has.

### Task 4 — MAYBE re-enable the un-removable lush blocks
The user is considering letting lush-cave blocks (moss, glow berries, azalea, dripleaf) be obtainable and appear in creative, on the logic that if they're going to be visible in the world anyway, they may as well be collectable.

**This is a judgement call, and there's a real argument on each side. Present both, don't just implement it:**

- **For:** consistency. A block you can see but can't touch is worse than either extreme. The user already voiced this exact discomfort.
- **Against:** it breaks Phase 1's honest claim to be "Beta 1.8," and the user *already decided* (documented) that these should be gated in Phase 1 and deliberately re-introduced in Phase 2 as framed new content. When asked which modern blocks had Phase 2 plans, they picked **only tuff**.

**If they proceed, do it correctly:** un-gate via `config/unbeta/overrides.json` rules, **not** by editing `b18_keeplist.json`. That keeps the manifest an honest record and makes the exception visible and reversible.

---

## 7. Working practices that proved essential

### ⚠️ NEVER trust remembered API names for 1.20.1 — verify against the user's jars
This was the single highest-value practice. Nearly every batch had exactly one wrong API name, and `javap` caught them all before wasted builds.

**The pattern:**
```bash
JAR=$(find ~/.gradle -name 'fabric-<module>*.jar' ! -name '*-sources.jar' 2>/dev/null | head -1)
javap -cp "$JAR" 'net.fabricmc.fabric.api.<pkg>.SomeClass$InnerInterface'
```
Obfuscated names decode as: `class_1297`=Entity, `class_3218`=ServerWorld, `class_2818`=WorldChunk, `class_2960`=Identifier, `class_52`=LootTable, `class_5321`=RegistryKey, `class_6796`=PlacedFeature, `class_3300`=ResourceManager, `class_60`=LootManager.

If `javap` returns "class not found," the *package* is wrong — search all jars:
```bash
for j in $(find ~/.gradle -name 'fabric-*.jar' ! -name '*-sources.jar' 2>/dev/null); do
  if unzip -l "$j" 2>/dev/null | grep -q 'ClassName'; then echo "FOUND: $j"; unzip -l "$j" | grep 'ClassName'; fi
done
```

**Real mistakes this caught:**
- `LootTableEvents.REPLACE` has **5** params in v2, not 4 (missing `original`)
- `ServerEntityEvents` is in `api.event.lifecycle.v1`, **not** `api.entity.event.v1`
- `ServerChunkEvents` has only `CHUNK_LOAD`/`CHUNK_UNLOAD` — **no** `CHUNK_GENERATE`
- 1.20.1 `RecipeManager` uses `Recipe<?>`, not `RecipeEntry<?>` (that's 1.20.2+)

For Minecraft's own classes, `javap` is useless (the dev jar is intermediary-obfuscated) — **just build and let the compiler name the wrong method.** Faster than hunting.

### Generate lists from the running game, never from memory
Two disasters were avoided by this:
- The **manifest** came from `/unbeta audit` (real registry), not a recalled list of 1300 item IDs.
- The **feature list** came from `/unbeta features` after 11 of 33 guessed names turned out not to exist.

If you need a list of vanilla IDs, add a command that dumps it. Do not guess.

### Make gates fail soft
`removeFeature` **throws** on an unknown key rather than returning false — one bad name in a user-editable config list crashed world creation. Every gate that reads user config must wrap its per-item work in try/catch and report skipped entries once, not crash.

### Performance: do not scan blocks on chunk load
`GeneratedBlockGate` scanned chunks on `CHUNK_LOAD` and stalled the server thread badly enough to freeze world saving **twice**, even after adding a section-palette pre-check. If lush-cave cleanup is retried, do it **at generation time** (mixin on chunk generation) or via a custom biome source — never a per-load scan.

Also: when it did run, it turned gated *plants* into stone stubs. The correct behaviour is gated **non-solid** blocks → **air**, gated **solid** blocks → **stone**.

### Delivery loop
Package changed files as a zip preserving repo-relative paths, so the user can:
```bash
cd /home/blackmita/Desktop/Minecraft-mod-dev/unbeta
unzip -o ~/Downloads/<delta>.zip -d /home/blackmita/Desktop/Minecraft-mod-dev/unbeta
./gradlew build
```
Then have them verify installation before building:
```bash
ls -la core/src/main/java/net/unbeta/core/gates/<NewFile>.java
grep -n "<NewClass>" core/src/main/java/net/unbeta/core/UnbetaCore.java
```
(One whole debugging cycle was lost because a zip was never downloaded and the missing log line was misread as a wiring bug.)

---

## 8. Environment

```
Project:   /home/blackmita/Desktop/Minecraft-mod-dev/unbeta
Repo:      github.com/BlackMita/unbeta   (branch: main)
OS:        Ubuntu 24.04 / Linux Mint, kernel 6.8
Java:      OpenJDK 21 (compiles to release 17 — this works, do not "fix" it)
Gradle:    wrapper 8.14.4 (system gradle 8.14.4 via snap)
Loom:      1.10.5   ← 1.6 does NOT work with Gradle 8.14 (Problems.forNamespace removed)
Minecraft: 1.20.1
Yarn:      1.20.1+build.10
Loader:    0.15.11
Fabric API:0.92.2+1.20.1
Mods in dev: core/run/mods/  →  moderner_beta 3.0.0+1.20.1
```

Commands:
```bash
./gradlew build                # compile both modules
./gradlew :core:runClient      # launch dev client (first run downloads assets)
git add . && git commit -m "..." && git push
```

Note: the user re-enters their GitHub Personal Access Token on each push. `git config --global credential.helper store` was suggested but not confirmed as applied.

Moderner Beta config quirk: `config/moderner_beta.json` holds only colors + a `defaultSettingsPreset` pointer. The deepslate/farlands/cave toggles live **inside a preset**, set per-world in the world-creation UI, and this version offers no way to name/save a custom preset. Proper fix is shipping the preset via the future `unbeta-pack` (packwiz), not hand-tuning. **Do not spend more time reverse-engineering Moderner Beta's preset system.**

---

## 9. Phase 2 preview

Phase 2 is "Minecraft Unbeta 1.7.3" — the user's own design document (they have it; ask for it). Highlights: Nether removed, colored permanent obsidian fire, rebuilt hostile mobs (creepers plant bombs in your inventory, skeletons leave re-animating bone piles), new mobs (Siren, Will o' Wisp, Genius), dungeons replacing strongholds, Clamboxes, a Tree of Life with teleporting indestructible "Life Blocks," and an Origins-style class system with Flint tools.

**Phase 1 already built the hooks Phase 2 needs:**
- Generic `DimensionGate` — removing the Nether is **one uncommented line** in `content/src/main/java/net/unbeta/content/ContentRules.java`
- `NetherPortalBlockMixin` — already written, currently inert by design
- `system/unbeta.mob_daylight_burn` rule — exists, defaults to vanilla, Phase 2 flips it
- The `unbeta:rules` entrypoint + `ContentRules` scaffold (all examples commented out)

**Flagged design conflict:** Phase 2 wants "Frozen Zombies for biomes that snow," but **Beta 1.8 removed snowy biomes entirely** (they returned in 1.0.0). Resolve before Phase 2 mob work: (a) allow 1.0-era snowy biomes as an exception, (b) re-enable them in Moderner Beta's preset, or (c) tie the mob to snow-layer presence/altitude instead of biome.

**Also undecided:** whether Phase 2's underground dungeons *repurpose* `StrongholdGenerator` or are brand-new jigsaw structures. This determines whether the deferred stronghold-portal-room work (D5 layer 4) is useful or throwaway. The floating-island dungeons almost certainly need custom structure code regardless.

Two services were added to Phase 1 scope *specifically* for Phase 2 and should be built before Life Blocks / Classes work: a `PlayerDataService` (persistent per-player NBT) and `UnbetaWorldState` (typed persistent cross-chunk world state). Check whether they exist yet — as of this handoff they were planned but **not implemented**.

---

## Appendix — one-paragraph brief

> `unbeta-core` is a Fabric mod for Minecraft 1.20.1 (Java 17, Yarn mappings, Fabric API 0.92.2, Loom 1.10.5) that makes vanilla 1.20.1 present only content that existed in Java Beta 1.8, while leaving all non-`minecraft` namespace content from other mods untouched. Content is never unregistered — it is gated: no recipe, no loot, no spawn, no worldgen, no creative entry. Every gate is a named rule resolved through a precedence chain (manifest → core config → other mods' overrides → user overrides), so a Phase 2 mod can flip any rule without editing core. All mixins are cancellable `@Inject` at HEAD guarded by a rule check; `@Overwrite` is banned; rendering, chunk-building and lighting classes are off-limits. Terrain is owned by Moderner Beta, visuals by Nostalgic Tweaks, textures by the Golden Days resource pack — do not duplicate their work. Verify every 1.20.1 API name against the user's jars with `javap` before writing code that depends on it.
