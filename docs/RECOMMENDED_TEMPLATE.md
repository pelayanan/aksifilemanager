# Recommended UI and project template

This is the recommended baseline for new Aksi File Manager screens and follow-on features. Keep the existing Kotlin + Jetpack Compose architecture; avoid adding a separate UI framework for an ordinary screen.

## Visual system

- **Product name:** Aksi File Manager
- **Tagline:** All in One File Explorer
- **Primary:** `#2F80FF`
- **Dark background / card:** `#0B0F17` / `#151B26`
- **Light background / card:** `#F2F6FC` / `#FFFFFF`
- **Shape:** 12–22 dp rounded cards; 24 dp search field; 52 dp central quick-action button.
- **Spacing:** 4 / 8 / 12 / 16 / 20 / 24 dp.
- **Typography:** Material 3 defaults; screen title 19–21 sp, body 14–16 sp, secondary metadata 11–13 sp.
- **Icons:** use clear pictograms and labels; keep actions accessible without relying on emoji alone.

## Screen structure

1. Add a route in `Nav` and its route handler in `Main()`.
2. Create the screen as a focused `@Composable` in a relevant module (`Screens.kt`, `Screens2.kt`, `MediaScreens.kt`, `Office.kt`, `Ftp.kt`, or a new file in the same package).
3. Use `TopBar(title, onBack)` and reuse `Tile`, `Sec`, `SRow`, `Choice`, `Thumb`, and the `K` palette rather than duplicating styling.
4. Keep slow file IO, archive work, hashing, PDF parsing, media processing, and network operations off the main thread with `Dispatchers.IO`.
5. Show a loading state, success feedback, and a human-readable error for every operation that can fail.
6. Ask before destructive operations, use unique output names through `FS.uniq`, and increment `Nav.tick` after filesystem mutations.
7. Add strings to the `IDS` translation map so English remains the default and Indonesian is available.
8. Keep permissions scoped to the task. Prefer Android's document picker/SAF when the feature does not need broad file access.

## Compose screen starter

```kotlin
@Composable
fun ExampleFeatureScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(K.bg)) {
        TopBar("Feature title", { Nav.back() })
        // Put the core workflow first; keep secondary actions in a menu or bottom row.
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        // Add the feature UI here.
    }
}
```

## Code quality checklist

- Empty input, invalid paths, missing permissions, zero-byte files, and cancellation are handled.
- Large work does not run on the UI thread.
- User-created output never overwrites an existing file without confirmation.
- File/archive operations guard against path traversal and unexpected input.
- Credentials are never logged. Server credentials use Android Keystore-backed AES/GCM storage.
- SFTP verifies a saved host key; the app displays the SHA-256 fingerprint before saving a first-time trust decision.
- Test on API 26, API 29, API 30+, and an SD-card/device without removable storage where available.
