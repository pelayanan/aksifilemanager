#!/usr/bin/env python3
"""Banner Play Store 1024x500 Aksi File Manager — versi improved (pakai screenshot app asli)."""
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import os

W, H = 1024, 500
SHOTS = "/root/aksifile-shots"
out = "store/banner_1024x500.png"
F = "/usr/share/fonts/truetype/liberation/"
f_big  = ImageFont.truetype(F + "LiberationSans-Bold.ttf", 66)
f_tag  = ImageFont.truetype(F + "LiberationSans-Regular.ttf", 22)
f_tag2 = ImageFont.truetype(F + "LiberationSans-Bold.ttf", 22)
f_chip = ImageFont.truetype(F + "LiberationSans-Regular.ttf", 15)
f_smk  = ImageFont.truetype(F + "LiberationSans-Regular.ttf", 11)

def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

img = Image.new("RGBA", (W, H))
px = img.load()
c0, c1 = (5, 38, 110), (20, 96, 220)
for y in range(H):
    for x in range(W):
        px[x, y] = lerp(c0, c1, min(1.0, (x / W) * 0.55 + (y / H) * 0.45))

# diagonal shine + ring dekorasi
ov = Image.new("RGBA", (W, H))
od = ImageDraw.Draw(ov)
od.polygon([(430, 0), (660, 0), (960, 500), (730, 500)], fill=(255, 255, 255, 16))
od.ellipse([-130, -170, 230, 190], outline=(255, 255, 255, 40), width=3)
od.ellipse([-100, -140, 200, 160], outline=(255, 255, 255, 22), width=2)
od.ellipse([690, 330, 1140, 780], outline=(255, 255, 255, 34), width=3)
img = Image.alpha_composite(img, ov)
d = ImageDraw.Draw(img, "RGBA")

def rounded_mask(w, h, r):
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w - 1, h - 1], radius=r, fill=255)
    return m

def phone(scr_path, cx, cy, pw, rot):
    scr = Image.open(scr_path).convert("RGB")
    ratio = scr.height / scr.width
    ph = int(pw * ratio)
    scr = scr.resize((pw, ph), Image.LANCZOS)
    pad = 10
    W2, H2 = pw + pad * 2, ph + pad * 2
    body = Image.new("RGBA", (W2, H2))
    bd = ImageDraw.Draw(body)
    bd.rounded_rectangle([0, 0, W2 - 1, H2 - 1], radius=34, fill=(255, 255, 255, 255))
    body.paste(scr, (pad, pad))
    # notch
    nd = ImageDraw.Draw(body)
    nd.rounded_rectangle([W2 // 2 - 28, pad + 6, W2 // 2 + 28, pad + 12], radius=3, fill=(255, 255, 255, 170))
    mask = rounded_mask(W2, H2, 34)
    # shadow
    sh = Image.new("RGBA", (W2 + 60, H2 + 60))
    ImageDraw.Draw(sh).rounded_rectangle([30, 30, 30 + W2, 30 + H2], radius=36, fill=(2, 22, 80, 130))
    sh = sh.filter(ImageFilter.GaussianBlur(11))
    rot_sh = sh.rotate(rot, expand=True, resample=Image.BICUBIC)
    img.paste(rot_sh, (int(cx - rot_sh.width / 2), int(cy - rot_sh.height / 2)), rot_sh)
    rot_b = body.rotate(rot, expand=True, resample=Image.BICUBIC)
    mask_r = mask.rotate(rot, expand=True, resample=Image.BICUBIC)
    img.paste(rot_b, (int(cx - rot_b.width / 2), int(cy - rot_b.height / 2)), mask_r)

# app ikon (motif folder+A)
def draw_icon(cx, cy, s):
    big = Image.new("RGBA", (s * 2, s * 2))
    u = s * 2 / 512
    dd = ImageDraw.Draw(big)
    dd.rounded_rectangle([16 * u, 16 * u, 496 * u, 496 * u], radius=112 * u, fill=(9, 54, 145, 255))
    gd = Image.new("RGBA", big.size)
    gmask = Image.new("L", big.size, 0)
    gm = ImageDraw.Draw(gmask)
    gm.rounded_rectangle([16 * u, 16 * u, 496 * u, 496 * u], radius=112 * u, fill=255)
    for yy in range(0, big.height, 4):
        a = int(66 * (1 - yy / big.height))
        ImageDraw.Draw(gd).rectangle([0, yy, big.width, yy + 4], fill=(40, 118, 238, a))
    gd.putalpha(Image.composite(gd.split()[3], Image.new("L", big.size, 0), gmask))
    big = Image.alpha_composite(big, gd)
    dd = ImageDraw.Draw(big)
    dd.rounded_rectangle([86 * u, 127 * u, 430 * u, 232 * u], radius=20 * u, fill=(255, 196, 43, 255))
    dd.rectangle([86 * u, 202 * u, 430 * u, 232 * u], fill=(240, 140, 12, 128))
    dd.rounded_rectangle([65 * u, 181 * u, 447 * u, 420 * u], radius=36 * u, fill=(19, 108, 235, 255))
    dd.rounded_rectangle([65 * u, 181 * u, 447 * u, 276 * u], radius=36 * u, fill=(74, 168, 255, 100))
    dd.polygon([(256 * u, 236 * u), (332 * u, 391 * u), (283 * u, 391 * u), (269 * u, 360 * u), (241 * u, 360 * u), (227 * u, 391 * u), (178 * u, 391 * u)], fill=(255, 255, 255))
    dd.polygon([(255 * u, 294 * u), (241 * u, 328 * u), (269 * u, 328 * u)], fill=(17, 97, 219))
    big = big.resize((s, s), Image.LANCZOS)
    shimg = Image.new("RGBA", (s * 2, s * 2))
    ImageDraw.Draw(shimg).rounded_rectangle([s // 2, s // 2, s * 3 // 2, s * 3 // 2], radius=int(s * .22), fill=(2, 24, 84, 120))
    shimg = shimg.filter(ImageFilter.GaussianBlur(14))
    img.paste(shimg, (int(cx - s), int(cy - s)), shimg)
    img.paste(big, (int(cx - s / 2), int(cy - s / 2)), big)

draw_icon(148, 244, 216)

tx = 288
d.text((tx, 96), "Aksi File", font=f_big, fill=(255, 255, 255))
d.text((tx, 168), "Manager", font=f_big, fill=(255, 214, 74))
d.text((tx + 2, 250), "All in One File Explorer", font=f_tag2, fill=(255, 255, 255))
d.text((tx + 2, 284), "Offline  •  Cepat  •  Ringan  •  Gratis", font=f_tag, fill=(185, 212, 255))

chips = ["ZIP · 7Z · RAR", "PDF Tools", "Image & Video Editor", "FTP / SFTP", "DOCX · XLSX · PPTX", "Storage Analyzer"]
cx, cy = tx, 330
for c in chips:
    tw = d.textlength(c, font=f_chip); bw = tw + 30
    if cx + bw > 704: cx = tx; cy += 42
    d.rounded_rectangle([cx, cy, cx + bw, cy + 34], radius=17, fill=(255, 255, 255, 26), outline=(255, 255, 255, 74))
    d.text((cx + 15, cy + 8), c, font=f_chip, fill=(238, 244, 255))
    cx += bw + 11
d.text((tx + 2, cy + 48), "id.aksisoft.aksifile  •  Android 16 ready", font=f_smk, fill=(150, 185, 235))

# screenshots asli dalam frame ponsel
phone(f"{SHOTS}/04_more.png", 760, 250, 116, -7)
phone(f"{SHOTS}/01_home.png", 905, 240, 128, 5)

d.rounded_rectangle([688, 452, 1004, 480], radius=14, fill=(255, 255, 255, 30), outline=(255, 255, 255, 84))
d.text((704, 457), "▶  Target Android 16 (API 36)  •  R8 minified", font=f_chip, fill=(222, 235, 255))

os.makedirs("store", exist_ok=True)
img.convert("RGB").save(out, quality=96)
print("saved", os.path.abspath(out))
