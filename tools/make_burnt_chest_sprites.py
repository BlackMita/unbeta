#!/usr/bin/env python3
"""
Burnt Chest textures, recoloured from vanilla's chest. Re-run any time:
    python3 tools/make_burnt_chest_sprites.py

Wood becomes charcoal-brown (brightness kept, so the grain still reads); the grey iron
latch is detected by its low saturation and just darkened, so it still looks like metal.
Tweak DARK / LIGHT / METAL_DIM below to taste.
"""
import colorsys, io, os, zipfile
from PIL import Image

CLIENT = "/home/blackmita/Downloads/PrismLauncher-App/libraries/com/mojang/minecraft/1.20.1/minecraft-1.20.1-client.jar"
RES = "content/src/main/resources/assets/unbeta-content/textures"
DARK = (22, 18, 16)     # deepest charcoal
LIGHT = (104, 80, 60)   # lightest scorched brown
METAL_DIM = 0.62

def burn(img):
    img = img.convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255
            if s < 0.15:  # the iron latch
                v = int(lum * 255 * METAL_DIM)
                px[x, y] = (v, v, v, a)
            else:
                t = min(1.0, lum * 1.25)
                px[x, y] = tuple(int(DARK[i] + (LIGHT[i] - DARK[i]) * t) for i in range(3)) + (a,)
    return img

with zipfile.ZipFile(CLIENT) as jar:
    load = lambda p: Image.open(io.BytesIO(jar.read(p)))
    os.makedirs(f"{RES}/entity/chest", exist_ok=True)
    os.makedirs(f"{RES}/block", exist_ok=True)
    for src, dst in (("normal", "burnt"), ("normal_left", "burnt_left"), ("normal_right", "burnt_right")):
        burn(load(f"assets/minecraft/textures/entity/chest/{src}.png")).save(f"{RES}/entity/chest/{dst}.png")
    burn(load("assets/minecraft/textures/block/oak_planks.png")).save(f"{RES}/block/burnt_chest_particle.png")
print("burnt chest textures: single, left, right + break particles")
