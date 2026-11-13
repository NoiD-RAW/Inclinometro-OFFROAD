"""Comprueba textos e imágenes de Google Play y App Store, sin dependencias.

Tamaños de Apple:
https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications/
"""
import json
import re
import struct
import sys
from pathlib import Path

from png_assets import png_info

STORE = Path(__file__).resolve().parent
ROOT = STORE.parent
text = (STORE / "ficha.md").read_text(encoding="utf-8")
errors = []


def check(condition, message):
    print(f"{message}: {'ok' if condition else 'ERROR'}")
    if not condition:
        errors.append(message)


def field(name):
    match = re.search(r"\*\*" + re.escape(name) + r"\*\*[^:]*: (.+)", text)
    if not match:
        raise ValueError(f"Falta el campo {name}")
    return match.group(1).strip()


def jpeg_size(path):
    """Read SOF dimensions without decoding or changing the JPEG."""
    data = path.read_bytes()
    if data[:2] != b"\xff\xd8":
        raise ValueError(f"{path}: no es JPEG")
    offset = 2
    while offset < len(data):
        if data[offset] != 255:
            raise ValueError(f"{path}: marcador JPEG incorrecto")
        while data[offset] == 255:
            offset += 1
        marker = data[offset]
        offset += 1
        if marker in (0xD8, 0xD9) or 0xD0 <= marker <= 0xD7:
            continue
        length = struct.unpack_from(">H", data, offset)[0]
        if marker in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
                      0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
            height, width = struct.unpack_from(">HH", data, offset + 3)
            return width, height
        if marker == 0xDA:
            break
        offset += length
    raise ValueError(f"{path}: JPEG sin dimensiones")


def main():
    description = re.search(r"<!-- descripcion -->\n(.*?)\n<!-- /descripcion -->", text, re.S)
    if not description:
        raise ValueError("Falta el bloque de descripción completa")
    ios_description = re.search(r"<!-- descripcion-ios -->\n(.*?)\n<!-- /descripcion-ios -->", text, re.S)
    if not ios_description:
        raise ValueError("Falta el bloque de descripción de App Store")
    limits = [
        ("Nombre", len(field("Nombre")), 30, "caracteres"),
        ("Descripción breve", len(field("Descripción breve")), 80, "caracteres"),
        ("Descripción completa", len(description.group(1)), 4000, "caracteres"),
        ("Descripción App Store", len(ios_description.group(1)), 4000, "caracteres"),
        ("Subtítulo", len(field("Subtítulo")), 30, "caracteres"),
        ("Palabras clave", len(field("Palabras clave").encode("utf-8")), 100, "bytes"),
    ]
    for name, count, limit, unit in limits:
        check(count <= limit, f"{name}: {count}/{limit} {unit}")

    icon = png_info(STORE / "google-play/icono-512.png")
    check((icon["width"], icon["height"]) == (512, 512), "Icono Google Play: 512×512")
    check(jpeg_size(STORE / "google-play/grafico-funciones-1024x500.jpg") == (1024, 500),
          "Gráfico Google Play: 1024×500")
    play_screenshots = sorted((STORE / "google-play/capturas").glob("*.jpg"))
    check(2 <= len(play_screenshots) <= 8, f"Capturas de teléfono Google Play: {len(play_screenshots)}")
    for path in play_screenshots:
        width, height = jpeg_size(path)
        check(min(width, height) >= 320 and max(width, height) <= 3840
              and max(width, height) <= min(width, height) * 2,
              f"{path.relative_to(STORE)}: {width}×{height}")

    # Current required iPhone and iPad 13-inch screenshot sizes, in portrait order.
    iphone_sizes = {(1179, 2556), (1206, 2622), (1260, 2736), (1290, 2796), (1320, 2868),
                    (1242, 2688), (1284, 2778)}
    ipad_sizes = {(2064, 2752), (2048, 2732)}
    iphone_count = ipad_count = 0
    directories = sorted(p for p in (STORE / "app-store").iterdir() if p.is_dir())
    for directory in directories:
        screenshots = sorted(directory.glob("*.png"))
        check(1 <= len(screenshots) <= 10, f"Capturas {directory.name}: {len(screenshots)}")
        for path in screenshots:
            info = png_info(path)
            size = info["width"], info["height"]
            dimensions = re.search(r"(\d+)x(\d+)$", directory.name)
            expected = tuple(map(int, dimensions.groups())) if dimensions else None
            is_ipad = directory.name.startswith("ipad-")
            allowed = ipad_sizes if is_ipad else iphone_sizes
            accepted = size in allowed or size[::-1] in allowed
            check(accepted and expected == size,
                  f"{path.relative_to(STORE)}: {size[0]}×{size[1]} válido")
            check(not info["alpha"], f"{path.relative_to(STORE)}: sin canal alfa")
            if accepted:
                if is_ipad:
                    ipad_count += 1
                else:
                    iphone_count += 1
    check(iphone_count > 0, "App Store: capturas de iPhone disponibles")
    config = json.loads((ROOT / "app.json").read_text(encoding="utf-8"))["expo"]
    check(config.get("ios", {}).get("bundleIdentifier") == "dev.arklay.inclinometro",
          "Identificador iOS: dev.arklay.inclinometro")
    check(config.get("android", {}).get("package") == "dev.arklay.inclinometro",
          "Paquete Android: dev.arklay.inclinometro")
    if config.get("ios", {}).get("supportsTablet", False):
        check(ipad_count > 0, "App Store: capturas de iPad 13 pulgadas disponibles (supportsTablet=true)")
    app_icon = png_info(ROOT / config["icon"])
    check((app_icon["width"], app_icon["height"]) == (1024, 1024) and not app_icon["alpha"],
          "Icono iOS: 1024×1024 sin alfa")
    return 1 if errors else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, OSError, KeyError, struct.error, IndexError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        sys.exit(1)
