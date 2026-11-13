"""Inspect PNGs and remove an entirely opaque alpha channel without changing RGB pixels.

Uses only Python's standard library. No resizing, compositing, or colour conversion.
"""
import argparse
import hashlib
import struct
import zlib
from pathlib import Path

SIGNATURE = b"\x89PNG\r\n\x1a\n"


def chunks(path):
    data = Path(path).read_bytes()
    if not data.startswith(SIGNATURE):
        raise ValueError(f"{path}: no es PNG")
    offset = 8
    result = []
    while offset < len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        crc = struct.unpack_from(">I", data, offset + 8 + length)[0]
        if zlib.crc32(kind + payload) != crc:
            raise ValueError(f"{path}: CRC incorrecto en {kind!r}")
        result.append((kind, payload))
        offset += 12 + length
    if not result or result[0][0] != b"IHDR" or result[-1][0] != b"IEND":
        raise ValueError(f"{path}: PNG incompleto")
    return result


def png_info(path):
    parts = chunks(path)
    width, height, depth, colour, _, _, interlace = struct.unpack(">IIBBBBB", parts[0][1])
    return {
        "width": width, "height": height, "depth": depth, "colour": colour,
        "interlace": interlace,
        "alpha": colour in (4, 6) or any(k == b"tRNS" for k, _ in parts),
    }


def _paeth(left, above, upper_left):
    estimate = left + above - upper_left
    a, b, c = abs(estimate - left), abs(estimate - above), abs(estimate - upper_left)
    return left if a <= b and a <= c else above if b <= c else upper_left


def _rows(parts, width, height, channels):
    raw = zlib.decompress(b"".join(p for k, p in parts if k == b"IDAT"))
    stride = width * channels
    if len(raw) != (stride + 1) * height:
        raise ValueError("Longitud de píxeles PNG incorrecta")
    previous = bytearray(stride)
    for y in range(height):
        offset = y * (stride + 1)
        method = raw[offset]
        row = bytearray(raw[offset + 1:offset + 1 + stride])
        if method == 1:
            for i in range(channels, stride):
                row[i] = (row[i] + row[i - channels]) & 255
        elif method == 2:
            for i in range(stride):
                row[i] = (row[i] + previous[i]) & 255
        elif method in (3, 4):
            for i in range(stride):
                left = row[i - channels] if i >= channels else 0
                upper_left = previous[i - channels] if i >= channels else 0
                predictor = ((left + previous[i]) // 2 if method == 3
                             else _paeth(left, previous[i], upper_left))
                row[i] = (row[i] + predictor) & 255
        elif method != 0:
            raise ValueError(f"Filtro PNG desconocido: {method}")
        yield row
        previous = row


def _encode_chunk(kind, payload):
    return (struct.pack(">I", len(payload)) + kind + payload
            + struct.pack(">I", zlib.crc32(kind + payload)))


def strip_opaque_alpha(path):
    """Return RGB SHA-256; refuse any transparent pixel instead of flattening it."""
    path = Path(path)
    parts = chunks(path)
    info = png_info(path)
    if info["colour"] != 6 or info["depth"] != 8 or info["interlace"] != 0:
        raise ValueError(f"{path}: se requiere PNG RGBA de 8 bits sin entrelazar")
    if any(k == b"acTL" for k, _ in parts):
        raise ValueError(f"{path}: no se modifica un PNG animado")
    compressor = zlib.compressobj(9)
    compressed = []
    digest = hashlib.sha256()
    for row in _rows(parts, info["width"], info["height"], 4):
        if row[3::4] != b"\xff" * info["width"]:
            raise ValueError(f"{path}: contiene transparencia real; no se ha modificado")
        rgb = bytearray(info["width"] * 3)
        rgb[0::3], rgb[1::3], rgb[2::3] = row[0::4], row[1::4], row[2::4]
        digest.update(rgb)
        compressed.append(compressor.compress(b"\0" + rgb))
    compressed.append(compressor.flush())
    output = bytearray(SIGNATURE)
    wrote_pixels = False
    for kind, payload in parts:
        if kind == b"IHDR":
            header = bytearray(payload)
            header[9] = 2  # RGB, with all original dimensions and bit depth preserved.
            payload = bytes(header)
        elif kind == b"IDAT":
            if wrote_pixels:
                continue
            payload = b"".join(compressed)
            wrote_pixels = True
        elif kind == b"sBIT":
            payload = payload[:3]
        output.extend(_encode_chunk(kind, payload))
    temporary = path.with_suffix(".opaque.tmp")
    temporary.write_bytes(output)
    check_parts = chunks(temporary)
    check_digest = hashlib.sha256()
    for row in _rows(check_parts, info["width"], info["height"], 3):
        check_digest.update(row)
    if check_digest.digest() != digest.digest():
        temporary.unlink()
        raise ValueError(f"{path}: la comprobación RGB no coincide")
    temporary.replace(path)
    return digest.hexdigest()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="+", type=Path)
    args = parser.parse_args()
    for image in args.paths:
        digest = strip_opaque_alpha(image)
        print(f"{image}: RGB idéntico, sin alfa, SHA-256 {digest}")
