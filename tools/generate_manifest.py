#!/usr/bin/env python3
"""
Generate unbeta-core's manifest.json by inverting a Beta 1.8 keep-list against the
actual contents of a Minecraft 1.20.1 registry dump.

    python3 tools/generate_manifest.py <audit.csv> [-o core/src/main/resources/unbeta/manifest.json]

The audit CSV comes from running /unbeta audit in-game. That file is an authoritative
enumeration of everything registered on YOUR instance, which beats any hand-written
list of vanilla IDs.

IMPORTANT ARCHITECTURE NOTE
---------------------------
This script inverts a keep-list into an explicit remove-list at AUTHORING time.
The generated manifest contains explicit minecraft: IDs, and the running game only
ever consults that explicit list. The runtime never asks "is this on the keep-list?"
- which is what would break other mods. Do not move this logic into the mod.

Only rows whose namespace is `minecraft` are considered. Anything from another mod
present in the CSV is ignored entirely and never written to the manifest.
"""

import argparse
import csv
import json
import os
import sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_KEEPLIST = os.path.join(HERE, "b18_keeplist.json")
DEFAULT_OUT = os.path.join(HERE, "..", "core", "src", "main", "resources", "unbeta", "manifest.json")

# System rules are not derivable from a registry dump, so they are carried over
# verbatim from the existing manifest (or seeded here if none exists).
SEED_SYSTEMS = [
    ("unbeta:enchanting", "remove", "1.0.0", None),
    ("unbeta:brewing", "remove", "1.0.0",
     "Only Regeneration (golden apple), Poison (cave spider) and Hunger (raw chicken/rotten flesh) survive."),
    ("unbeta:trading", "remove", "1.3", None),
    ("unbeta:raids", "remove", "1.14", None),
    ("unbeta:breeding", "remove", "1.2",
     "b1.8 animals do not despawn (so they are capturable) but cannot be bred."),
    ("unbeta:baby_mobs", "remove", "1.2", None),
    ("unbeta:offhand", "remove", "1.9", None),
    ("unbeta:shields", "remove", "1.9", None),
    ("unbeta:elytra", "remove", "1.9", None),
    ("unbeta:swimming", "remove", "1.13", None),
    ("unbeta:spawn_eggs", "remove", "1.1", None),
    ("unbeta:recipe_book", "remove", "1.12", None),
    ("unbeta:adventure_mode", "remove", "1.3", None),
    ("unbeta:spectator_mode", "remove", "1.8", None),
    ("unbeta:climbable_vines", "remove", "1.0.0", "Vines exist in b1.8 but cannot be climbed."),
    ("unbeta:attack_cooldown", "defer", "1.9", "DECISION D2: owned by Nostalgic Tweaks."),
    ("unbeta:sweep_attack", "defer", "1.9", "DECISION D2: owned by Nostalgic Tweaks."),
    ("unbeta:advancements", "defer", "1.12", "DECISION D3: deferred to Phase 2."),
    ("unbeta:world_height_clamp", "keep", None,
     "DECISION D1: CLOSED. World height stays -64..320."),
    ("unbeta:hunger", "keep", "b1.8", "Hunger IS a Beta 1.8 feature. Do not remove."),
    ("unbeta:sprinting", "keep", "b1.8", "Sprinting IS a Beta 1.8 feature. Do not remove."),
    ("unbeta:experience_orbs", "keep", "b1.8", "Orbs exist; XP has no use in b1.8."),
    ("unbeta:creative_mode", "keep", "b1.8", "Creative mode and flight were added IN b1.8."),
    ("unbeta:mob_daylight_burn", "keep", "b1.8",
     "Phase 2 flips this to remove. Hook built in Phase 1, default ON."),
]

KINDS_FROM_KEEPLIST = {
    "item": "blocks_and_items",
    "block": "blocks_and_items",
    "entity": "entities",
    "structure": "structures",
    "dimension": "dimensions",
}


def load_keeplist(path):
    with open(path) as f:
        k = json.load(f)
    return {
        "blocks_and_items": set(k["blocks_and_items"].keys()),
        "entities": set(k["entities"].keys()),
        "structures": set(k["structures"]),
        "dimensions": set(k["dimensions"]),
        "_raw": k,
    }


def load_audit(path):
    """Yield (kind, namespace, path) for every minecraft: row in the audit CSV."""
    rows = []
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            kind = (row.get("kind") or "").strip()
            ident = (row.get("id") or "").strip()
            if not kind or not ident or ":" not in ident:
                continue
            ns, _, p = ident.partition(":")
            if ns != "minecraft":
                continue  # never our business
            rows.append((kind, ns, p))
    return rows


def carry_over_notes(existing_path):
    """Preserve verified flags and notes from a previous manifest, keyed by id+kind."""
    prior = {}
    if not os.path.exists(existing_path):
        return prior
    try:
        with open(existing_path) as f:
            d = json.load(f)
        for e in d.get("entries", []):
            prior[(e.get("id"), e.get("kind"))] = e
    except Exception as ex:
        print(f"  ! could not read prior manifest ({ex}); starting fresh", file=sys.stderr)
    return prior


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("audit_csv", help="CSV produced by /unbeta audit")
    ap.add_argument("-k", "--keeplist", default=DEFAULT_KEEPLIST)
    ap.add_argument("-o", "--out", default=DEFAULT_OUT)
    ap.add_argument("--dry-run", action="store_true", help="print stats, write nothing")
    args = ap.parse_args()

    keep = load_keeplist(args.keeplist)
    rows = load_audit(args.audit_csv)
    prior = carry_over_notes(args.out)

    if not rows:
        print("No minecraft: rows found in that CSV. Is it the right file?", file=sys.stderr)
        return 1

    entries = []
    stats = Counter()
    seen = set()

    for kind, ns, path in rows:
        bucket = KINDS_FROM_KEEPLIST.get(kind)
        if bucket is None:
            continue                      # kinds we do not gate (e.g. system rows)
        ident = f"{ns}:{path}"
        if (ident, kind) in seen:
            continue
        seen.add((ident, kind))

        kept = path in keep[bucket]
        action = "keep" if kept else "remove"
        stats[(kind, action)] += 1

        entry = {"id": ident, "kind": kind, "action": action}

        # carry over human-authored metadata from the previous manifest
        old = prior.get((ident, kind))
        if old:
            for field in ("introducedIn", "note", "verified", "mechanisms", "phase2Action"):
                if field in old and old[field] not in (None, "", [], False):
                    entry[field] = old[field]

        # attach keep-list annotations
        if kept and bucket == "blocks_and_items":
            meta = keep["_raw"]["blocks_and_items"].get(path, {})
            if meta.get("note") and "note" not in entry:
                entry["note"] = meta["note"]
            if meta.get("confidence") == "low":
                entry["verified"] = False
                entry["note"] = meta.get("note", "Low confidence - verify against minecraft.wiki")
        if kept and bucket == "entities":
            note = keep["_raw"]["entities"].get(path)
            if note and "note" not in entry:
                entry["note"] = note

        entry.setdefault("verified", False)
        if action == "remove":
            entry.setdefault("mechanisms", {
                "item": ["recipe_gate", "loot_filter", "creative_hide"],
                "block": ["recipe_gate", "loot_filter", "creative_hide"],
                "entity": ["spawn_gate", "loot_filter", "creative_hide"],
                "structure": ["structure_set_datagen"],
                "dimension": ["dimension_gate"],
            }[kind])
        entries.append(entry)

    # carry the system rules across untouched
    sys_prior = [e for (i, k), e in prior.items() if k == "system"]
    if sys_prior:
        entries.extend(sorted(sys_prior, key=lambda e: e["id"]))
        stats[("system", "carried")] = len(sys_prior)
    else:
        for sid, action, ver, note in SEED_SYSTEMS:
            e = {"id": sid, "kind": "system", "action": action, "verified": True}
            if ver:
                e["introducedIn"] = ver
            if note:
                e["note"] = note
            entries.append(e)
            stats[("system", action)] += 1

    entries.sort(key=lambda e: (e["kind"], e["id"]))

    manifest = {
        "schemaVersion": 1,
        "generatedFor": "Minecraft 1.20.1 / Fabric",
        "target": "Java Edition Beta 1.8 (Adventure Update Part 1, 2011-09-14)",
        "generatedBy": "tools/generate_manifest.py inverting tools/b18_keeplist.json "
                       "against a /unbeta audit registry dump",
        "readme": "DO NOT HAND-EDIT. Edit tools/b18_keeplist.json and re-run the generator. "
                  "Anything in vanilla 1.20.1 and absent from the keep-list is action=remove.",
        "actions": {
            "keep": "present in Beta 1.8; do nothing",
            "remove": "gate it: no recipe, no loot, no spawn, no worldgen, no creative entry",
            "defer": "should be gone, but another mod owns it; ship the rule key, no implementation",
        },
        "entries": entries,
    }

    print("\n  Manifest generation summary")
    print("  " + "-" * 46)
    for kind in ("entity", "item", "block", "structure", "dimension", "system"):
        k = stats.get((kind, "keep"), 0)
        r = stats.get((kind, "remove"), 0)
        c = stats.get((kind, "carried"), 0)
        if k or r or c:
            extra = f"  carried {c}" if c else ""
            print(f"  {kind:<10} keep {k:>5}   remove {r:>5}{extra}")
    print("  " + "-" * 46)
    print(f"  total entries: {len(entries)}\n")

    if args.dry_run:
        print("  (dry run - nothing written)")
        return 0

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")
    print(f"  wrote {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
