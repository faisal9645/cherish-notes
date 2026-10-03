import os
from PIL import Image, ImageDraw

def mask_rounded_corners(img, radius):
    """Applies a rounded rect mask to remove the white corners."""
    mask = Image.new('L', img.size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle([(0,0), img.size], radius=radius, fill=255)
    
    result = img.copy()
    result.putalpha(mask)
    return result

src = r"C:\Users\karth\.gemini\antigravity-ide\brain\29d2ab31-3542-4f8c-b5fd-208d34bb073a\.user_uploaded\media_1790964034409.jpg"
if not os.path.exists(src):
    print("Source image not found:", src)
    exit(1)

img = Image.open(src).convert("RGBA")
radius = int(img.width * 0.22)
transparent_img = mask_rounded_corners(img, radius)

base_dir = r"C:\Users\karth\Desktop\notesapp\app\src\main\res"

# Standard sizes for splash screen icon (288dp)
sizes = {
    "mdpi": (1.0, 288),
    "hdpi": (1.5, 432),
    "xhdpi": (2.0, 576),
    "xxhdpi": (3.0, 864),
    "xxxhdpi": (4.0, 1152)
}

# The Android 12 mask is a 192dp circle in the center.
# We will make the actual icon fit inside 180dp to be safe.
SAFE_SIZE_DP = 180

for density, (scale, canvas_size) in sizes.items():
    folder = os.path.join(base_dir, f"mipmap-{density}")
    os.makedirs(folder, exist_ok=True)
    
    inner_size = int(SAFE_SIZE_DP * scale)
    resized_icon = transparent_img.resize((inner_size, inner_size), Image.Resampling.LANCZOS)
    
    # Create transparent canvas
    bg = Image.new("RGBA", (canvas_size, canvas_size), (255, 255, 255, 0))
    offset = (canvas_size - inner_size) // 2
    
    bg.paste(resized_icon, (offset, offset), resized_icon)
    
    dest = os.path.join(folder, "splash_logo.png")
    bg.save(dest)
    print(f"Saved {dest}")

print("Splash icons generated successfully!")
