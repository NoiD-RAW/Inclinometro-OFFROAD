#!/usr/bin/env python3
"""Exercise the actual iOS recorder and SQLite store with isolated GPS-session fixtures."""
from pathlib import Path
import tempfile
from native_test_tools import CORE, ROOT, run_swift

with tempfile.TemporaryDirectory(prefix="inclinometro-distance-") as tmp:
    directory = Path(tmp)
    run_swift([CORE / "ios/Recorder.swift", CORE / "ios/TripDb.swift", CORE / "ios/MaxTracker.swift",
               ROOT / "tests/native/RecordingDistanceTests.swift"], directory, [directory])
