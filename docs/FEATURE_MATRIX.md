# Feature implementation matrix

This table maps the visible feature groups to the source modules and documents their current boundaries. It is intended as a regression checklist for future builds.

| Feature group | Source | Implemented flow | Scope boundary / QA note |
|---|---|---|---|
| Home, tabs, themes, language | `MainActivity.kt`, `Ui.kt` | Home tiles, bottom tabs, splash, light/dark/system, English/Indonesian strings | Verify UI on compact phones and with large system font. |
| Local file explorer | `MainActivity.kt`, `FS.kt` | Navigate roots/path, list styles, hidden files, common actions, clipboard, create/rename/delete | Broad external-storage access is required for full-device traversal. |
| Search and galleries | `Screens2.kt`, `Extras.kt`, `FS.kt` | Type/category search, favorites, recent, custom galleries | Search is bounded to the first 1,000 matching entries; long scans should be tested on large storage. |
| Archive tools | `Arc.kt`, `FS.kt` | List/extract ZIP, 7Z, RAR, TAR, TAR.GZ, GZ; create ZIP, 7Z, TAR.GZ | RAR creation is not supported. Test malformed archives and path traversal inputs. |
| Text/code editor | `Editor.kt` | Highlighting, line numbers, find/replace, save/save-as, HTML/Markdown preview | Preview is a WebView; do not treat untrusted HTML preview as a secure sandbox. |
| Image editor | `ImageEditor.kt`, `Media.kt` | Crop/ratio, transforms, filters, tone/contrast/saturation, sharpen, image export | Very large images can require significant memory; test low-memory devices. |
| Video cutter | `MediaScreens.kt`, `Media.kt` | Select source, trim range, write MP4 | Uses stream copy; trims may snap to sync frames and preserve only source tracks accepted by MP4. |
| PDF tools | `PdfTools.kt`, `Media.kt` | View, extract text, merge, split, add text stamp | Stamping is additive, not direct editing of existing text. Validate corrupted/encrypted PDFs. |
| Office formats | `Office.kt` | DOCX paragraph text, XLSX sheet table, PPTX slide text, CSV table | Not a replacement for full Word/Excel/PowerPoint rendering. Legacy formats use external apps. |
| QR scanning | `MediaScreens.kt`, `Media.kt` | Live camera scanning and scan from image | Requires camera permission for live mode. Handle no-code/invalid-image cases. |
| FTP / FTPS / SFTP | `Ftp.kt`, `Net.kt` | Saved profiles, remote listing, upload/download, recursive download, mkdir/rename/delete | Verify FTP server TLS policy; SFTP must have a trusted host key. Test interruption and partial transfers. |
| SFTP trust and secrets | `Net.kt`, `Ui.kt` | Fingerprint review, explicit trust, strict host checks, forget-key flow, AES/GCM encrypted profile store | Compare fingerprints through an independent trusted channel; this is not automatic PKI validation. |
| Analyzer and cleanup | `Screens.kt`, `Extras.kt`, `FS.kt` | Category-size chart, largest folders, duplicate scan, junk/empty/large file selection | Destructive cleanup is user-confirmed; exercise with test data before production use. |
| Installed apps and PIN | `Extras.kt`, `Ui.kt`, `MainActivity.kt` | List launchable apps, export/share APK, app info/uninstall, PIN gate | PIN gate is not file encryption; Play Store policy may restrict APK and broad file-access behavior. |
| Branding / project guidance | `assets/`, `docs/` | App icon SVG, horizontal logo SVG, screen/design template, feature matrix | SVG assets are source assets; Android launcher uses the vector drawable resource. |

## Minimum manual QA before release

- Build debug and release from a clean Android Studio checkout.
- Install on API 26 and API 34+ devices; validate permission flow after granting/revoking all-files access.
- Exercise file operations using a disposable test directory, including duplicate names and archive entries with `../` paths.
- Test encryption migration with a pre-existing `srv` preference, restart the app, and verify that the plaintext `srv` key is removed.
- Test SFTP unknown-host rejection, first-time fingerprint review, successful trust, rejection of a changed host key, and the explicit forget-and-retrust workflow.
- Test interrupted network transfers, full storage, unsupported MIME types, malformed archives, large images, and corrupt PDFs.
- Replace Google's test AdMob IDs/configuration with valid release configuration only after checking current ad policy and consent requirements.
