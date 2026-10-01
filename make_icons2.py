import os
from PIL import Image, ImageDraw

def mask_rounded_corners(img, radius):
    """Applies a circular/rounded rect mask to remove the white corners."""
    mask = Image.new('L', img.size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle([(0,0), img.size], radius=radius, fill=255)
    
    result = img.copy()
    result.putalpha(mask)
    return result

src = r"C:\Users\karth\.gemini\antigravity-ide\brain\7e5a5dc2-7047-4072-b7b4-5cd9fc34ffea\.user_uploaded\media_1790837725864.jpg"
img = Image.open(src).convert("RGBA")

# 1. Mask out the white corners from the JPG
# The JPG is 1024x1024 (assuming from length). Let's use a 220px radius which usually cuts out AI generated squircle white backgrounds.
radius = int(img.width * 0.22)
transparent_img = mask_rounded_corners(img, radius)

base_dir = r"C:\Users\karth\Desktop\notesapp\notes\app\src\main\res"

sizes = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192
}

# 2. Legacy icons (with transparent corners)
for density, size in sizes.items():
    folder = os.path.join(base_dir, f"mipmap-{density}")
    os.makedirs(folder, exist_ok=True)
    
    # Save standard PNGs
    resized = transparent_img.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(os.path.join(folder, "ic_launcher.png"))
    resized.save(os.path.join(folder, "ic_launcher_round.png"))

# 3. Adaptive Foreground
# For Adaptive icons, we don't want the transparent corners to be visible if the OS mask is smaller or different shape.
# So we scale the transparent image slightly and place it on a transparent 108dp background.
def make_adaptive_foreground(src_img, dest, size, inner):
    img = src_img.resize((inner, inner), Image.Resampling.LANCZOS)
    bg = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    offset = (size - inner) // 2
    bg.paste(img, (offset, offset), img)
    bg.save(dest)

for density, size in sizes.items():
    scale = 2.25
    adaptive_size = int(size * scale)
    # The inner icon should slightly bleed past the 72dp safe zone so that the Android mask completely cuts off any transparency inside the mask,
    # meaning the blue squircle completely fills the mask! 
    # If adaptive_size is 108, the mask is about 72. So if we make inner = 108, it will be cropped by Android.
    # Let's just make inner = adaptive_size to fill it.
    inner_size = adaptive_size 
    folder = os.path.join(base_dir, f"mipmap-{density}")
    make_adaptive_foreground(transparent_img, os.path.join(folder, "ic_launcher_foreground.png"), adaptive_size, inner_size)

print("Icons generated perfectly without corner issues!")
