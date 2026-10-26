# IEBundlePacker — Android Unity AssetBundle editor

IEBundlePacker targets Android ARM32 (`armeabi-v7a`) and ARM64 (`arm64-v8a`). It uses Chaquopy Python 3.11 and a pinned, pure-Python UnityPy source tree. The GitHub Actions workflow fetches the pinned source and packages it into the APK without attempting to compile UnityPy's desktop-only `UnityPyBoost` C++ extension.

## Build the APK on GitHub

1. Upload the **contents** of this project directory to the root of your GitHub repository. Preserve the `.github/workflows/android-apk.yml` path.
2. Open **Actions** and run **Build Android APK** (or push to `main`).
3. Download the `IEBundlePacker-debug-apk` artifact from the completed run.

The workflow downloads UnityPy source at a pinned commit, checks Python syntax, smoke-tests the backend on the build host, installs Gradle 8.9, builds the debug APK, checks that the APK contains native libraries for both ABIs, and uploads the APK.

## Features in this Android build

- Open a local Unity AssetBundle or supported Unity serialized asset file through Android's file picker.
- Browse objects with name/type/path-ID information, search, filter, and sort.
- Export object JSON/type-tree data and raw object bytes.
- Export OBJ for meshes when UnityPy exposes a compatible exporter.
- Replace Texture2D assets from PNG without decoding the original compressed texture. Replacement pixels are stored as uncompressed RGBA32 and the original dimensions are kept; use **Save As** to serialize the modified bundle.
- Save the modified bundle to a new file.

## Codec limitations

The core bundle parser uses Android-compatible `lz4`, `brotli`, Pillow, `attrs`, and `fsspec` packages. The project deliberately avoids UnityPy's optional native extensions because those extensions do not have wheels for this app's Android ARM32/ARM64 target in the current setup.

Consequently, Texture2D replacement works even when the source texture is compressed, because it writes RGBA32. PNG preview/export works for Pillow-supported formats (including common uncompressed formats and supported BCn formats), but some ETC, ASTC, and Crunch formats need the omitted native decoder and will show an explanatory error. FMOD-only audio decoding and native texture compression are not included; raw/JSON export remains available where the asset type supports it.

This does **not** claim to support encrypted bundles or every Unity version/asset type. Always keep an untouched copy of the original bundle. The build is not considered device-verified until the APK has completed the included CI checks and has been tested against a real bundle on-device.

## Validation status

- Python syntax check and backend import/dependency smoke test: included in the GitHub workflow after UnityPy is fetched.
- APK ABI payload: checked in the GitHub workflow for ARM32 and ARM64.
- Full Gradle build and runtime bundle operations: not run in this environment because Gradle and the Android build environment are not installed here. The included GitHub Actions workflow must pass, followed by testing the APK against a real bundle on-device.
