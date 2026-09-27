# New App: Grace Launcher

Grace Launcher is a list-based Android launcher with favorites, alphabetical app browsing, a calendar agenda, notification previews and home-screen media controls. Weather is optional and supplied by a separately installed Breezy Weather app.

- Source: https://github.com/Galaxy-rio/GraceLauncher
- Release: https://github.com/Galaxy-rio/GraceLauncher/releases/tag/v1.0.0
- Application ID: `com.galaxyrio.gracelauncher`
- Version: `1.0.0` (`1`), Android 9+
- Code license: GPL-3.0-only
- Weather artwork: official Material Symbols Outlined, Apache-2.0; local vectors and pinned source links are documented in `third_party/material-symbols/NOTICE.md`.
- Store metadata: English and Simplified Chinese under `fastlane/metadata/android/`, with four English screenshots
- Build: Gradle 9.5.0, AGP 9.3.3, OpenJDK 21, Android SDK 37, Build Tools 36.1.0
- No Internet permission, advertising or analytics SDKs. Calendar, notification and Breezy Weather access enable optional features.
- Developer-signed publication is requested via `Binaries` and `AllowedAPKSigningKeys`. The certificate fingerprint must be read from the published APK; no private signing material is needed.

Before submitting this draft, complete these items and retain any checklist supplied by GitLab:

- [x] Replace the previous weather artwork with Apache-2.0 Material Symbols and retain its license and source records.
- [ ] Publish the source tag containing the store metadata and screenshots.
- [ ] Upload the signed `GraceLauncher_1.0.0.apk` release asset.
- [ ] Generate the YAML with the final full commit hash and verified signing certificate fingerprint.
- [ ] Verify the metadata and build in F-Droid CI, including reproducibility against the published APK.

The Windows preparation build and screenshot capture are local checks only; they are not a Linux reproducibility result.
