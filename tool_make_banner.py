#!/usr/bin/env python3
"""Generate Play Store banner 1024x500 for Aksi File Manager (PIL only)."""
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import os

W, H = 1024, 500
B = "assets" if os.path.isdir("assets") else "."

def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

# ---------- background: diagonal gradient dark->bright blue ----------
img = Image.new("RGB", (W, H))
px = img.load()
c0, c1 = (6, 44, 130), (18, 99, 223)   # #062C82 -> #1263DF
for y in range(H):
    for x in range(0, W, 2):
        t = (x / W * 0.6) + (y / H * 0.4)
        col = lerp(c0, c1, min(t, 1.0))
        px[x, y] = col
        if x + 1 < W: px[x + 1, y] = col

draw = ImageDraw.Draw(img, "RGBA")

# subtle diagonal shine
overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
od = ImageDraw.Draw(overlay)
od.polygon([(520, 0), (760, 0), (1010, 500), (770, 500)], fill=(255, 255, 255, 14))
od.polygon([(830, 0), (940, 0), (1024, 190), (1024, 300)], fill=(255, 255, 255, 10))
img = Image.alpha_composite(img.convert("RGBA"), overlay)

# decorative circles
deco = Image.new("RGBA", (W, H), (0, 0, 0, 0))
dd = ImageDraw.Draw(deco)
dd.ellipse([-120, -160, 220, 180], outline=(255, 255, 255, 34), width=3)
dd.ellipse([-90, -130, 190, 150], outline=(255, 255, 255, 22), width=2)
dd.ellipse([760, 300, 1150, 690], outline=(255, 255, 255, 30), width=3)
img = Image.alpha_composite(img.convert("RGBA"), deco)

draw = ImageDraw.Draw(img, "RGBA")

F = "/usr/share/fonts/truetype/liberation/"
f_big   = ImageFont.truetype(F + "LiberationSans-Bold.ttf", 64)
f_med   = ImageFont.truetype(F + "LiberationSans-Bold.ttf", 30)
f_tag   = ImageFont.truetype(F + "LiberationSans-Regular.ttf", 21)
f_chip  = ImageFont.truetype(F + "LiberationSans-Regular.ttf", 15)
f_b     = ImageFont.truetype(F + "LiberationSans-Bold.ttf", 14)
f_sm    = ImageFont.truetype(F + "LiberationSans-Regular.ttf", 12)
f_smk   = ImageFont.truetype(F + "LiberationSans-Regular.ttf", 10)

# ---------------- app icon (folder + A motif) ----------------
def rounded(draw, box, r, fill):
    draw.rounded_rectangle(box, radius=r, fill=fill, outline=None)

def draw_icon(cx, cy, s):
    """Draw the icon centered at (cx,cy); s = side length."""
    x0, y0 = cx - s / 2, cy - s / 2
    big = Image.new("RGBA", (s * 4, s * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    u = s * 4 / 512  # unit scale from the 512 viewBox
    o = 16 * u / 4   # margin (icon drawn at 4x, offset map below)
    # background rounded square
    d.rounded_rectangle([16 * u, 16 * u, 496 * u, 496 * u], radius=112 * u, fill=(10, 60, 158, 255))
    # gradient trick: lighter top-left tint
    grad = Image.new("RGBA", (s * 4, s * 4), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grad)
    gd.rounded_rectangle([16 * u, 16 * u, 496 * u, 496 * u], radius=112 * u, fill=(35, 110, 235, 60))
    mask = Image.new("L", (s * 4, s * 4), 0)
    for yy in range(0, s * 4, 8):
        for xx in range(0, s * 4, 8):
            m = max(0, 1.0 - (xx + yy) / (s * 4 * 1.1))
            ImageDraw.Draw(mask).rectangle([xx, yy, xx + 8, yy + 8], fill=int(255 * m))
    grad.putalpha(Image.composite(grad.split()[3], Image.new("L", (s * 4, s * 4), 0), mask))
    big = Image.alpha_composite(big, grad)
    d = ImageDraw.Draw(big)
    # yellow tab
    d.rounded_rectangle([86 * u, 127 * u, 430 * u, 230 * u], radius=20 * u, fill=(255, 196, 43, 255))
    d.rectangle([86 * u, 202 * u, 430 * u, 230 * u], fill=(245, 139, 12, 128))
    # folder body
    d.rounded_rectangle([65 * u, 181 * u, 447 * u, 418 * u], radius=36 * u, fill=(19, 108, 235, 255))
    d.rounded_rectangle([65 * u, 181 * u, 447 * u, 275 * u], radius=36 * u, fill=(70, 165, 255, 110))
    # white A triangle
    d.polygon([(256 * u, 236 * u), (332 * u, 391 * u), (283 * u, 391 * u), (269 * u, 360 * u), (241 * u, 360 * u), (227 * u, 391 * u), (178 * u, 391 * u)], fill=(255, 255, 255, 255))
    d.polygon([(255 * u, 294 * u), (241 * u, 328 * u), (269 * u, 328 * u)], fill=(18, 99, 223, 255))
    big = big.resize((s, s), Image.LANCZOS)
    # shadow
    sh = Image.new("RGBA", (s * 2, s * 2), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([s // 2, s // 2, s // 2 + s, s // 2 + s], radius=int(s * 0.22), fill=(3, 30, 95, 120))
    sh = sh.filter(ImageFilter.GaussianBlur(14))
    img.paste(sh, (int(cx - s), int(cy - s)), sh)
    img.paste(big, (int(cx - s / 2), int(cy - s / 2)), big)

draw_icon(150, 250, 210)

# ---------------- texts ----------------
tx = 292
w = draw.textlength("Aksi File", font=f_big)
draw.text((tx, 108), "Aksi File", font=f_big, fill=(255, 255, 255, 255))
draw.text((tx, 180), "Manager", font=f_big, fill=(255, 217, 80, 255))
draw.text((tx, 262), "All in One File Explorer", font=f_tag, fill=(200, 220, 255, 255))
draw.text((tx, 292), "Offline  •  Cepat  •  100% Gratis", font=f_tag, fill=(160, 195, 255, 255))

print("texts done")
img.save("banner_tmp.png")

# ---------------- feature chips ----------------
chips = [
    "ZIP / RAR / 7Z", "PDF Tools", "Image & Video Editor",
    "FTP / SFTP", "OCR Coding & DOCX", "Storage Analyzer",
]
cx, cy = tx, 342
for c in chips:
    tw = draw.textlength(c, font=f_chip)
    bw = tw + 26
    pad = 12
    if cx + bw > 690:
        cx = tx; cy += 44
    draw.rounded_rectangle([cx, cy, cx + bw, cy + 34], radius=17, fill=(255, 255, 255, 28), outline=(255, 255, 255, 70), width=1)
    draw.text((cx + 13, cy + 8), c, font=f_chip, fill=(235, 242, 255, 255))
    cx += bw + pad

cy_line = cy + 34 + 14
if cy_line < H:
    draw.line([(tx, cy_line + 20), (tx + draw.textlength("id.aksisoft.aksifile", font=f_smk) + 4, cy_line + 20)], fill=(255, 255, 255, 0))
    draw.text((tx, cy_line + 4), "id.aksisoft.aksifile  •  v1.1", font=f_smk, fill=(150, 180, 230, 255))

# ---------------- right side: 2 phone mockups ----------------
def phone(px0, py0, pw, ph):
    r = 26
    # body
    draw.rounded_rectangle([px0, py0, px0 + pw, py0 + ph], radius=r, fill=(255, 255, 255, 255))
    # screen area
    sx = px0 + 8; sy = py0 + 8; sw = pw - 16; sh = ph - 16
    draw.rounded_rectangle([sx, sy, sx + sw, sy + sh], radius=r - 8, fill=(242, 246, 252, 255))
    # header
    draw.rounded_rectangle([sx, sy, sx + sw, sy + 44], radius=r - 8, fill=(48, 96, 224, 255))
    draw.rounded_rectangle([sx, sy + 22, sx + sw, sy + 44], fill=(48, 96, 224, 255))
    draw.text((sx + 12, sy + 12), "Aksi File", font=f_b, fill=(255, 255, 255, 255))
    return sx, sy, sw, sh

# phone 1: explorer (files list)
px1, py1, pw1, ph1 = 740, 46, 120, 244
sx, sy, sw, sh = phone(px1, py1, pw1, ph1)
rows = [(255, 196, 43), (24, 118, 233), (24, 118, 233), (232, 84, 61), (120, 190, 110), (120, 190, 110)]
ry = sy + 62
for i, col in enumerate(rows):
    draw.rounded_rectangle([sx + 8, ry, sx + 26, ry + 18], radius=5, fill=col)
    wd = 90 - i * 9 if i % 2 == 0 else 70 - i * 6
    draw.rounded_rectangle([sx + 34, ry + 2, sx + 34 + max(wd, 30), ry + 9], radius=4, fill=(115, 130, 160, 255))
    draw.rounded_rectangle([sx + 34, ry + 13, sx + 34 + max(wd // 2, 20), ry + 17], radius=2, fill=(190, 200, 220, 255))
    ry += 26

# phone 2: tools grid
px2, py2, pw2, ph2 = 880, 96, 120, 244
sx2, sy2, sw2, sh2 = phone(px2, py2, pw2, ph2)
cols = [(20, 86, 214), (255, 152, 20), (66, 165, 245), (124, 85, 247), (22, 178, 138), (235, 72, 86)]
gx, gy = sx2 + 8, sy2 + 56
n = 0
for r_i in range(3):
    for c_i in range(2):
        if n >= 6: break
        bx = gx + c_i * ((sw2 - 16) // 2 + 0) + c_i * 0
        bw2 = (sw2 - 20) // 2
        draw.rounded_rectangle([gx + c_i * (bw2 + 4), gy + r_i * 58, gx + c_i * (bw2 + 4) + bw2, gy + r_i * 58 + 50], radius=9, fill=(255, 255, 255, 255), outline=(224, 230, 240, 255))
        ccx = gx + c_i * (bw2 + 4) + bw2 // 2
        draw.rounded_rectangle([ccx - 10, gy + r_i * 58 + 6, ccx + 10, gy + r_i * 58 + 26], radius=6, fill=cols[n])
        draw.rounded_rectangle([ccx - 14, gy + r_i * 58 + 32, ccx + 14, gy + r_i * 58 + 40], radius=3, fill=(180, 190, 210, 255))
        n += 1

# badge "Get it on Google Play" bar bottom right
draw.rounded_rectangle([740, 366, 1000, 392], radius=13, fill=(255, 255, 255, 30), outline=(255, 255, 255, 80), width=1)
draw.text((756, 371), "▶  Target Android 16 (API 36)  •  R8 minify", font=f_sm, fill=(220, 233, 255, 255))

img.convert("RGB").save("store/banner_1024x500.png", quality=95)
os.system("rm -f banner_tmp.png")
print("banner saved:", os.path.abspath("store/banner_1024x500.png"))
