#!/usr/bin/env python3
"""Regression tests against both production maximum filters (requires Swift and Android build caches)."""
from pathlib import Path
import tempfile
from native_test_tools import CORE, ROOT, run_kotlin, run_swift

with tempfile.TemporaryDirectory(prefix="inclinometro-maxima-") as tmp:
    directory = Path(tmp)
    run_swift([CORE / "ios/MaxTracker.swift", ROOT / "tests/native/MaxTrackerTests.swift"], directory)
    run_kotlin([CORE / "android/src/main/java/dev/arklay/inclinometro/core/MaxTracker.kt",
                ROOT / "tests/native/MaxTrackerTests.kt"],
               "dev.arklay.inclinometro.core.MaxTrackerTestsKt", directory)
