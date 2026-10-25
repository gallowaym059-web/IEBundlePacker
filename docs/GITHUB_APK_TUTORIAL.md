# Build IEBundlePacker APK with GitHub Actions (Android ARM32 + ARM64)

## 1. Create a repository
1. Create a GitHub repository named `IEBundlePacker`.
2. Upload the *contents* of this ZIP to the repository root, preserving folders such as `.github/workflows/` and `app/`.
3. Commit to the `main` branch. A push to `main` starts the workflow automatically.

## 2. Run the build
1. Open the repository's **Actions** tab.
2. Select **Build Android APK**.
3. If needed, choose **Run workflow**.
4. Open the latest run. If it fails, open the failed `Build debug APK` step and inspect the first Gradle/Python dependency error.

## 3. Download and install
1. After a successful run, open the run's **Artifacts** section.
2. Download `IEBundlePacker-debug-apk` and extract the artifact ZIP.
3. Install `app-debug.apk` on an ARM Android phone. Android may ask you to allow installs from the file manager used to open it.

The app is configured with `armeabi-v7a` (32-bit ARM) and `arm64-v8a` (64-bit ARM). x86 and x86_64 are intentionally excluded. The APK includes the configured ARM ABIs; it is not a promise that every third-party native dependency supports every ABI.

## 4. First-run test checklist
Use a copy of a small, non-encrypted bundle and keep the original untouched.
1. Open the bundle and confirm the asset count populates.
2. Search and filter for `Texture2D`.
3. Export a texture PNG.
4. Replace a texture with a PNG of exactly the original dimensions.
5. Use **SAVE AS** to write a new bundle, then reopen that output.
6. Test a Mesh OBJ export and JSON/raw export separately; some Unity versions/types may not export.
7. Repeat on an ARM32 device/emulator and an ARM64 device if both are targets.

## 5. Known limits
- UnityPy cannot decode every texture format on every Android ABI.
- Encrypted bundles are not supported.
- Mesh-to-OBJ export depends on UnityPy's exporter for that mesh/version. OBJ import is not implemented.
- This project is not a complete replacement for desktop UABEA/UABE; inspect errors and verify outputs in the target game/editor.
