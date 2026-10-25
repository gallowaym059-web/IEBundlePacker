# IEBundlePacker

Android Unity AssetBundle workbench with a dark, searchable asset list. The project now connects its native Android UI to a UnityPy Python backend through Chaquopy.

## Supported Android ABIs

- `armeabi-v7a` (32-bit ARM)
- `arm64-v8a` (64-bit ARM)

No x86/x86_64 ABI is configured. The debug APK is built with both ARM ABIs included, subject to the Python package/native dependency availability in the CI build.

## Implemented backend actions

- Open a bundle and list UnityPy-readable objects.
- Search, filter, and sort asset rows.
- Export `Texture2D` to PNG when its format can be decoded on the device.
- Replace a `Texture2D` with a PNG of matching dimensions, then save the modified bundle.
- Export Mesh to OBJ where the installed UnityPy version provides a usable exporter.
- Export JSON/type-tree or raw object data for inspection.
- Save the UnityPy environment to a user-selected output file.

## Limitations

- This is not a universal editor for every Unity object or Unity version.
- Encrypted/proprietary bundles are unsupported.
- Texture decoding and Python native wheels can vary by format and ABI. Android 32-bit and 64-bit builds must be confirmed in GitHub Actions; runtime operations still need device testing.
- OBJ import/replacement is not implemented. Sprite reconstruction, arbitrary object-field editing, and every audio/model format are not promised.
- Very large bundles can exceed phone memory.

## Build

Follow `docs/GITHUB_APK_TUTORIAL.md`. The workflow is configured for Android ARM32 and ARM64 only. A green build proves compilation/package resolution, not that every asset format works on-device.