#!/usr/bin/env python3
"""Exercise real Swift/Kotlin exporters and validate GPX data and namespace interoperability."""
from pathlib import Path
import tempfile
import xml.etree.ElementTree as ET
from native_test_tools import CORE, ROOT, run_kotlin, run_swift

GPX = "http://www.topografix.com/GPX/1/1"
INCL = "urn:dev.arklay.inclinometro:gpx:1"
ns = {"g": GPX, "i": INCL}
with tempfile.TemporaryDirectory(prefix="inclinometro-exports-") as tmp:
    directory = Path(tmp)
    swift_gpx, kotlin_gpx = directory / "ios.gpx", directory / "android.gpx"
    run_swift([CORE / "ios/Export.swift", ROOT / "tests/native/ExportTests.swift"], directory, [swift_gpx])
    run_kotlin([CORE / "android/src/main/java/dev/arklay/inclinometro/core/Export.kt",
                ROOT / "tests/native/ExportTests.kt"],
               "dev.arklay.inclinometro.core.ExportTestsKt", directory, [kotlin_gpx])
    for file in [swift_gpx, kotlin_gpx]:
        root = ET.parse(file).getroot()
        assert root.tag == f"{{{GPX}}}gpx" and root.get("version") == "1.1"
        assert root.findtext("g:trk/g:name", namespaces=ns) == "Ruta & <arroyo>"
        points = root.findall("g:trk/g:trkseg/g:trkpt", ns)
        assert len(points) == 2, "Samples without GPS must be omitted"
        assert float(points[0].get("lat")) == 42 and float(points[0].get("lon")) == -1
        assert points[0].findtext("g:time", namespaces=ns) == "2023-11-14T22:13:20Z"
        assert points[1].find("g:ele", ns) is None
        for point, pitch, roll in zip(points, [55, -12.5], [-45, 8]):
            extension = point.find("g:extensions", ns)
            assert extension is not None and len(extension) == 2
            assert all(child.tag.startswith(f"{{{INCL}}}") for child in extension), "GPX extensions require a foreign namespace"
            assert float(extension.findtext("i:cabeceo", namespaces=ns)) == pitch
            assert float(extension.findtext("i:alabeo", namespaces=ns)) == roll
        print(f"{file.name}: GPX namespace and data checks passed")
