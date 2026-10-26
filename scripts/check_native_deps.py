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
