import os
from PIL import Image

def resize_icon(src, dest, size):
    img = Image.open(src).convert("RGBA")
    img = img.resize((size, size), Image.Resampling.LANCZOS)
    img.save(dest)

src = r"C:\Users\karth\.gemini\antigravity-ide\brain\7e5a5dc2-7047-4072-b7b4-5cd9fc34ffea\.user_uploaded\media_1790834366908.png"
base_dir = r"C:\Users\karth\Desktop\notesapp\notes\app\src\main\res"

sizes = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192
}

# Generate standard legacy PNGs
for density, size in sizes.items():
    folder = os.path.join(base_dir, f"mipmap-{density}")
    os.makedirs(folder, exist_ok=True)
    resize_icon(src, os.path.join(folder, "ic_launcher.png"), size)
    resize_icon(src, os.path.join(folder, "ic_launcher_round.png"), size)

def make_adaptive_foreground(src, dest, size, inner):
    img = Image.open(src).convert("RGBA")
    img = img.resize((inner, inner), Image.Resampling.LANCZOS)
    bg = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    offset = (size - inner) // 2
    bg.paste(img, (offset, offset), img)
    bg.save(dest)

# Generate Adaptive Foreground PNGs
for density, size in sizes.items():
    scale = 2.25
    adaptive_size = int(size * scale)
    inner_size = int(size * 1.5) # Slight padding so the corners aren't completely chopped off by circle mask
    folder = os.path.join(base_dir, f"mipmap-{density}")
    make_adaptive_foreground(src, os.path.join(folder, "ic_launcher_foreground.png"), adaptive_size, adaptive_size)

print("Icons generated successfully!")
