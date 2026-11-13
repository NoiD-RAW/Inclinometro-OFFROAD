#!/usr/bin/env python3
"""Exercise the actual iOS SQLite store and recorder with isolated faults, without a simulator."""
from pathlib import Path
import tempfile
from native_test_tools import CORE, ROOT, run_swift

with tempfile.TemporaryDirectory(prefix="inclinometro-storage-") as tmp:
    directory = Path(tmp)
    run_swift([CORE / "ios/TripDb.swift", CORE / "ios/Recorder.swift", CORE / "ios/MaxTracker.swift",
               ROOT / "tests/native/StorageTests.swift"], directory, [directory])
