# Unbeta

Makes Minecraft Java **1.20.1** (Fabric) present only the content that existed in
**Java Beta 1.8** (Adventure Update Part 1, 14 September 2011) — while leaving
content from every other mod completely untouched.

Full plan: [`docs/MASTER_PLAN.md`](docs/MASTER_PLAN.md).

## Status

**Phase 1, milestones M0–M2.** The rule engine, manifest and diagnostics exist.
No content is gated yet — the gates themselves (M4–M8) are the next work.

> ⚠️ **This code has not been compiled.** It was written without access to the
> Minecraft jar or Yarn mappings. Expect a small number of import/signature fixes
> on the first `./gradlew build`. Everything is written against 1.20.1 Yarn names.

## First run

```bash
gradle wrapper --gradle-version 8.6   # once; the wrapper is not committed
./gradlew build
./gradlew :core:runClient
```

Then in game:

```
/unbeta rules            list every rule and its resolved value
/unbeta why minecraft:villager
/unbeta audit            dump every registered item/block/entity + gate status to CSV
/unbeta reload           re-read config/unbeta/overrides.json
```

## The three rules that must not be broken

1. **Nothing is ever unregistered.** Registries are frozen and shared; deleting
   entries crashes the game and breaks saves. Content is *gated* instead:
   no recipe, no loot, no spawn, no worldgen, no creative entry.
2. **Every gate is namespace-scoped to `minecraft:`.** Never write "remove all
   hostile mobs except the b1.8 list" — an allowlist over all namespaces would
   delete Immersive Paintings' paintings today and Phase 2's own mobs tomorrow.
   Always remove an *explicit list of vanilla IDs*.
3. **`unbeta-core` contains no Phase 2 content.** Only gate machinery and vanilla
   rules.

## How a rule resolves

```
manifest.json  <  config/unbeta/core.json  <  other mods  <  config/unbeta/overrides.json
```

`true` means *Unbeta has removed this*. So `dimension/minecraft.the_nether` is
`false` in Phase 1 (Beta 1.8 has the Nether) and Phase 2 flips it to `true` with
one line in `content/…/ContentRules.java` — no change to core.

## Layout

```
core/     the gate engine. Public API in net.unbeta.core.api — semver'd, Phase 2 depends on it.
content/  Phase 2. Empty scaffold for now.
docs/     the master plan, the beta reference, and committed audit CSVs.
```

## Division of labour

Unbeta does **not** reimplement nostalgia. One owner per subsystem:

| Subsystem | Owner |
|---|---|
| Terrain, biomes, caves, ore | Moderner Beta (has a Beta 1.8 preset) |
| Lighting, fog, sky, animations, screens | Nostalgic Tweaks |
| Textures, sounds, music | Golden Days resource pack |
| **What can exist, spawn, drop, craft, generate** | **unbeta-core** |
| New content | unbeta-content (Phase 2) |
