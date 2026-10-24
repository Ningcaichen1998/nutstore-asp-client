# -*- coding: utf-8 -*-
"""生成安卓应用图标（纯标准库，无 PIL）：蓝底圆角 + 白色"坚果"云朵图形"""
import struct, zlib, math, os

SIZE = 192
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "app", "res", "drawable", "icon.png")

def px(x, y):
    """返回 (r,g,b,a)"""
    cx, cy = SIZE / 2, SIZE / 2
    # 圆角矩形背景
    r = 42
    in_bg = True
    dx = min(x, SIZE - 1 - x)
    dy = min(y, SIZE - 1 - y)
    if dx < r and dy < r:
        d = math.hypot(r - dx, r - dy)
        if d > r:
            in_bg = False
    # 外描边
    if not in_bg:
        return (0, 0, 0, 0)
    # 渐变蓝底（左上浅，右下深）
    t = (x + y) / (2 * SIZE)
    br = int(55 + (35 - 55) * t)
    bg = int(150 + (236 - 150) * t)
    bb = int(236 + (75 - 236) * t)
    base = (br, bg, bb, 255)

    # 白色云朵：三个圆 + 底部矩形
    cloud = False
    for (ccx, ccy, cr) in [(78, 96, 26), (110, 84, 22), (124, 104, 18)]:
        if math.hypot(x - ccx, y - ccy) <= cr:
            cloud = True
    if 78 <= x <= 142 and 96 <= y <= 116:
        cloud = True
    if cloud:
        return (255, 255, 255, 255)

    # 白色圆点（坚果）
    if math.hypot(x - 70, y - 70) <= 7:
        return (255, 255, 255, 255)

    return base

def png_chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)

rows = []
for y in range(SIZE):
    row = b"\x00"
    for x in range(SIZE):
        r, g, b, a = px(x, y)
        row += struct.pack("4B", r, g, b, a)
    rows.append(row)

ihdr = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)
png = b"\x89PNG\r\n\x1a\n"
png += png_chunk(b"IHDR", ihdr)
png += png_chunk(b"IDAT", zlib.compress(b"".join(rows), 9))
png += png_chunk(b"IEND", b"")

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "wb") as f:
    f.write(png)
print("icon written:", OUT, len(png), "bytes")
