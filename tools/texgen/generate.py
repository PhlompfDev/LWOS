#!/usr/bin/env python3
"""Builder's-journal GUI texture generator for LWOS.

Deterministically paints the four sheets defined in
docs/superpowers/specs/2026-07-07-journal-ui-reskin-design.md and writes them to
src/main/resources/assets/lwos/textures/gui/journal/. Fixed seed, pure-python
noise -> identical bytes on every run (same Pillow version).

Usage:
    py -3 tools/texgen/generate.py            # (re)write the sheets
    py -3 tools/texgen/generate.py --verify   # regenerate in memory, diff vs disk
"""
import hashlib
import io
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

OUT_DIR = Path(__file__).resolve().parents[2] / "src/main/resources/assets/lwos/textures/gui/journal"

# Palette (RGB) — mirrors JournalTheme / the spec.
PARCH_LIGHT = (228, 213, 172)   # #E4D5AC
PARCH_DARK  = (203, 185, 138)   # #CBB98A
INK         = (59, 46, 30)      # #3B2E1E
LEATHER     = (107, 74, 43)     # #6B4A2B
LEATHER_DK  = (78, 52, 28)
BRASS       = (176, 141, 79)    # #B08D4F
BRASS_LIGHT = (214, 181, 118)
BRASS_DARK  = (128, 98, 50)
WAX         = (168, 53, 44)     # #A8352C
WAX_DARK    = (128, 36, 30)

PANEL_ALPHA = 232               # ~91% opaque: world stays visible behind the panel


def value_noise(rng, w, h, cell):
    """Seeded 2D value noise in [0,1] with smoothstep bilinear interpolation."""
    gw, gh = w // cell + 3, h // cell + 3
    grid = [[rng.random() for _ in range(gw)] for _ in range(gh)]

    def smooth(t):
        return t * t * (3 - 2 * t)

    def at(x, y):
        gx, gy = x / cell, y / cell
        x0, y0 = int(gx), int(gy)
        tx, ty = smooth(gx - x0), smooth(gy - y0)
        a = grid[y0][x0] + (grid[y0][x0 + 1] - grid[y0][x0]) * tx
        b = grid[y0 + 1][x0] + (grid[y0 + 1][x0 + 1] - grid[y0 + 1][x0]) * tx
        return a + (b - a) * ty

    return at


def lerp3(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def parchment(rng, w, h, alpha=255, contrast=1.0):
    """Fibrous parchment fill. contrast<1 flattens the tone (less visible tiling seams)."""
    n1 = value_noise(rng, w, h, 8)
    n2 = value_noise(rng, w, h, 3)
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = 0.6 * n1(x, y) + 0.3 * n2(x, y) + 0.1 * n2(x * 3 % w, y)  # + fiber streaks
            t = 0.5 + (t - 0.5) * contrast
            t = min(1.0, max(0.0, t))
            px[x, y] = lerp3(PARCH_LIGHT, PARCH_DARK, t) + (alpha,)
    return img


def deckle(img, rng, depth=3):
    """Torn/deckled rectangular rim: noisy alpha cut + darkened edge."""
    w, h = img.size
    n = value_noise(rng, w, h, 4)
    px = img.load()
    for y in range(h):
        for x in range(w):
            d = min(x, y, w - 1 - x, h - 1 - y)
            if d < depth + 2:
                edge = d + n(x, y) * 2.5 - 1.2
                if edge < 0:
                    px[x, y] = (0, 0, 0, 0)
                elif edge < 1.2:
                    r, g, b, a = px[x, y]
                    px[x, y] = (int(r * 0.72), int(g * 0.68), int(b * 0.60), a)
    return img


def ink_border(img, rng, inset, color=INK, alpha=230, step=3):
    """Hand-wobbled 1px rectangular rule, `inset` px in from each side."""
    d = ImageDraw.Draw(img)
    w, h = img.size
    def j(i):
        return rng.choice((-1, 0, 0, 0, 1)) if i % step == 0 else 0
    for x in range(inset, w - inset):
        d.point((x, inset + j(x)), fill=color + (alpha,))
        d.point((x, h - 1 - inset + j(x + 1)), fill=color + (alpha,))
    for y in range(inset, h - inset):
        d.point((inset + j(y), y), fill=color + (alpha,))
        d.point((w - 1 - inset + j(y + 1), y), fill=color + (alpha,))
    return img


def leather_corners(img, rng, size=11):
    """Stitched leather caps over the four corners (panel binding)."""
    d = ImageDraw.Draw(img)
    w, h = img.size
    for cx, cy, sx, sy in ((0, 0, 1, 1), (w - 1, 0, -1, 1), (0, h - 1, 1, -1), (w - 1, h - 1, -1, -1)):
        tri = [(cx, cy), (cx + sx * size, cy), (cx, cy + sy * size)]
        d.polygon(tri, fill=LEATHER + (255,))
        d.line([(cx + sx * size, cy), (cx, cy + sy * size)], fill=LEATHER_DK + (255,), width=1)
        # two stitch dots along the diagonal
        for t in (0.3, 0.65):
            px = round(cx + sx * size * t * 0.8)
            py = round(cy + sy * size * (1 - t) * 0.8)
            d.point((px, py), fill=PARCH_LIGHT + (255,))
    return img


def gen_panel(rng):
    img = parchment(rng, 48, 48, alpha=PANEL_ALPHA, contrast=0.55)
    deckle(img, rng, depth=3)
    ink_border(img, rng, 5, alpha=140)
    leather_corners(img, rng)
    return img


def gen_hud_plate(rng):
    img = parchment(rng, 32, 16, alpha=PANEL_ALPHA, contrast=0.6)
    deckle(img, rng, depth=2)
    ink_border(img, rng, 2, alpha=170)
    return img


def gen_tool_wheel(rng):
    size = 128
    r_disc, r_ring = 62.0, 53.0
    base = parchment(rng, size, size, alpha=240, contrast=0.6)
    n = value_noise(rng, size, size, 5)
    img = Image.new("RGBA", (size, size))
    src = base.load()
    px = img.load()
    c = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            dist = math.hypot(x - c, y - c)
            edge = r_disc - dist + (n(x, y) - 0.5) * 4.0   # torn circular rim
            if edge <= 0:
                continue
            r, g, b, a = src[x, y]
            if edge < 1.6:
                px[x, y] = (int(r * 0.72), int(g * 0.68), int(b * 0.60), a)
            else:
                px[x, y] = (r, g, b, a)
    d = ImageDraw.Draw(img)
    # wobbly compass ring
    for i in range(720):
        ang = math.pi * i / 360.0
        rr = r_ring + (rng.random() - 0.5) * 1.4
        d.point((round(c + rr * math.cos(ang)), round(c + rr * math.sin(ang))), fill=INK + (200,))
    # 8 tick marks + center diamond
    for i in range(8):
        ang = math.pi * i / 4.0
        x0 = c + (r_ring - 5) * math.cos(ang); y0 = c + (r_ring - 5) * math.sin(ang)
        x1 = c + (r_ring - 1) * math.cos(ang); y1 = c + (r_ring - 1) * math.sin(ang)
        d.line([(x0, y0), (x1, y1)], fill=INK + (220,), width=1)
    d.polygon([(c, c - 3), (c + 3, c), (c, c + 3), (c - 3, c)], outline=INK + (220,))
    return img


def gen_widgets(rng):
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # --- slider track (0,0) 32x8: ink-ruled groove in darkened parchment
    track = parchment(rng, 32, 8, alpha=255, contrast=0.5)
    tp = track.load()
    for y in range(8):
        for x in range(32):
            r, g, b, a = tp[x, y]
            tp[x, y] = (int(r * 0.82), int(g * 0.82), int(b * 0.80), a)
    mask = Image.new("L", (32, 8), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, 31, 7], radius=3, fill=255)
    track.putalpha(mask)
    ImageDraw.Draw(track).rounded_rectangle([0, 0, 31, 7], radius=3, outline=INK + (220,), width=1)
    img.paste(track, (0, 0), track)

    # --- slider knob (0,8) 8x12: brass button
    knob = Image.new("RGBA", (8, 12), (0, 0, 0, 0))
    kd = ImageDraw.Draw(knob)
    for y in range(12):
        t = y / 11.0
        col = lerp3(BRASS_LIGHT, BRASS_DARK, t)
        kd.line([(1, y), (6, y)], fill=col + (255,))
    kd.rounded_rectangle([0, 0, 7, 11], radius=2, outline=INK + (255,), width=1)
    kd.point((2, 2), fill=(238, 224, 185, 255))  # spec highlight
    img.paste(knob, (0, 8), knob)

    # --- chips (32,0)/(32,16) 24x16: paper tag, plain + wax-edged
    chip = parchment(rng, 24, 16, alpha=250, contrast=0.5)
    ink_border(chip, rng, 1, alpha=200, step=2)
    img.paste(chip, (32, 0), chip)
    sel = parchment(rng, 24, 16, alpha=255, contrast=0.5)
    sd = ImageDraw.Draw(sel)
    sd.rectangle([0, 0, 23, 15], outline=WAX + (255,), width=2)
    sd.rectangle([1, 1, 22, 14], outline=WAX_DARK + (160,), width=1)
    img.paste(sel, (32, 16), sel)

    # --- block slot frame (64,0) 20x20: ink frame, translucent parchment well
    slot = parchment(rng, 20, 20, alpha=110, contrast=0.5)
    sp = slot.load()
    for y in range(20):
        for x in range(20):
            if min(x, y, 19 - x, 19 - y) < 2:      # opaque parchment frame band
                r, g, b, _ = sp[x, y]
                sp[x, y] = (int(r * 0.9), int(g * 0.9), int(b * 0.88), 255)
    ink_border(slot, rng, 0, alpha=235, step=2)
    img.paste(slot, (64, 0), slot)

    # --- search field frame (0,32) 48x16: bright parchment, ink rule + baseline
    field = parchment(rng, 48, 16, alpha=255, contrast=0.4)
    ink_border(field, rng, 0, alpha=210, step=2)
    fd = ImageDraw.Draw(field)
    fd.line([(3, 13), (44, 13)], fill=INK + (120,), width=1)   # writing baseline
    img.paste(field, (0, 32), field)

    # --- stitch divider (0,48) 64x4: ink dashes with jitter
    for x in range(0, 64, 6):
        y = 1 + (1 if (x // 6) % 3 == 1 else 0)
        d.line([(x + 1, y + 48), (x + 4, y + 48)], fill=INK + (200,), width=1)

    # --- scrollbar strap (88,0) 6x24: leather with stitch dots
    strap = Image.new("RGBA", (6, 24), LEATHER + (255,))
    sd2 = ImageDraw.Draw(strap)
    sd2.line([(0, 0), (0, 23)], fill=LEATHER_DK + (255,))
    sd2.line([(5, 0), (5, 23)], fill=LEATHER_DK + (255,))
    for y in range(2, 24, 4):
        sd2.point((3, y), fill=PARCH_LIGHT + (230,))
    img.paste(strap, (88, 0), strap)

    # --- buttons (96,0)/(96,16) 16x16: parchment with brass rivets, + pressed
    for (bx, by, dim) in ((96, 0, 1.0), (96, 16, 0.84)):
        btn = parchment(rng, 16, 16, alpha=255, contrast=0.5)
        bp = btn.load()
        if dim != 1.0:
            for y in range(16):
                for x in range(16):
                    r, g, b, a = bp[x, y]
                    bp[x, y] = (int(r * dim), int(g * dim), int(b * dim), a)
        ink_border(btn, rng, 0, alpha=220, step=2)
        bd = ImageDraw.Draw(btn)
        for rx, ry in ((2, 2), (13, 2), (2, 13), (13, 13)):
            bd.point((rx, ry), fill=BRASS + (255,))
        img.paste(btn, (bx, by), btn)

    return img


SHEETS = (
    ("panel.png", 0x10051, gen_panel),
    ("widgets.png", 0x10052, gen_widgets),
    ("tool_wheel.png", 0x10053, gen_tool_wheel),
    ("hud_plate.png", 0x10054, gen_hud_plate),
)


def render_all():
    out = {}
    for name, seed, fn in SHEETS:
        img = fn(random.Random(seed))
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        out[name] = buf.getvalue()
    return out


def main():
    sheets = render_all()
    if "--verify" in sys.argv:
        ok = True
        for name, data in sheets.items():
            path = OUT_DIR / name
            if not path.exists():
                print(f"MISSING  {name}")
                ok = False
                continue
            disk = hashlib.sha256(path.read_bytes()).hexdigest()
            mem = hashlib.sha256(data).hexdigest()
            status = "OK      " if disk == mem else "MISMATCH"
            if disk != mem:
                ok = False
            print(f"{status} {name}")
        sys.exit(0 if ok else 1)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, data in sheets.items():
        (OUT_DIR / name).write_bytes(data)
        print(f"wrote {OUT_DIR / name} ({len(data)} bytes)")


if __name__ == "__main__":
    main()
