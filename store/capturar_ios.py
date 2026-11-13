"""Capture real iOS Simulator screens; no resizing, artwork, or compositing.

Build a simulator .app first with Expo, then use a dedicated, booted simulator:
  python3 store/capturar_ios.py --device UDID --app /path/InclinometroOFFROAD.app \
    --output store/app-store/ipad-13-2064x2752
For the existing phone settings screenshot, add: --screens ajustes
Requires Xcode, macOS, and permission to access CoreSimulator.
"""
import argparse
import json
import plistlib
import shutil
import subprocess
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path

from png_assets import png_info, strip_opaque_alpha

BUNDLE = "dev.arklay.inclinometro"
SCREENS = {
    "industrial": ("01-industrial.png", "industrial", ""),
    "topografico": ("03-topografico.png", "topo", ""),
    "salida-mapa": ("05-salida-mapa.png", "industrial", "detail"),
    "ajustes": ("06-ajustes.png", "industrial", "settings"),
}


def simctl(*arguments, capture=False):
    return subprocess.run(["xcrun", "simctl", *arguments], check=True,
                          text=True, capture_output=capture)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True, help="UDID del simulador dedicado, ya arrancado")
    parser.add_argument("--app", required=True, type=Path, help=".app para iphonesimulator")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--screens", nargs="+", choices=SCREENS,
                        default=["industrial", "topografico", "ajustes"])
    parser.add_argument("--settle-seconds", type=float, default=8)
    parser.add_argument("--restart", action="store_true",
                        help="Reiniciar solo este simulador dedicado para cerrar permisos pendientes")
    args = parser.parse_args()
    app_info = plistlib.loads((args.app / "Info.plist").read_bytes())
    if app_info.get("CFBundleIdentifier") != BUNDLE:
        parser.error(f"El bundle debe ser {BUNDLE}")
    if app_info.get("CFBundleSupportedPlatforms") != ["iPhoneSimulator"]:
        parser.error("Se requiere una compilación para iOS Simulator")
    devices = json.loads(simctl("list", "devices", "available", "-j", capture=True).stdout)["devices"]
    matches = [(runtime, device) for runtime, group in devices.items()
               for device in group if device["udid"] == args.device]
    if len(matches) != 1 or matches[0][1]["state"] != "Booted":
        parser.error("El simulador indicado debe estar disponible y arrancado")
    runtime, device = matches[0]
    args.output.mkdir(parents=True, exist_ok=True)
    simctl("install", args.device, str(args.app.resolve()))
    # This script is for a dedicated simulator: pregrant the permission to keep
    # first-launch system dialogs out of store screenshots, and allow GPS demos.
    simctl("privacy", args.device, "grant", "location", BUNDLE)
    if args.restart:
        simctl("shutdown", args.device)
        simctl("boot", args.device)
        simctl("bootstatus", args.device, "-b")
    simctl("status_bar", args.device, "override", "--time", "9:41",
           "--dataNetwork", "wifi", "--wifiMode", "active", "--wifiBars", "3",
           "--batteryState", "charged", "--batteryLevel", "100")
    captures = []
    def launch(demo, theme="industrial"):
        simctl("spawn", args.device, "defaults", "write", BUNDLE, "theme", theme)
        for attempt in range(3):
            try:
                simctl("launch", "--terminate-running-process", args.device, BUNDLE,
                       "-AppleLanguages", "(es)", "-AppleLocale", "es_ES",
                       "-simGravity", "1.5,9.6,0.5", "-simHeading", "18",
                       "-simAltitude", "900", "-demo", demo)
                return
            except subprocess.CalledProcessError as error:
                # SpringBoard can briefly report ESRCH immediately after boot.
                if error.returncode != 3 or attempt == 2:
                    raise
                time.sleep(3)

    try:
        if "salida-mapa" in args.screens:
            # Record a fresh test route through the actual app and CoreLocation.
            # These are invented simulator positions, never a user's saved trip.
            route = ["42.1000,1.8000", "42.1002,1.8001", "42.1004,1.8003",
                     "42.1006,1.8004", "42.1008,1.8007", "42.1010,1.8010"]
            simctl("location", args.device, "set", route[0])
            launch("trip-start")
            time.sleep(4)
            for point in route[1:]:
                simctl("location", args.device, "set", point)
                time.sleep(3)
            launch("trip-stop")
            time.sleep(4)
        for name in args.screens:
            filename, theme, demo = SCREENS[name]
            # Launch arguments are simulator-only in Engine.swift/demoArg().
            # A dedicated simulator prevents changing the user's personal app data.
            print(f"Capturando {device['name']}: {filename}", flush=True)
            launch(demo, theme)
            time.sleep(max(2, args.settle_seconds))
            path = args.output / filename
            # CoreSimulator may lack permission to overwrite an existing file
            # in Documents. Capture a new temporary file, then replace it here.
            with tempfile.TemporaryDirectory(prefix="inclinometro-capture-", dir="/private/tmp") as directory:
                native_path = Path(directory) / filename
                simctl("io", args.device, "screenshot", "--type=png", str(native_path))
                rgb_hash = strip_opaque_alpha(native_path) if png_info(native_path)["alpha"] else None
                replacement = path.with_suffix(".capture.tmp")
                shutil.copy2(native_path, replacement)
                replacement.replace(path)
            info = png_info(path)
            captures.append({"file": filename, "width": info["width"], "height": info["height"],
                             "alpha": info["alpha"], "rgbSha256": rgb_hash})
    finally:
        simctl("status_bar", args.device, "clear")
    provenance = {
        "method": "xcrun simctl io screenshot; opaque alpha removed losslessly, native dimensions preserved",
        "capturedAtUTC": datetime.now(timezone.utc).isoformat(),
        "simulatorName": device["name"], "runtime": runtime,
        "bundleIdentifier": BUNDLE,
        "appVersion": app_info.get("CFBundleShortVersionString"),
        "buildNumber": app_info.get("CFBundleVersion"),
        "screenshots": captures,
        "gpsData": "Invented simulator test route recorded by the app" if "salida-mapa" in args.screens else None,
    }
    (args.output / "captura.json").write_text(json.dumps(provenance, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
