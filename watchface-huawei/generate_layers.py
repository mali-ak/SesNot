#!/usr/bin/env python3
"""
SesNot kadran katman üreticisi (Huawei Theme Studio için).

Tüm katmanlar Python/Pillow ile çizilir, dış görsel dosyası gerekmez.
Çıktı: out/<WxH>/ altında PNG katmanlar + preview_active.png / preview_aod.png

Kullanım:
    python generate_layers.py              # 466 ve 390 için üret
    python generate_layers.py --size 466   # tek çözünürlük

Katmanların Theme Studio'ya nasıl aktarılacağı README.md'de anlatılır.
"""
from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

# ---------------------------------------------------------------- renk paleti
BG = (7, 9, 14, 255)             # koyu zemin
BG_RING = (18, 22, 32, 255)      # dış halka
ACCENT = (255, 122, 60, 255)     # SesNot turuncu (mikrofon, ses dalgası)
TEXT = (238, 240, 245, 255)
TEXT_DIM = (150, 156, 170, 255)
STEPS_C = (84, 200, 120, 255)
HR_C = (240, 80, 90, 255)
BATT_C = (90, 170, 255, 255)
BLACK = (0, 0, 0, 255)
AOD_FG = (200, 200, 205, 255)
AOD_ACCENT = (255, 122, 60, 255)

FONT_CANDIDATES_BOLD = ["segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"]
FONT_CANDIDATES_REG = ["segoeui.ttf", "arial.ttf", "DejaVuSans.ttf"]


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    for name in (FONT_CANDIDATES_BOLD if bold else FONT_CANDIDATES_REG):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def new_layer(size: int) -> Image.Image:
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def s(size: int, v: float) -> int:
    """466 referansına göre ölçekle."""
    return int(round(v * size / 466.0))


# ---------------------------------------------------------------- katmanlar
def draw_background(size: int, aod: bool = False) -> Image.Image:
    img = Image.new("RGBA", (size, size), BLACK if aod else BG)
    if aod:
        return img
    d = ImageDraw.Draw(img)
    # yumuşak radyal parlama (üst-sol)
    glow = new_layer(size)
    gd = ImageDraw.Draw(glow)
    r = s(size, 260)
    gd.ellipse([s(size, 60) - r, s(size, 90) - r, s(size, 60) + r, s(size, 90) + r],
               fill=(255, 122, 60, 28))
    glow = glow.filter(ImageFilter.GaussianBlur(s(size, 70)))
    img.alpha_composite(glow)
    # ince dış halka
    pad = s(size, 6)
    d.ellipse([pad, pad, size - pad, size - pad], outline=BG_RING, width=s(size, 3))
    # dairesel kesim (yuvarlak ekran önizlemesi için)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def draw_waveform(size: int, color=ACCENT, aod: bool = False) -> Image.Image:
    """Mikrofonun iki yanına simetrik ses dalgası çubukları."""
    img = new_layer(size)
    d = ImageDraw.Draw(img)
    cx, cy = size // 2, s(size, 118)
    bar_w = s(size, 6)
    gap = s(size, 10)
    heights = [14, 26, 42, 30, 18, 36, 22]
    for side in (-1, 1):
        for i, h in enumerate(heights):
            x = cx + side * (s(size, 58) + i * (bar_w + gap))
            hh = s(size, h) // (2 if aod else 1)
            a = 255 - i * 24
            col = (color[0], color[1], color[2], a)
            d.rounded_rectangle([x - bar_w // 2, cy - hh, x + bar_w // 2, cy + hh],
                                radius=bar_w // 2, fill=col)
    return img


def draw_mic(size: int, color=ACCENT, aod: bool = False) -> Image.Image:
    """SesNot mikrofon motifi."""
    img = new_layer(size)
    d = ImageDraw.Draw(img)
    cx, cy = size // 2, s(size, 118)
    w = s(size, 44)
    h = s(size, 78)
    top = cy - s(size, 48)
    body = [cx - w // 2, top, cx + w // 2, top + h]
    if aod:
        d.rounded_rectangle(body, radius=w // 2, outline=color, width=s(size, 4))
    else:
        d.rounded_rectangle(body, radius=w // 2, fill=color)
        for k in range(3):  # ızgara çizgileri
            y = top + s(size, 26) + k * s(size, 12)
            d.line([cx - s(size, 12), y, cx + s(size, 12), y], fill=BG, width=s(size, 3))
    # tutucu yay
    arc_r = s(size, 40)
    arc_box = [cx - arc_r, top + h - arc_r - s(size, 10), cx + arc_r, top + h + arc_r - s(size, 10)]
    d.arc(arc_box, start=0, end=180, fill=color, width=s(size, 5))
    # sap ve taban
    stem_top = top + h + arc_r - s(size, 10)
    d.line([cx, stem_top, cx, stem_top + s(size, 18)], fill=color, width=s(size, 5))
    d.line([cx - s(size, 18), stem_top + s(size, 20), cx + s(size, 18), stem_top + s(size, 20)],
           fill=color, width=s(size, 5))
    return img


def draw_text_center(size, y, txt, fsize, color, bold=True, img=None):
    img = img or new_layer(size)
    d = ImageDraw.Draw(img)
    f = load_font(s(size, fsize), bold=bold)
    d.text((size // 2, y), txt, font=f, fill=color, anchor="mt")
    return img


def draw_brand(size: int, aod: bool = False) -> Image.Image:
    col = AOD_FG if aod else TEXT_DIM
    return draw_text_center(size, s(size, 204), "SesNot", 22, col)


def draw_time_sample(size: int, aod: bool = False) -> Image.Image:
    """Yer tutucu saat. Theme Studio'da 'Combined Res' (digits/) veya 'Text'
    kontrolü ile değiştirilir; sadece önizleme ve konum referansı."""
    col = AOD_FG if aod else TEXT
    return draw_text_center(size, s(size, 232), "10:42", 96, col)


def draw_date_sample(size: int) -> Image.Image:
    return draw_text_center(size, s(size, 318), "CUM 16 EKI", 24, TEXT_DIM, bold=False)


def draw_digits(size: int, aod: bool = False) -> dict[str, Image.Image]:
    """'Combined Res' kontrolü için 0-9 rakam dilimleri ve ':' ayırıcı."""
    out = {}
    f = load_font(s(size, 96), bold=True)
    col = AOD_FG if aod else TEXT
    cell_w, cell_h = s(size, 72), s(size, 112)
    for ch in "0123456789:":
        im = Image.new("RGBA", (cell_w if ch != ":" else s(size, 34), cell_h), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        d.text((im.width / 2, 0), ch, font=f, fill=col, anchor="mt")
        out["colon" if ch == ":" else ch] = im
    return out


def _icon_canvas(size: int):
    img = Image.new("RGBA", (s(size, 40), s(size, 40)), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img), img.width


def draw_icon_steps(size: int, color=STEPS_C) -> Image.Image:
    img, d, n = _icon_canvas(size)
    d.ellipse([n*0.18, n*0.10, n*0.50, n*0.62], fill=color)
    d.ellipse([n*0.55, n*0.30, n*0.85, n*0.80], fill=color)
    d.ellipse([n*0.22, n*0.68, n*0.46, n*0.90], fill=color)
    return img


def draw_icon_heart(size: int, color=HR_C) -> Image.Image:
    img, d, n = _icon_canvas(size)
    r = n * 0.24
    d.ellipse([n*0.12, n*0.18, n*0.12 + 2*r, n*0.18 + 2*r], fill=color)
    d.ellipse([n*0.40, n*0.18, n*0.40 + 2*r, n*0.18 + 2*r], fill=color)
    d.polygon([(n*0.13, n*0.48), (n*0.87, n*0.48), (n*0.50, n*0.90)], fill=color)
    return img


def draw_icon_battery(size: int, color=BATT_C) -> Image.Image:
    img, d, n = _icon_canvas(size)
    d.rounded_rectangle([n*0.10, n*0.28, n*0.80, n*0.72], radius=n*0.08,
                        outline=color, width=max(2, int(n*0.08)))
    d.rectangle([n*0.82, n*0.40, n*0.92, n*0.60], fill=color)
    d.rectangle([n*0.20, n*0.38, n*0.62, n*0.62], fill=color)
    return img


def draw_arc_progress(size: int, color, full: bool) -> Image.Image:
    """'Arc Res' kontrolü için: dolu yay (fill) ve soluk arka plan yayı (track)."""
    img = new_layer(size)
    d = ImageDraw.Draw(img)
    pad = s(size, 22)
    box = [pad, pad, size - pad, size - pad]
    w = s(size, 8)
    col = color if full else (color[0], color[1], color[2], 50)
    d.arc(box, start=150, end=390, fill=col, width=w)
    return img


def draw_data_labels_sample(size: int) -> Image.Image:
    """Önizleme için örnek veri metinleri (Text kontrolü ile değiştirilir)."""
    img = new_layer(size)
    d = ImageDraw.Draw(img)
    f = load_font(s(size, 26), bold=True)
    y = s(size, 396)
    for cx, txt in [(s(size, 128), "6 482"), (s(size, 233), "72"), (s(size, 338), "84%")]:
        d.text((cx, y), txt, font=f, fill=TEXT, anchor="mt")
    return img


def place_icons(size: int, canvas: Image.Image) -> None:
    y = s(size, 372)
    for cx, icon in [(s(size, 128), draw_icon_steps(size)),
                     (s(size, 233), draw_icon_heart(size)),
                     (s(size, 338), draw_icon_battery(size))]:
        canvas.alpha_composite(icon, (cx - icon.width // 2, y - icon.height // 2))


# ---------------------------------------------------------------- üretim
def build(size: int, out_root: Path) -> float:
    out = out_root / f"{size}x{size}"
    for sub in ("active", "aod", "digits", "digits_aod", "icons"):
        (out / sub).mkdir(parents=True, exist_ok=True)

    # --- aktif katmanlar
    bg = draw_background(size)
    bg.save(out / "active" / "01_background.png")
    wave = draw_waveform(size)
    wave.save(out / "active" / "02_waveform.png")
    mic = draw_mic(size)
    mic.save(out / "active" / "03_mic.png")
    brand = draw_brand(size)
    brand.save(out / "active" / "04_brand.png")
    track = draw_arc_progress(size, STEPS_C, full=False)
    track.save(out / "active" / "05_steps_arc_track.png")
    draw_arc_progress(size, STEPS_C, full=True).save(out / "active" / "06_steps_arc_fill.png")

    for name, im in draw_digits(size).items():
        im.save(out / "digits" / f"{name}.png")
    for name, im in draw_digits(size, aod=True).items():
        im.save(out / "digits_aod" / f"{name}.png")

    draw_icon_steps(size).save(out / "icons" / "steps.png")
    draw_icon_heart(size).save(out / "icons" / "heart.png")
    draw_icon_battery(size).save(out / "icons" / "battery.png")
    draw_icon_steps(size, AOD_FG).save(out / "icons" / "steps_aod.png")
    draw_icon_heart(size, AOD_FG).save(out / "icons" / "heart_aod.png")
    draw_icon_battery(size, AOD_FG).save(out / "icons" / "battery_aod.png")

    # --- AOD katmanlar (siyah zemin, ince çizgi, az piksel)
    abg = draw_background(size, aod=True)
    abg.save(out / "aod" / "01_background.png")
    amic = draw_mic(size, AOD_ACCENT, aod=True)
    amic.save(out / "aod" / "02_mic.png")
    awave = draw_waveform(size, AOD_ACCENT, aod=True)
    awave.save(out / "aod" / "03_waveform.png")

    # --- önizlemeler (Theme Studio'ya YÜKLENMEZ, sadece referans)
    prev = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for layer in (bg, wave, mic, brand, track,
                  draw_time_sample(size), draw_date_sample(size), draw_data_labels_sample(size)):
        prev.alpha_composite(layer)
    place_icons(size, prev)
    prev.save(out / "preview_active.png")

    aprev = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for layer in (abg, amic, awave, draw_time_sample(size, aod=True)):
        aprev.alpha_composite(layer)
    aprev.save(out / "preview_aod.png")

    # AOD piksel oranı (Huawei sınırı GT serisi 2.y.z: <%10 ideal, <%20 üst sınır)
    rgb = aprev.convert("RGB")
    hist = rgb.point(lambda v: 255 if v else 0).convert("L").histogram()
    nonblack = sum(hist[1:])  # en az bir kanali sifir olmayan pikseller
    ratio = nonblack / (size * size) * 100
    (out / "aod_pixel_ratio.txt").write_text(
        f"AOD non-black pixel ratio: {ratio:.2f}% (GT series limit: <10% ideal, <20% max)\n",
        encoding="utf-8")
    print(f"[{size}] katmanlar yazildi -> {out} | AOD siyah-olmayan piksel: {ratio:.2f}%")
    return ratio


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--size", type=int, choices=[466, 390], help="tek çözünürlük üret")
    ap.add_argument("--out", default=str(Path(__file__).parent / "out"))
    a = ap.parse_args()
    for sz in ([a.size] if a.size else [466, 390]):
        build(sz, Path(a.out))


if __name__ == "__main__":
    main()
