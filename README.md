# Aksi File Manager

**All in One File Explorer** — Android file manager built with Kotlin, Jetpack Compose, and Material 3.

- **Application ID:** `id.aksisoft.aksifile`
- **Minimum Android:** 8.0 / API 26
- **Compile / target SDK:** 36 / API 36 (Android 16 — wajib Google Play per 31 Agustus 2026)
- **versionName 1.1 | versionCode 2** — signed `tukangdigital.keystore`, R8 minify + shrinkResources aktif
- **Default language:** English; Indonesian is available in Settings
- **Theme:** System, Light, Dark

## Included features

### File manager
- Home screen, storage overview, internal/removable-volume roots where Android exposes them, path navigation, and Grid/List/Card views.
- Search across reachable files, hidden-file toggle, favorites, recent files, and custom galleries.
- Open/open-with, share, browser handoff, copy/cut/paste, rename, delete confirmation, create folder/text/Markdown/HTML file, size details, folder analysis, and unique output filenames.
- Storage analyzer, largest-folder view, duplicate detection/removal, temporary/log and empty-folder cleaner, and installed-app APK export/share/app-info/uninstall actions.

### Editing and media
- Text/code editor with line numbers, syntax highlighting, find/replace, Save As, Markdown/HTML preview, and browser handoff.
- Image editor with crop, aspect ratios, rotate/flip, filter, brightness, contrast, saturation, tone, sharpen, and PNG/JPEG/WEBP export.
- Video trim-to-MP4 using Android media APIs without re-encoding (keyframe-accurate rather than frame-accurate cuts).
- PDF viewer, text extraction, merge, split, and text stamping; PDF merge uses PDFBox when supported and an image-rendered fallback otherwise.
- CSV table view; DOCX text read/edit with a `.bak` backup; XLSX sheet table view; PPTX slide text cards.
- ZIP, 7Z, RAR, TAR, TAR.GZ, and GZ archive browsing/extraction; ZIP, 7Z, and TAR.GZ creation.
- QR scanner using camera or an image selected from storage.

### Network and settings
- FTP, explicit FTPS, and SFTP server profiles; remote browse, multi-file upload/download, recursive folder download, create folder, rename, and delete.
- PIN gate for the app and protected APK-opening flow, default browser view/path settings, hidden files, cache clearing, and a Google test banner toggle.
- Saved server profiles are encrypted with AES/GCM using a non-exportable Android Keystore key. Older plaintext `srv` preferences are migrated on first read.
- SFTP uses strict `known_hosts` validation. Use **Verify key**, compare the SHA-256 fingerprint with a trusted external source, and only then choose **Trust key**. Changed host keys are rejected rather than silently replaced; a separate Forget key action is available for an intentionally verified key rotation.

## Build

1. Open this folder in Android Studio (JDK 17 recommended).
2. Install Android SDK Platform 36 and let Gradle sync download the listed dependencies.
3. Run the `app` configuration on an Android device/emulator, or use `gradle :app:assembleDebug` if Gradle is installed and configured locally.
4. Debug APK output, after a successful build: `app/build/outputs/apk/debug/app-debug.apk`.

This repository now builds headlessly. Update v1.1 (versionCode 2): target/compileSdk raised to 36 (Android 16) per Google Play policy effective 31 August 2026, release signing via `tukangdigital.keystore` (`keystore.properties`), APK + AAB outputs:

- APK : `app/build/outputs/apk/release/app-release.apk`
- AAB : `app/build/outputs/bundle/release/app-release.aab`

Build commands (Gradle 8.11.1, AGP 8.10.1, JDK 17+):
```bash
gradle :app:assembleDebug   # debug APK
gradle :app:assembleRelease # release APK (R8 minified, signed)
gradle :app:bundleRelease   # App Bundle (AAB, signed)
```

Previous note from the first source drop: this archive originally had no Gradle wrapper JAR and was never compiled by its generator; the authoritative compile had to happen in a real Android build environment. That is resolved by this repository's updated build config.

## Project map

- `MainActivity.kt` — app shell, navigation, Home, Explorer, permissions, storage roots.
- `Ui.kt` — design tokens, shared Compose components, translations, settings persistence, PIN UI.
- `FS.kt` / `Media.kt` — filesystem, utilities, image/video/PDF/QR media helpers.
- `Screens.kt` / `Screens2.kt` — analyzer, duplicates, gallery, tools, settings.
- `Arc.kt` — archive browse/create/extract.
- `Editor.kt` / `ImageEditor.kt` / `MediaScreens.kt` — editor, image tools, video and QR screens.
- `Office.kt` / `PdfTools.kt` — Office OOXML and PDF operations.
- `Ftp.kt` / `Net.kt` — FTP/FTPS/SFTP interface and transfers, strict SFTP host-key workflow.
- `Extras.kt` — custom galleries, installed apps, cleaner.
- `assets/aksi-file-manager-icon.svg` — standalone app icon SVG.
- `assets/aksi-file-manager-logo.svg` — wordmark + tagline SVG.
- `docs/RECOMMENDED_TEMPLATE.md` — recommended design and screen template for new features.

## Known scope limits

- Legacy `.doc`, `.xls`, `.ppt`, and `.odt` formats are handed off to an external app; in-app Office parsing is for OOXML `.docx`, `.xlsx`, and `.pptx`.
- DOCX editing is paragraph-text based. It does not provide full word-processing semantics for tables, embedded images, comments, or tracked changes.
- RAR is extract-only; the included Java libraries do not create RAR archives.
- PDF stamping adds new text; it does not rewrite the original text objects already inside a PDF. Standard PDF fonts have limited character coverage.
- Video trimming uses stream copy, so cuts snap to available sync/keyframes and output depends on the source codecs accepted by MP4.
- SFTP first-time host verification is trust-on-first-use. Always compare the displayed fingerprint with a fingerprint obtained through a trusted independent channel; the first connection alone cannot prove server identity.
- The PIN is a local access gate, not file encryption or a substitute for Android device security.
- This project requests broad file access on Android 11+ to support full-device file management. Review Google Play's all-files-access policy before publishing; a public Play release may need a narrower Storage Access Framework implementation.

## Branding

Use `assets/aksi-file-manager-logo.svg` for horizontal marketing placements and `assets/aksi-file-manager-icon.svg` for square icon placements. The recommended design tokens and reusable Compose screen pattern are documented in `docs/RECOMMENDED_TEMPLATE.md`.
