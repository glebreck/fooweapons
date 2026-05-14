# tools/generate_textures.py
# Generates a 16x16 dark-gray pistol texture with simple banding.
# Run: python tools/generate_textures.py
from PIL import Image

DARK = (45, 45, 50, 255)
MID = (75, 75, 80, 255)
LIGHT = (110, 110, 115, 255)
HIGHLIGHT = (160, 160, 165, 255)

img = Image.new("RGBA", (16, 16), DARK)
px = img.load()

for x in range(16):
    for y in range(16):
        if (x + y) % 4 == 0:
            px[x, y] = MID
        if y == 7 or y == 8:
            px[x, y] = LIGHT
        if (x == 0 or x == 15) and 4 <= y <= 11:
            px[x, y] = HIGHLIGHT

img.save("resourcepack/assets/fooweapons/textures/item/pistol_01.png")
print("Wrote pistol_01.png")
