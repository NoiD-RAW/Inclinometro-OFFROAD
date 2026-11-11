"""Comprueba los límites de la ficha (store/ficha.md) que piden Google Play y App Store."""
import re
import sys
from pathlib import Path

text = (Path(__file__).parent / "ficha.md").read_text(encoding="utf-8")

def field(name):
    m = re.search(r"\*\*" + re.escape(name) + r"\*\*[^:]*: (.+)", text)
    return m.group(1).strip()

desc = re.search(r"<!-- descripcion -->\n(.*?)\n<!-- /descripcion -->", text, re.S).group(1)
checks = [
    ("Nombre", len(field("Nombre")), 30, "caracteres"),
    ("Descripción breve", len(field("Descripción breve")), 80, "caracteres"),
    ("Descripción completa", len(desc), 4000, "caracteres"),
    ("Subtítulo", len(field("Subtítulo")), 30, "caracteres"),
    ("Palabras clave", len(field("Palabras clave").encode("utf-8")), 100, "bytes"),
]
ok = True
for name, n, limit, unit in checks:
    mark = "ok" if n <= limit else "SE PASA"
    ok &= n <= limit
    print(f"{name}: {n}/{limit} {unit} {mark}")
sys.exit(0 if ok else 1)
