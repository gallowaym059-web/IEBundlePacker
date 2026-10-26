#!/usr/bin/env python3
"""Fail if any native library in the APK needs a shared library that is neither
bundled in the APK nor provided by Android itself.

Usage: check_native_deps.py path/to/app-debug.apk
"""
import pathlib
import re
import subprocess
import sys
import tempfile
import zipfile

ABIS = ("armeabi-v7a", "arm64-v8a")

# Libraries guaranteed by the Android platform (NDK stable system libraries).
SYSTEM_LIBS = {
    "libc.so", "libm.so", "libdl.so", "liblog.so", "libz.so", "libandroid.so",
    "libjnigraphics.so", "libstdc++.so", "libEGL.so", "libGLESv1_CM.so",
    "libGLESv2.so", "libGLESv3.so", "libvulkan.so", "libOpenSLES.so",
    "libmediandk.so", "libnativewindow.so", "libcamera2ndk.so", "libaaudio.so",
    "libsync.so", "libbinder_ndk.so", "libamidi.so", "libneuralnetworks.so",
}


def needed(so_path):
    out = subprocess.run(["readelf", "-d", str(so_path)], capture_output=True,
                         text=True, check=True).stdout
    return re.findall(r"\(NEEDED\)\s+Shared library: \[(.+?)\]", out)


def main(apk):
    work = pathlib.Path(tempfile.mkdtemp())
    problems = []
    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
        for abi in ABIS:
            root = work / abi
            provided = set()   # libs the dynamic linker can find in the APK
            to_check = []      # every .so we must verify
            for n in names:
                if n.startswith(f"lib/{abi}/") and n.endswith(".so"):
                    z.extract(n, root)
                    provided.add(pathlib.PurePosixPath(n).name)
                    to_check.append(root / n)
            # Chaquopy keeps pip-installed packages (and their .so files) in
            # assets/chaquopy/*.imy archives, which are zip files.
            imys = [n for n in names if n.startswith("assets/chaquopy/")
                    and n.endswith(".imy") and abi in n]
            if not imys:
                print(f"WARNING: no requirements archive found for {abi}")
            for imy in imys:
                imy_path = z.extract(imy, root)
                if not zipfile.is_zipfile(imy_path):
                    print(f"WARNING: {imy} is not a zip; skipped")
                    continue
                with zipfile.ZipFile(imy_path) as iz:
                    for n in iz.namelist():
                        if n.endswith(".so"):
                            iz.extract(n, root / "req")
                            to_check.append(root / "req" / n)
            print(f"[{abi}] checking {len(to_check)} native libraries")
            for so in to_check:
                for dep in needed(so):
                    if dep in provided or dep in SYSTEM_LIBS:
                        continue
                    problems.append(f"{abi}: {so.name} needs missing {dep}")
    if problems:
        print("\nMISSING NATIVE DEPENDENCIES:")
        for p in sorted(set(problems)):
            print("  -", p)
        sys.exit(1)
    print("All native dependencies are satisfied for every ABI.")


if __name__ == "__main__":
    main(sys.argv[1])
