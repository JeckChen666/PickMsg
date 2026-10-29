#!/usr/bin/env python3
"""Build Android icon resources from the approved ivory icon (requires Pillow).

This is an offline asset tool, not an Android build dependency. Generated resources
are checked in so normal builds do not need Python or the image generation API.
"""
from pathlib import Path
import hashlib
import json
import math

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'assets/branding/source/approved-ivory.png'
RES = ROOT / 'app/src/main/res'
OUT = ROOT / 'assets/branding'
IVORY = '#F7F4EB'
CANVAS = 1080
VISIBLE = (180, 180, 900, 900)  # Central 72 dp of Android's 108 dp adaptive layers.


def silhouette(image):
    red, green, blue = image.convert('RGB').split()
    jade = ImageChops.multiply(
        ImageChops.subtract(green, red).point(lambda v: 255 if v > 10 else 0),
        ImageChops.subtract(green, blue).point(lambda v: 255 if v > 5 else 0),
    )
    # Close the source's fine white fold seam before filling the interior message card.
    jade = jade.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.MinFilter(5))
    filled = jade.copy()
    ImageDraw.floodfill(filled, (0, 0), 128)
    return filled.point(lambda v: 0 if v == 128 else 255)


def radius(mask):
    cx, cy = mask.width / 2, mask.height / 2
    return max(math.hypot(x-cx, y-cy)
               for y in range(mask.height) for x in range(mask.width)
               if mask.getpixel((x, y)) > 16)


def place(image, size, canvas=CANVAS):
    result = Image.new('RGBA', (canvas, canvas))
    scaled = image.resize(size, Image.Resampling.LANCZOS)
    result.alpha_composite(scaled, ((canvas-size[0])//2, (canvas-size[1])//2))
    return result


def shape_mask(size, shape):
    mask = Image.new('L', (size, size))
    draw = ImageDraw.Draw(mask)
    if shape == 'circle':
        draw.ellipse((0, 0, size-1, size-1), fill=255)
    else:
        draw.rounded_rectangle((0, 0, size-1, size-1), radius=size * (0.26 if shape == 'squircle' else 0.20), fill=255)
    return mask


def masked(image, shape):
    result = image.copy().convert('RGBA')
    result.putalpha(shape_mask(result.width, shape))
    return result


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    source = Image.open(SOURCE).convert('RGB')
    assert source.size == (1254, 1254), 'Approved artwork changed; review the extraction before regenerating.'
    mask = silhouette(source)
    bounds = mask.getbbox()
    assert bounds and 700 < bounds[2]-bounds[0] < 900 and 700 < bounds[3]-bounds[1] < 900, bounds
    symbol = source.convert('RGBA')
    symbol.putalpha(mask.filter(ImageFilter.GaussianBlur(0.5)))
    symbol = symbol.crop(bounds)
    symbol.save(OUT / 'pickmsg-symbol.png', optimize=True)

    scale = min(560 / symbol.width, 560 / symbol.height, 315 / radius(symbol.getchannel('A')))
    size = (round(symbol.width * scale), round(symbol.height * scale))
    foreground = place(symbol, size)
    assert radius(foreground.getchannel('A')) <= 330, 'Foreground exceeds the 66 dp circular safe zone.'
    (RES / 'drawable-nodpi').mkdir(parents=True, exist_ok=True)
    foreground.save(RES / 'drawable-nodpi/ic_launcher_foreground.png', optimize=True)

    # Simplify to alpha only: cut out the card and a broad curved rim. Keep its two bars.
    crop = source.crop(bounds)
    mono_alpha = symbol.getchannel('A').copy()
    pixels = list(crop.get_flattened_data()) if hasattr(crop, 'get_flattened_data') else list(crop.getdata())
    ivory = Image.new('L', crop.size)
    ivory.putdata([255 if r > 220 and g > 210 and b > 195 and abs(r-g) < 20 else 0 for r, g, b in pixels])
    mono_alpha = ImageChops.subtract(mono_alpha, ivory)
    w, h = crop.size
    p = [(0, .33*h), (.23*w, .58*h), (.73*w, .65*h), (w, .34*h)]
    curve = []
    for i in range(101):
        t = i / 100
        curve.append(tuple((1-t)**3*p[0][a] + 3*(1-t)**2*t*p[1][a] + 3*(1-t)*t*t*p[2][a] + t**3*p[3][a] for a in (0, 1)))
    ImageDraw.Draw(mono_alpha).line(curve, fill=0, width=round(w*.045))
    mono = Image.new('RGBA', symbol.size, 'white')
    mono.putalpha(mono_alpha)
    mono_layer = place(mono, size)
    mono_layer.save(RES / 'drawable-nodpi/ic_launcher_monochrome.png', optimize=True)
    notification_size = (round(mono.width*72/max(mono.size)), round(mono.height*72/max(mono.size)))
    place(mono, notification_size, 96).save(RES / 'drawable-nodpi/ic_pickmsg_notification.png', optimize=True)

    base = Image.new('RGBA', (CANVAS, CANVAS), IVORY)
    base.alpha_composite(foreground)
    visible = base.crop(VISIBLE)
    # Opaque square export for stores; Android adaptive icons get the background separately.
    visible.convert('RGB').resize((512, 512), Image.Resampling.LANCZOS).save(OUT / 'pickmsg-store-512.png', optimize=True)
    masked(visible, 'rounded').save(OUT / 'pickmsg-icon.png', optimize=True)
    for density, edge in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
        directory = RES / ('mipmap-' + density)
        directory.mkdir(parents=True, exist_ok=True)
        for shape, filename in [('rounded', 'ic_launcher.png'), ('circle', 'ic_launcher_round.png')]:
            masked(visible, shape).resize((edge, edge), Image.Resampling.LANCZOS).save(directory / filename, optimize=True)

    board = Image.new('RGB', (1200, 760), '#E4E7E2')
    draw = ImageDraw.Draw(board)
    font_path = Path('/System/Library/Fonts/Helvetica.ttc')
    font = ImageFont.truetype(str(font_path), 25) if font_path.exists() else ImageFont.load_default()
    for i, shape in enumerate(['rounded', 'circle', 'squircle']):
        icon = masked(visible, shape).resize((260, 260), Image.Resampling.LANCZOS)
        x = i*400+70
        draw.text((x, 30), 'SOFT SQUARE' if shape == 'squircle' else shape.upper(), font=font, fill='#20372D')
        board.paste(icon, (x, 80), icon)
        for edge, dx in [(96, 30), (48, 180)]:
            small = masked(visible, shape).resize((edge, edge), Image.Resampling.LANCZOS)
            board.paste(small, (x+dx, 380+(96-edge)//2), small)
    for i, (bg, tint) in enumerate([('#E1EAD8', '#314A34'), ('#243229', '#C4DDC5')]):
        mono_visible = mono_layer.crop(VISIBLE).getchannel('A')
        themed = Image.new('RGBA', visible.size, bg)
        mark = Image.new('RGBA', visible.size, tint)
        mark.putalpha(mono_visible)
        themed.alpha_composite(mark)
        themed = masked(themed, 'rounded').resize((150, 150), Image.Resampling.LANCZOS)
        board.paste(themed, (90+i*230, 555), themed)
    status = Image.open(RES / 'drawable-nodpi/ic_pickmsg_notification.png')
    draw.rounded_rectangle((730, 570, 1110, 695), radius=20, fill='#26372D')
    for i, edge in enumerate([24, 36, 48]):
        mark = status.resize((edge, edge), Image.Resampling.LANCZOS)
        board.paste(mark, (785+i*100, 620-edge//2), mark)
    draw.text((70, 510), 'THEMED ICON PREVIEW', font=font, fill='#20372D')
    draw.text((730, 510), 'NOTIFICATION / TILE', font=font, fill='#20372D')
    board.save(OUT / 'icon-preview.png', optimize=True)
    metadata = dict(source=str(SOURCE.relative_to(ROOT)), sourceSha256=hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
                    extractionBounds=bounds, adaptiveLayerSize=CANVAS, contentSize=size,
                    contentRadiusDp=round(radius(foreground.getchannel('A'))/10, 2),
                    safeRadiusDp=33, background=IVORY)
    (OUT / 'asset-metadata.json').write_text(json.dumps(metadata, indent=2)+'\n')
    print(json.dumps(metadata, indent=2))


if __name__ == '__main__':
    main()
