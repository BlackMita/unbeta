#!/usr/bin/env python3
"""
Generate the green 'corrupted' animal textures from vanilla's.

Re-run after changing DESATURATE / TINT to retune the look:
    python3 tools/tint_corrupted.py
Placeholder art until real Blockbench textures replace it.
"""
import io, os, zipfile
from PIL import Image

CLIENT = "/home/blackmita/Downloads/PrismLauncher-App/libraries/com/mojang/minecraft/1.20.1/minecraft-1.20.1-client.jar"
OUT = "content/src/main/resources/assets/unbeta-content/textures/entity/corruption"
SOURCES = {
    "zombie_cow":     "assets/minecraft/textures/entity/cow/cow.png",
    "zombie_pig":     "assets/minecraft/textures/entity/pig/pig.png",
    "zombie_sheep":   "assets/minecraft/textures/entity/sheep/sheep.png",
    "zombie_chicken": "assets/minecraft/textures/entity/chicken.png",
}
DESATURATE = 0.5              # 0 = keep original colours, 1 = fully grey before tinting
TINT = (0.62, 0.92, 0.50)     # multiplier per channel: sickly zombie green

def corrupt(img):
    img = img.convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            out = []
            for c, t in zip((r, g, b), TINT):
                v = (c * (1 - DESATURATE) + lum * DESATURATE) * t
                out.append(max(0, min(255, int(v))))
            px[x, y] = (out[0], out[1], out[2], a)
    return img

os.makedirs(OUT, exist_ok=True)
with zipfile.ZipFile(CLIENT) as jar:
    for name, path in SOURCES.items():
        img = Image.open(io.BytesIO(jar.read(path)))
        corrupt(img).save(os.path.join(OUT, name + ".png"))
        print(f"{name}.png  <- {path}  {img.size}")
