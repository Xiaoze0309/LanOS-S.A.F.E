"""纯标准库生成 App 图标：裁掉白边 → 居中方形 → 缩放 512 → 输出 PNG。

用法：python3 tools/make_icon.py 输入.png 输出.png
没有 Pillow 也能跑（Android/Termux 上 pip 装不了 Pillow 时用这个兜底）。
"""

from __future__ import annotations

import struct
import sys
import zlib


def load_png(path: str) -> tuple[int, int, int, bytearray]:
    data = open(path, "rb").read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit("不是 PNG 文件")
    pos = 8
    idat = bytearray()
    width = height = 0
    color_type = 0
    while pos < len(data):
        length = int.from_bytes(data[pos : pos + 4], "big")
        kind = data[pos + 4 : pos + 8]
        chunk = data[pos + 8 : pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, bit_depth, color_type, _, _, interlace = struct.unpack(">IIBBBBB", chunk)
            if bit_depth != 8 or interlace != 0:
                raise SystemExit("只支持 8bit 非隔行 PNG")
        elif kind == b"IDAT":
            idat += chunk
        elif kind == b"IEND":
            break

    bpp = {0: 1, 2: 3, 4: 2, 6: 4}.get(color_type)
    if bpp is None:
        raise SystemExit(f"不支持的 color type {color_type}")

    raw = zlib.decompress(bytes(idat))
    stride = width * bpp
    out = bytearray(width * height * bpp)
    prev = bytearray(stride)
    cursor = 0

    for y in range(height):
        filter_type = raw[cursor]
        cursor += 1
        line = bytearray(raw[cursor : cursor + stride])
        cursor += stride
        if filter_type == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif filter_type == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif filter_type == 3:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif filter_type == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride : (y + 1) * stride] = line
        prev = line

    return width, height, bpp, out


def ink_bbox(width: int, height: int, bpp: int, pixels: bytearray) -> tuple[int, int, int, int]:
    """按“连续墨水行/列”找主体边界：自动忽略角落的孤立噪点。"""
    stride = width * bpp
    row_ink = [0] * height
    col_ink = [0] * width
    for y in range(height):
        base = y * stride
        for x in range(width):
            i = base + x * bpp
            if bpp == 4:
                r, g, b, a = pixels[i], pixels[i + 1], pixels[i + 2], pixels[i + 3]
            elif bpp == 3:
                r, g, b = pixels[i], pixels[i + 1], pixels[i + 2]
                a = 255
            else:
                r = g = b = pixels[i]
                a = 255
            if a > 16 and (r + g + b) / 3 < 240:
                row_ink[y] += 1
                col_ink[x] += 1

    def longest_run(counts: list[int], minimum: int = 5) -> tuple[int, int]:
        best = (0, len(counts) - 1)
        best_len = 0
        start = None
        for i, value in enumerate(counts):
            if value >= minimum:
                if start is None:
                    start = i
            elif start is not None:
                if i - start > best_len:
                    best, best_len = (start, i - 1), i - start
                start = None
        if start is not None and len(counts) - start > best_len:
            best = (start, len(counts) - 1)
        return best

    top, bottom = longest_run(row_ink)
    left, right = longest_run(col_ink)
    return left, top, right + 1, bottom + 1


def render_square(
    width: int,
    height: int,
    bpp: int,
    pixels: bytearray,
    box: tuple[int, int, int, int],
    size: int = 512,
    padding: float = 0.10,
) -> bytes:
    x0, y0, x1, y1 = box
    side = int(max(x1 - x0, y1 - y0) * (1 + padding * 2))
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    left = max(0, min(width - side, cx - side // 2))
    top = max(0, min(height - side, cy - side // 2))
    side = min(side, width - left, height - top)

    stride = width * bpp
    rows = []
    for dy in range(size):
        sy0 = top + dy * side // size
        sy1 = max(sy0 + 1, top + (dy + 1) * side // size)
        line = bytearray()
        for dx in range(size):
            sx0 = left + dx * side // size
            sx1 = max(sx0 + 1, left + (dx + 1) * side // size)
            r = g = b = a = count = 0
            for sy in range(sy0, sy1):
                base = sy * stride
                for sx in range(sx0, sx1):
                    i = base + sx * bpp
                    if bpp == 4:
                        pr, pg, pb, pa = pixels[i], pixels[i + 1], pixels[i + 2], pixels[i + 3]
                    elif bpp == 3:
                        pr, pg, pb, pa = pixels[i], pixels[i + 1], pixels[i + 2], 255
                    else:
                        pr = pg = pb = pixels[i]
                        pa = 255
                    r += pr * pa
                    g += pg * pa
                    b += pb * pa
                    a += pa
                    count += 1
            if a:
                line += bytes((r // a, g // a, b // a))
            else:
                line += b"\xff\xff\xff"
        rows.append(b"\x00" + bytes(line))
    return b"".join(rows)


def write_png(path: str, size: int, raw_rows: bytes) -> None:
    def chunk(kind: bytes, payload: bytes) -> bytes:
        return (
            struct.pack(">I", len(payload))
            + kind
            + payload
            + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
        )

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)
    body = zlib.compress(raw_rows, 9)
    with open(path, "wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", body) + chunk(b"IEND", b""))


def main() -> None:
    src, dst = sys.argv[1], sys.argv[2]
    size = int(sys.argv[3]) if len(sys.argv) > 3 else 512
    width, height, bpp, pixels = load_png(src)
    box = ink_bbox(width, height, bpp, pixels)
    print(f"源图 {width}x{height}，内容边界 {box}，输出 {size}x{size}")
    rows = render_square(width, height, bpp, pixels, box, size)
    write_png(dst, size, rows)
    print("已写入", dst)


if __name__ == "__main__":
    main()
