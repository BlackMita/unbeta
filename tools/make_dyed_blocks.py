#!/usr/bin/env python3
"""
Dyed wood and stone: builds the 16-colour blocks' models, block states, textures and loot
tables from vanilla's oak / stone ones. Re-run any time:
    python3 tools/make_dyed_blocks.py

Textures are "stained": each pixel keeps its brightness relative to the texture's average
(so the grain and speckle still read) and takes its colour from that dye's wool.
Loot: a dyed block drops its PLAIN block (oak piece; Stone gives cobblestone) - except to a
gold tool (Unbeta's silk touch) or real Silk Touch, which keep the colour. Dyed Stone, like
Stone, needs an iron pickaxe or better (or gold).
"""
import glob, io, json, os, sys, zipfile
from PIL import Image

COLORS = "white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black".split()
BASES = ["oak_planks", "oak_stairs", "oak_slab", "oak_fence", "oak_fence_gate", "oak_door", "oak_trapdoor",
         "oak_button", "oak_pressure_plate", "oak_sign", "oak_wall_sign", "stripped_oak_log", "stone"]
RES = "content/src/main/resources"
ASSETS = f"{RES}/assets/unbeta-content"
DATA = f"{RES}/data/unbeta-content"


def find_jar():
    home = os.path.expanduser("~")
    candidates = [f"{home}/Downloads/PrismLauncher-App/libraries/com/mojang/minecraft/1.20.1/minecraft-1.20.1-client.jar"]
    candidates += glob.glob(f"{home}/.gradle/caches/fabric-loom/**/minecraft-client.jar", recursive=True)
    candidates += glob.glob(f"{home}/.gradle/caches/fabric-loom/**/*1.20.1*client*.jar", recursive=True)
    candidates += glob.glob(f"{home}/.local/share/PrismLauncher/libraries/com/mojang/minecraft/1.20.1/*client*.jar")
    candidates += [f"{home}/.minecraft/versions/1.20.1/1.20.1.jar"]
    for c in candidates:
        try:
            with zipfile.ZipFile(c) as z:
                z.getinfo("assets/minecraft/textures/block/oak_planks.png")
                return c
        except (OSError, KeyError, zipfile.BadZipFile):
            continue
    sys.exit("STOP: couldn't find the Minecraft 1.20.1 client jar (looked in Prism's libraries and Gradle's cache).")


def renameable(name):
    return name.startswith("oak_") or name.startswith("stripped_oak_log") or name in ("stone", "stone_mirrored")


def rename(name, c):
    if name.startswith("stripped_oak_log"):
        return name.replace("stripped_oak_log", f"{c}_stripped_log")
    if name.startswith("oak_"):
        return f"{c}_wood_{name[4:]}"
    return f"{c}_{name}"


SILK = {"condition": "minecraft:match_tool",
        "predicate": {"enchantments": [{"enchantment": "minecraft:silk_touch", "levels": {"min": 1}}]}}
GOLD_PICK = {"condition": "minecraft:match_tool", "predicate": {"items": ["minecraft:golden_pickaxe"]}}


def loot_table(base, new):
    """Silk touch (gold tools have it) keeps the colour; anything else drops the plain block."""
    me = f"unbeta-content:{new}"
    if base == "stone":
        by_gold = {"type": "minecraft:item", "name": me, "conditions": [GOLD_PICK]}
        by_silk = {"type": "minecraft:item", "name": me, "conditions": [SILK]}
        plain = {"type": "minecraft:item", "name": "minecraft:cobblestone",
                 "conditions": [{"condition": "minecraft:survives_explosion"}]}
        return {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:alternatives", "children": [by_gold, by_silk, plain]}]}]}
    keep = {"type": "minecraft:item", "name": me, "conditions": [SILK]}
    if base == "stripped_oak_log":
        # A gold tool keeps it whole; anything else gives an oak log.
        children = [keep, {"type": "minecraft:item", "name": "minecraft:oak_log"}]
        return {"type": "minecraft:block", "pools": [{"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}],
                                                      "entries": [{"type": "minecraft:alternatives", "children": children}]}]}
    plain = {"type": "minecraft:item", "name": f"minecraft:{base}"}
    pool = {"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [keep, plain]}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}
    if base == "oak_slab":
        double = {"function": "minecraft:set_count", "count": 2.0, "add": False,
                  "conditions": [{"condition": "minecraft:block_state_property", "block": me,
                                  "properties": {"type": "double"}}]}
        for e in (keep, plain):
            e["functions"] = [dict(double)]
    if base == "oak_door":
        pool["conditions"].append({"condition": "minecraft:block_state_property", "block": me,
                                   "properties": {"half": "lower"}})
    return {"type": "minecraft:block", "pools": [pool]}


def lum(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


class Maker:
    def __init__(self, jar):
        self.z = zipfile.ZipFile(jar)
        self.wool = {}
        for c in COLORS:
            img = self.image(f"textures/block/{c}_wool.png")
            data = img.load()
            px = [data[x, y] for y in range(img.height) for x in range(img.width) if data[x, y][3] > 0]
            self.wool[c] = tuple(sum(p[i] for p in px) / len(px) for i in range(3))
        self.done = set()

    def json(self, path):
        return json.loads(self.z.read(f"assets/minecraft/{path}" if not path.startswith("data/") else path))

    def image(self, path):
        return Image.open(io.BytesIO(self.z.read(f"assets/minecraft/{path}"))).convert("RGBA")

    def write(self, path, obj):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as f:
            json.dump(obj, f, indent=2)
            f.write("\n")

    # ----------------------------------------------------------------- textures
    def stain(self, src, c):
        img = self.image(src)
        px = img.load()
        opaque = [px[x, y] for y in range(img.height) for x in range(img.width) if px[x, y][3] > 0]
        mean = sum(lum(p) for p in opaque) / max(1, len(opaque))
        tint = self.wool[c]
        for y in range(img.height):
            for x in range(img.width):
                r, g, b, a = px[x, y]
                if a == 0:
                    continue
                k = lum((r, g, b)) / mean if mean else 1.0
                px[x, y] = tuple(max(0, min(255, int(round(t * k)))) for t in tint) + (a,)
        return img

    def texture(self, ref, c):
        ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
        kind, name = path.split("/", 1)
        if ns != "minecraft" or not renameable(name):
            return ref
        new = f"{kind}/{rename(name, c)}"
        key = ("tex", new)
        if key not in self.done:
            self.done.add(key)
            out = f"{ASSETS}/textures/{new}.png"
            os.makedirs(os.path.dirname(out), exist_ok=True)
            self.stain(f"textures/{kind}/{name}.png", c).save(out)
        return f"unbeta-content:{new}"

    # ----------------------------------------------------------------- models
    def model(self, ref, c):
        ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
        kind, name = path.split("/", 1)
        if ns != "minecraft" or not renameable(name):
            return ref
        new = f"{kind}/{rename(name, c)}"
        key = ("model", new)
        if key not in self.done:
            self.done.add(key)
            m = self.json(f"models/{kind}/{name}.json")
            if "parent" in m:
                m["parent"] = self.model(m["parent"], c)
            for k, v in list(m.get("textures", {}).items()):
                if not v.startswith("#"):
                    m["textures"][k] = self.texture(v, c)
            self.write(f"{ASSETS}/models/{new}.json", m)
        return f"unbeta-content:{new}"

    def walk_states(self, node, c):
        if isinstance(node, dict):
            return {k: (self.model(v, c) if k == "model" else self.walk_states(v, c)) for k, v in node.items()}
        if isinstance(node, list):
            return [self.walk_states(v, c) for v in node]
        return node

    # ----------------------------------------------------------------- loot
    def loot(self, node, base, new):
        if isinstance(node, dict):
            return {k: (f"unbeta-content:{new}" if k == "block" and v == f"minecraft:{base}" else self.loot(v, base, new))
                    for k, v in node.items()}
        if isinstance(node, list):
            return [self.loot(v, base, new) for v in node]
        return node

    def run(self):
        # No dyed ladders any more (they fought Smart Ladders): clear out any made before.
        for pattern in (f"{ASSETS}/blockstates/*_ladder.json", f"{ASSETS}/models/*/*_ladder.json",
                        f"{ASSETS}/textures/block/*_ladder.png", f"{DATA}/loot_tables/blocks/*_ladder.json"):
            for old in glob.glob(pattern):
                os.remove(old)
        for c in COLORS:
            for base in BASES:
                new = rename(base, c)
                states = self.walk_states(self.json(f"blockstates/{base}.json"), c)
                self.write(f"{ASSETS}/blockstates/{new}.json", states)
                if base != "oak_wall_sign":
                    item = self.json(f"models/item/{base}.json")
                    if "parent" in item:
                        item["parent"] = self.model(item["parent"], c)
                    for k, v in list(item.get("textures", {}).items()):
                        item["textures"][k] = self.texture(v, c)
                    self.write(f"{ASSETS}/models/item/{new}.json", item)
                    self.write(f"{DATA}/loot_tables/blocks/{new}.json", loot_table(base, new))
            # The sign's own picture (drawn by the sign renderer, not a block model).
            out = f"{ASSETS}/textures/entity/signs/{c}_wood.png"
            os.makedirs(os.path.dirname(out), exist_ok=True)
            self.stain("textures/entity/signs/oak.png", c).save(out)
        print(f"Dyed blocks: {len(COLORS) * len(BASES)} block states, "
              f"{sum(1 for k in self.done if k[0] == 'model')} models, "
              f"{sum(1 for k in self.done if k[0] == 'tex') + len(COLORS)} textures.")


if __name__ == "__main__":
    os.chdir(os.path.dirname(os.path.abspath(__file__)) + "/..")
    Maker(find_jar()).run()
