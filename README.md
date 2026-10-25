# IEBundlePacker (Android ARM32/ARM64)

Dark native Android interface scaffold for inspecting Unity AssetBundle files. The project targets only `armeabi-v7a` (ARM32) and `arm64-v8a` (ARM64), and uses Android API 24 as its minimum due to the configured Chaquopy version.

## Build APK on GitHub

1. Upload the **contents** of this folder to the root of a GitHub repository (do not upload only the ZIP).
2. Open **Actions** and run **Build Android APK**.
3. Download the `IEBundlePacker-debug-apk` artifact from a successful run.

## Current limitations — read before installing

This version is designed to build and keep the interface from crashing when Python initialization fails. It does **not** yet include a compatible UnityPy native extension. Therefore, opening a bundle will report that UnityPy is unavailable instead of parsing it. PNG replacement also requires Pillow, which is not included in this build. This is a build-stability scaffold, not a functional AssetBundle editor yet.

UnityPy 1.25.x currently tries to build native extensions from source in Chaquopy; the build log shows `Chaquopy_cannot_compile_native_code`. To implement actual bundle editing, the project needs an Android-compatible build of UnityPy and each native dependency for both ARM ABIs, or a different native/Java backend. Do not save important bundles with this scaffold.

## Validation status

- Python source syntax: checked.
- ZIP integrity: checked after packaging.
- Full Gradle build: not run in this environment (Gradle is not installed here).
- Device launch and actual bundle editing: not verified.
