#!/usr/bin/env python3
"""Seared Flesh sprite: vanilla rotten flesh, darkened to a charred brown.
Re-run after changing DESATURATE / TINT:  python3 tools/tint_seared_flesh.py"""
import io, zipfile
from PIL import Image

CLIENT = "/home/blackmita/Downloads/PrismLauncher-App/libraries/com/mojang/minecraft/1.20.1/minecraft-1.20.1-client.jar"
OUT = "content/src/main/resources/assets/unbeta-content/textures/item/seared_flesh.png"
DESATURATE = 0.35
TINT = (0.62, 0.42, 0.28)   # charred brown

with zipfile.ZipFile(CLIENT) as jar:
    img = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/item/rotten_flesh.png"))).convert("RGBA")
px = img.load()
for y in range(img.height):
    for x in range(img.width):
        r, g, b, a = px[x, y]
        lum = 0.299 * r + 0.587 * g + 0.114 * b
        px[x, y] = tuple(max(0, min(255, int((c * (1 - DESATURATE) + lum * DESATURATE) * t)))
                         for c, t in zip((r, g, b), TINT)) + (a,)
img.save(OUT)
print("seared_flesh.png written", img.size)
