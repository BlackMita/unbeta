#!/usr/bin/env python3
"""
Build data/unbeta-content/tags/blocks/scorched.json from the Burnt mod's block list.

Rule: every block the Burnt mod adds counts as scorched ground, EXCEPT soot stains
(sooty_*, soot_block) and blocks that aren't burnt ground at all (flags, fire barrels,
tools, technical blocks). Plus vanilla fire and soul fire. Entries are optional, so the
tag loads fine even if Burnt is updated or removed. Re-run after updating Burnt:
    python3 tools/gen_scorched_tag.py
"""
import glob, json, re, zipfile

MODS = "/home/blackmita/Downloads/PrismLauncher-App/instances/1.20.1 Unbeta TEST/minecraft/mods"
OUT = "content/src/main/resources/data/unbeta-content/tags/blocks/scorched.json"

EXCLUDE_EXACT = {"soot_block", "fire_barrel", "fire_barrel_active", "fire_barrel_blast_block",
                 "firestarter", "smoke_detector", "splash", "fire_set_block",
                 "fragile_magma", "blast_leaves"}

def excluded(name):
    return (name in EXCLUDE_EXACT
            or name.startswith("sooty_")      # smoke stains, not burnt ground
            or name.endswith("_flag"))        # decorative flags

jar_path = sorted(glob.glob(MODS + "/burnt*.jar"))[0]
with zipfile.ZipFile(jar_path) as jar:
    names = sorted({m.group(1) for n in jar.namelist()
                    if (m := re.match(r"assets/burnt/blockstates/(.+)\.json$", n))})

included = [n for n in names if not excluded(n)]
dropped = [n for n in names if excluded(n)]

values = [{"id": "minecraft:fire", "required": False},
          {"id": "minecraft:soul_fire", "required": False}]
values += [{"id": f"burnt:{n}", "required": False} for n in included]

import os
os.makedirs(os.path.dirname(OUT), exist_ok=True)
json.dump({"replace": False, "values": values}, open(OUT, "w"), indent=2)
print(f"from {jar_path}")
print(f"scorched: {len(included)} Burnt blocks + vanilla fire/soul fire")
print("excluded:", " ".join(dropped))
