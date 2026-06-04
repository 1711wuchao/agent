from pathlib import Path
from PIL import Image

src = Path(r"C:\Users\amc\Desktop\D4A43F3B6A1EBBBFEFEAD56CD0D53E88.png")
out = Path(r"C:\Users\amc\Documents\agent—Contract\ocr_slices")
out.mkdir(exist_ok=True)

for path in out.glob("slice_*.png"):
    path.unlink()

img = Image.open(src).convert("RGB")
x1, x2 = 350, 1260
height = 900
overlap = 40
scale = 2

count = 0
y = 0
while y < img.height:
    crop = img.crop((x1, y, x2, min(y + height, img.height)))
    crop = crop.resize(
        (crop.width * scale, crop.height * scale),
        Image.Resampling.LANCZOS,
    )
    crop.save(out / f"slice_{count:03d}.png")
    count += 1
    y += height - overlap

print(f"slices {count} dir {out}")
