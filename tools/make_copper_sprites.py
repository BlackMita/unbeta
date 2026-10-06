#!/usr/bin/env python3
"""
Generates the copper nugget, copper wire and wood/copper bucket sprites from vanilla's.
Re-run any time:  python3 tools/make_copper_sprites.py

Buckets: only the METAL is recoloured. The contents (water, lava, milk) are found by
comparing each filled sprite against the empty bucket - any pixel that differs is
contents and keeps vanilla's look. Brightness is preserved by mapping each pixel's
luminance onto a palette taken from a reference texture (oak planks / copper ingot).
"""
import io, os, zipfile
from PIL import Image

CLIENT = "/home/blackmita/Downloads/PrismLauncher-App/libraries/com/mojang/minecraft/1.20.1/minecraft-1.20.1-client.jar"
OUT = "content/src/main/resources/assets/unbeta-content/textures/item"

def lum(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]

with zipfile.ZipFile(CLIENT) as jar:
    def load(path):
        return Image.open(io.BytesIO(jar.read(path))).convert("RGBA")
    bucket = load("assets/minecraft/textures/item/bucket.png")
    filled = {"water": load("assets/minecraft/textures/item/water_bucket.png"),
              "lava":  load("assets/minecraft/textures/item/lava_bucket.png"),
              "milk":  load("assets/minecraft/textures/item/milk_bucket.png")}
    nugget = load("assets/minecraft/textures/item/iron_nugget.png")
    string = load("assets/minecraft/textures/item/string.png")
    planks = load("assets/minecraft/textures/block/oak_planks.png")
    ingot  = load("assets/minecraft/textures/item/copper_ingot.png")

def palette(img, outline_scale=None):
    cols = sorted({px for px in img.getdata() if px[3] > 0}, key=lum)
    if outline_scale:  # add a darker tone so outlines keep their contrast
        d = cols[0]
        cols.insert(0, (int(d[0] * outline_scale), int(d[1] * outline_scale), int(d[2] * outline_scale), 255))
    return cols

WOOD = palette(planks, outline_scale=0.45)
COPPER = palette(ingot)

def lum_range(img, mask=None):
    px = img.load()
    ls = [lum(px[x, y]) for y in range(img.height) for x in range(img.width)
          if px[x, y][3] > 0 and (mask is None or mask(x, y))]
    return min(ls), max(ls)

def remap(img, pal, lo, hi, mask=None):
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            p = px[x, y]
            if p[3] == 0 or (mask is not None and not mask(x, y)):
                continue
            t = 0.0 if hi == lo else min(1.0, max(0.0, (lum(p) - lo) / (hi - lo)))
            c = pal[int(round(t * (len(pal) - 1)))]
            px[x, y] = (c[0], c[1], c[2], p[3])
    return out

os.makedirs(OUT, exist_ok=True)
lo, hi = lum_range(bucket)   # the metal's brightness range, from the empty bucket
bpx = bucket.load()
for material, pal in (("wood", WOOD), ("copper", COPPER)):
    remap(bucket, pal, lo, hi).save(f"{OUT}/{material}_bucket.png")
    for contents, img in filled.items():
        ipx = img.load()
        is_metal = lambda x, y, ipx=ipx: bpx[x, y][3] > 0 and ipx[x, y] == bpx[x, y]
        remap(img, pal, lo, hi, is_metal).save(f"{OUT}/{material}_{contents}_bucket.png")
    print(f"{material}: bucket + water, lava, milk")

remap(nugget, COPPER, *lum_range(nugget)).save(f"{OUT}/copper_nugget.png")
remap(string, COPPER, *lum_range(string)).save(f"{OUT}/copper_wire.png")
print("copper_nugget, copper_wire")
