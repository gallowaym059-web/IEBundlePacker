# Build IEBundlePacker APK on GitHub (Android only)

1. Create or open the GitHub repository.
2. Upload the **contents** of this project folder, including `.github/workflows/android-apk.yml`, `app/`, `build.gradle`, `settings.gradle`, and `gradle.properties`.
3. Commit the upload to the repository's `main` branch.
4. Open **Actions** and choose **Build Android APK**.
5. Select **Run workflow** when available, or make a commit to trigger the workflow.
6. Wait for the workflow to finish. If it fails, open the failed step and read its log; do not download an APK from a failed run.
7. On a successful run, open the run page, scroll to **Artifacts**, and download `IEBundlePacker-debug-apk`.

The workflow fetches UnityPy source at a pinned commit, checks syntax and backend imports on the build host, downloads Gradle 8.9, uses Python 3.11 via Chaquopy, installs Android-compatible wheels for compression/image support, builds the APK, and verifies that both `armeabi-v7a` and `arm64-v8a` native payloads exist.

## Important limitations

- Build using this GitHub Actions workflow; it fetches the UnityPy source before Gradle runs.
- Texture replacement writes PNG pixels as RGBA32 and does not need to decode the original texture first.
- Some ETC/ASTC/Crunch texture PNG exports and FMOD-only audio decoding need native codecs that are not bundled. Use raw/JSON export where available or replace Texture2D assets with PNG.
- A successful Android build does not itself prove every Unity AssetBundle is supported. Test on a copy of your actual bundle and preserve the original.
