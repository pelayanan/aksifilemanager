# EXTRA README — Build v1.1 (versionCode 2)

## Catatan build otomatis (sekali proses di session ini)
- Environment: Linux container, JDK 21 (OpenJVM Ubuntu), Gradle 8.11.1 manual install
  (tanpa wrapper JAR — project asli tidak menyertakan wrapper), AGP **8.10.1**,
  Kotlin 1.9.24 + Compose Compiler 1.5.14.
- Android SDK: platform-tools, **platforms;android-36**, build-tools 36.
  `local.properties` diarahkan ke `/opt/android-sdk`.
- Target/compileSdk dinaikkan 34 → **36** sesuai kebijakan Google Play per 31 Agustus 2026
  (unggahan dengan target di bawah 36 akan ditolak).
- Signed dengan **tukangdigital.keystore** (alias `tukangdigital`, password di
  `keystore.properties`). SHA-256 cert:
  `CB:0B:96:0F:C9:E0:FC:77:62:A3:53:76:71:F7:04:13:90:59:15:D0:19:D2:75:C2:6D:A1:F2:91:4C:5A:37:BC`
- Release: **minifyEnabled (R8)** + **shrinkResources** aktif; proguard rules menjaga
  jsch/pdfbox/commons-compress/junrar agar reflection tidak rusak.

## Store assets (dibuat session ini)
- `store/banner_1024x500.png` — banner Play Store (logo + tagline + feature chips + mockup).
- `tool_make_banner.py` — generator banner (PIL, HTML-free, reproducible).

## Batasan yang diwarisi dari project (FAQ)
- RAR extract-only; DOCX/XLSX/PPTX hanya OOXML (legacy .doc/.xls/.ppt via app luar).
- Video trim stream-copy (snap ke keyframe); PDF stamp additive.
- SFTP strict host-key (TOFU + verifikasi fingerprint manual); PIN = gerbang lokal, bukan enkripsi.
- `MANAGE_EXTERNAL_STORAGE` perlu direview terhadap kebijakan Play all-files-access.

## Checkpoint yang lolos (diisi setelah build)
- [x] Source sync & dependencies resolve
- [x] assembleDebug sukses (42.6 MB, app-debug.apk)
- [x] assembleRelease (R8 minify) sukses & signed (29 MB)
- [x] bundleRelease (AAB) sukses & signed (24 MB)
