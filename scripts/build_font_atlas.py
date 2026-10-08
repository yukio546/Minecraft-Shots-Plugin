#!/usr/bin/env python3
"""Rasterize the pinned Minecraft Five outlines on their native 140-unit grid.

Build dependency: fonttools==4.61.1, pillow==12.1.0. No runtime client dependency.
The font's ASCII outlines contain only straight segments. Sampling pixel centers
preserves holes and produces opaque pixels without antialiased edge fringes.
"""
from pathlib import Path
import hashlib
import json
import math
from fontTools.ttLib import TTFont
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'resource-pack'
FONT = PACK / 'assets/drunkshyt/font/minecraft-five-regular.ttf'
# Pin the original source bytes, independently of generated asset checksums.
SHA256 = 'c57aad97df42971ebd5f9d0eedc571c9f169b077c5ead0ffa0059a89384b326a'
PIXEL = 140
CELL_W, CELL_H, COLUMNS, ASCENT = 16, 8, 16, 7


def inside(x, y, contours):
    crossing = False
    for polygon in contours:
        for (x1, y1), (x2, y2) in zip(polygon, polygon[1:] + polygon[:1]):
            if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
                crossing = not crossing
    return crossing


def build():
    assert hashlib.sha256(FONT.read_bytes()).hexdigest() == SHA256
    font = TTFont(FONT)
    cmap = font.getBestCmap()
    codes = list(range(33, 127))
    rows = math.ceil(len(codes) / COLUMNS)
    atlas = Image.new('RGBA', (CELL_W * COLUMNS, CELL_H * rows))
    widths = {}
    for index, code in enumerate(codes):
        glyph = font['glyf'][cmap[code]]
        points, ends, flags = glyph.getCoordinates(font['glyf'])
        assert all(flag & 1 for flag in flags), f'Curved outline for {chr(code)}'
        contours, start = [], 0
        for end in ends:
            contours.append(list(points[start:end + 1]))
            start = end + 1
        left = min(x for x, y in points)
        assert max(x for x, y in points) - left <= CELL_W * PIXEL
        assert min(y for x, y in points) >= -PIXEL
        assert max(y for x, y in points) <= ASCENT * PIXEL
        pixels = []
        for y in range(CELL_H):
            for x in range(CELL_W):
                if inside(left + (x + .5) * PIXEL, (ASCENT - y - .5) * PIXEL, contours):
                    atlas.putpixel(((index % COLUMNS) * CELL_W + x, (index // COLUMNS) * CELL_H + y), (255, 255, 255, 255))
                    pixels.append((x, y))
        assert pixels, f'Empty glyph: {chr(code)}'
        widths[chr(code)] = max(x for x, y in pixels) + 1
        if chr(code) in 'AaM01':
            assert max(y for x, y in pixels) - min(y for x, y in pixels) + 1 == 5
    destination = PACK / 'assets/drunkshyt/textures/font/five.png'
    destination.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(destination, optimize=False)
    assert set(atlas.getchannel('A').tobytes()) == {0, 255}
    chars = [''.join(chr(c) for c in codes[i:i + COLUMNS]).ljust(COLUMNS, '\0') for i in range(0, len(codes), COLUMNS)]
    definition = {'providers': [
        {'type': 'space', 'advances': {' ': 3}},
        {'type': 'bitmap', 'file': 'drunkshyt:font/five.png', 'height': CELL_H, 'ascent': ASCENT, 'chars': chars},
        {'type': 'reference', 'id': 'minecraft:default'}
    ]}
    (PACK / 'assets/drunkshyt/font/five.json').write_text(json.dumps(definition, indent=2) + '\n')
    metadata = {'cellWidth': CELL_W, 'cellHeight': CELL_H, 'columns': COLUMNS, 'ascent': ASCENT,
                'firstCode': codes[0], 'lastCode': codes[-1], 'widths': widths, 'space': 3}
    (ROOT / 'preview/five-atlas.json').write_text(json.dumps(metadata, indent=2) + '\n')
    print(f'Generated {len(codes)} crisp glyphs; atlas {atlas.width}x{atlas.height}; five-pixel capitals, baseline {ASCENT}.')


if __name__ == '__main__':
    build()
