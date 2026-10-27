package id.aksisoft.aksifile

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.view.View
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.*
import java.io.File
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

class Pal(val bg: Color, val card: Color, val card2: Color, val tx: Color, val sub: Color)
val DarkP = Pal(Color(0xFF0B0F17), Color(0xFF151B26), Color(0xFF222B3B), Color.White, Color(0xFF8B94A7))
val LightP = Pal(Color(0xFFF2F6FC), Color.White, Color(0xFFE3EAF6), Color(0xFF0F172A), Color(0xFF64748B))
val BLUE = Color(0xFF2F80FF)
val LP = compositionLocalOf { DarkP }
val K: Pal @Composable get() = LP.current

object Prefs {
    private const val KEY_ALIAS = "id.aksisoft.aksifile.credentials.v1"
    lateinit var sp: SharedPreferences
        private set
    lateinit var filesDir: File
        private set
    var lang by mutableStateOf("en"); var theme by mutableStateOf("system"); var ads by mutableStateOf(true)
    var hidden by mutableStateOf(false); var view by mutableIntStateOf(0); var path by mutableStateOf("")

    var pin: String
        get() = sp.getString("pin", "")!!
        set(v) { sp.edit().putString("pin", v).apply() }

    fun load(c: Context) {
        sp = c.getSharedPreferences("p", Context.MODE_PRIVATE)
        filesDir = c.filesDir
        lang = sp.getString("lang", "en")!!; theme = sp.getString("theme", "system")!!; ads = sp.getBoolean("ads", true)
        hidden = sp.getBoolean("hidden", false); view = sp.getInt("view", 0); path = sp.getString("path", "")!!
    }

    fun save() = sp.edit().putString("lang", lang).putString("theme", theme).putBoolean("ads", ads)
        .putBoolean("hidden", hidden).putInt("view", view).putString("path", path).apply()

    /** Store sensitive values using AES/GCM with a non-exportable Android Keystore key. */
    fun putSecureString(key: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        sp.edit().putString("${key}_enc", encoded).remove(key).apply()
    }

    /** Read encrypted values and migrate legacy plaintext entries at first access. */
    fun secureString(key: String, default: String): String {
        val encoded = sp.getString("${key}_enc", null)
        if (!encoded.isNullOrBlank()) return runCatching { decryptSecureString(encoded) }.getOrDefault(default)
        val legacy = sp.getString(key, null) ?: return default
        // Migration is best-effort so a device Keystore issue does not crash the whole app.
        runCatching { putSecureString(key, legacy) }
        return legacy
    }

    private fun decryptSecureString(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.DEFAULT)
        require(payload.size > 12) { "Invalid encrypted preference" }
        val iv = payload.copyOfRange(0, 12)
        val data = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    fun set(k: String): Set<String> = sp.getStringSet(k, emptySet())?.toSet() ?: emptySet()
    fun toggle(k: String, v: String): Boolean {
        val values = set(k).toMutableSet(); val added = values.add(v); if (!added) values.remove(v)
        sp.edit().putStringSet(k, values).apply(); return added
    }
}

private val IDS = """Home=Beranda
Files=File
Tools=Alat
More=Lainnya
Settings=Pengaturan
General=Umum
Language=Bahasa
Theme=Tema
System=Sistem
Light=Terang
Dark=Gelap
PIN Lock=Kunci PIN
Enabled=Aktif
Disabled=Nonaktif
Show test banner=Tampilkan banner uji
Default View=Tampilan Default
Default Path=Path Default
Show Hidden Files=Tampilkan File Tersembunyi
Clear Cache=Bersihkan Cache
Grid=Grid
List=Daftar
Card=Kartu
Images=Gambar
Videos=Video
Documents=Dokumen
APKs=APK
Audio=Audio
Compressed=Terkompresi
Favorites=Favorit
Recent=Terbaru
All=Semua
Downloads=Unduhan
Internal Storage=Penyimpanan Internal
Search files, folders...=Cari file, folder...
Open=Buka
Open with=Buka dengan
Share=Bagikan
Open in browser=Buka di browser
Favorite=Favorit
Copy=Salin
Cut=Potong
Paste=Tempel
Rename=Ganti nama
Compress=Kompres
Extract=Ekstrak
Analyze=Analisis
Size=Ukuran
Select=Pilih
Delete=Hapus
Cancel=Batal
Save=Simpan
Save As=Simpan Sebagai
Preview=Pratinjau
Crop=Potong
Filter=Filter
Adjust=Atur
Rotate=Putar
Enter PIN=Masukkan PIN
Create PIN=Buat PIN
Confirm PIN=Konfirmasi PIN
Wrong PIN=PIN salah
New folder=Folder baru
New text file=File teks baru
New Markdown=Markdown baru
New HTML=HTML baru
Largest Folders=Folder Terbesar
Storage Analyzer=Analisis Penyimpanan
Duplicates=Duplikat
Server List=Daftar Server
Add Server=Tambah Server
Download=Unduh
Upload=Unggah
Scan QR Code=Pindai Kode QR
Camera=Kamera
From Image=Dari Gambar
Align the QR code within the frame=Posisikan kode QR di dalam bingkai
Video Cutter=Pemotong Video
Allow file access to start=Izinkan akses file untuk memulai
Grant=Izinkan
Delete selected?=Hapus yang dipilih?
items=item
Search=Cari
Compress and extract=Kompres dan ekstrak
Use the item menu to compress or extract=Gunakan menu item untuk kompres atau ekstrak
PDF Merge=Gabung PDF
Remove Duplicate=Hapus Duplikat
File Search=Cari File
Office Files=File Office
Add to Gallery=Tambah ke Galeri
New gallery=Galeri baru
Delete gallery=Hapus galeri
Add folder=Tambah folder
Apps=Aplikasi
Cleaner=Pembersih
Extract all=Ekstrak semua
Extract selected=Ekstrak terpilih
Find=Cari
Replace=Ganti
Replace all=Ganti semua
Add text=Tambah teks
Split=Pisah
Text=Teks
Extract APK=Ekstrak APK
Share APK=Bagikan APK
App info=Info aplikasi
Uninstall=Copot
Download Folder=Folder Unduhan
Folder name=Nama folder
Edit text=Edit teks
Move=Pindah
Loading=Memuat
No files=Tidak ada file
Scanning=Memindai
Preview=Pratinjau
Edit=Edit
Back=Kembali""".lines().associate { it.substringBefore('=') to it.substringAfter('=') }

fun t(k: String) = if (Prefs.lang == "id") IDS[k] ?: k else k

@Composable
fun TopBar(title: String, back: (() -> Unit)? = null, right: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF1E5BFF), Color(0xFF2F80FF))))
        .statusBarsPadding().padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) Text("←", Modifier.clip(CircleShape).background(Color(0x26FFFFFF))
            .clickable { back() }.padding(horizontal = 12.dp, vertical = 2.dp), color = Color.White, fontSize = 20.sp)
        else Spacer(Modifier.width(14.dp))
        Text(title, Modifier.weight(1f).padding(horizontal = 8.dp), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        CompositionLocalProvider(LocalContentColor provides Color.White) { right() }
    }
}

/** Indeterminate or indeterminate-with-label loading bar, dipakai saat scan/list memuat. */
@Composable
fun ScanBar(text: String, progress: Float? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, fontSize = 12.sp, color = K.sub, maxLines = 2)
        if (progress == null) LinearProgressIndicator(Modifier.padding(top = 4.dp).fillMaxWidth())
        else LinearProgressIndicator({ progress }, Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(CircleShape))
    }
}

/** Pinch-zoom helper untuk pratinjau gambar/PDF: scale, pan, dan modifier siap pakai. */
@Composable
fun rememberZoom(): Pair<Pair<MutableState<Float>, MutableState<Offset>>, Modifier> {
    val sc = remember { mutableStateOf(1f) }
    val pan = remember { mutableStateOf(Offset.Zero) }
    val m = Modifier.clipToBounds().graphicsLayer { scaleX = sc.value; scaleY = sc.value; translationX = pan.value.x; translationY = pan.value.y }
        .pointerInput(Unit) {
            detectTransformGestures { _, p, z, _ ->
                sc.value = (sc.value * z).coerceIn(1f, 10f)
                pan.value = if (sc.value > 1f) Offset(pan.value.x + p.x, pan.value.y + p.y) else Offset.Zero
            }
        }
    return (sc to pan) to m
}

@Composable
fun Tile(icon: String, label: String, color: Color, mod: Modifier = Modifier, onClick: () -> Unit) {
    Column(mod.clip(RoundedCornerShape(18.dp)).background(K.card).clickable(onClick = onClick).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .18f)), Alignment.Center) { Text(icon, fontSize = 22.sp) }
        Spacer(Modifier.height(6.dp)); Text(t(label), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun SRow(label: String, value: String = "", sw: Boolean? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t(label), Modifier.weight(1f))
        if (value.isNotEmpty()) Text(t(value), fontSize = 13.sp, color = K.sub)
        if (sw != null) Switch(sw, null)
    }
}

@Composable fun Sec(s: String) = Text(t(s), Modifier.padding(20.dp, 16.dp, 20.dp, 4.dp), fontSize = 12.sp, color = K.sub, fontWeight = FontWeight.SemiBold)

@Composable
fun Ad() {
    if (Prefs.ads) AndroidView({ ctx ->
        runCatching {
            AdView(ctx).apply { setAdSize(AdSize.BANNER); adUnitId = "ca-app-pub-6396355089911498/8901326360"; loadAd(AdRequest.Builder().build()) }
        }.getOrElse { View(ctx) }
    }, Modifier.fillMaxWidth())
}

@Composable
fun Thumb(f: File, mod: Modifier, fs: TextUnit = 34.sp) {
    val b by produceState<Bitmap?>(null, f) { value = withContext(Dispatchers.IO) { Media.thumb(f) } }
    val bm = b
    if (bm != null) Image(bm.asImageBitmap(), null, mod, contentScale = ContentScale.Crop)
    else Box(mod, Alignment.Center) { Text(FS.icon(f), fontSize = fs) }
}

@Composable
fun Choice(title: String, opts: List<Pair<String, String>>, pick: (String) -> Unit, dismiss: () -> Unit) =
    AlertDialog(dismiss, confirmButton = {}, title = { Text(t(title)) },
        text = { Column { opts.forEach { (k, n) -> TextButton({ pick(k); dismiss() }) { Text(n) } } } })

@Composable
fun Splash() {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1E5BFF), Color(0xFF0B2A8F)))), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)))
            Text("Aksi File Manager", Modifier.padding(top = 16.dp), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("All in One File Explorer", color = Color(0xCCFFFFFF), fontSize = 13.sp)
            LinearProgressIndicator(Modifier.padding(top = 28.dp).width(160.dp).clip(CircleShape), color = Color.White, trackColor = Color(0x44FFFFFF))
            Text("Loading...", Modifier.padding(top = 6.dp), color = Color(0xCCFFFFFF), fontSize = 11.sp)
        }
    }
}

@Composable
fun PinFlow(mode: String, ok: () -> Unit) {
    val c = LocalContext.current
    var s by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    val title = if (mode == "create") (if (first.isEmpty()) "Create PIN" else "Confirm PIN") else "Enter PIN"
    LaunchedEffect(s) {
        if (s.length < 4) return@LaunchedEffect
        when {
            mode == "create" && first.isEmpty() -> { first = s; s = "" }
            mode == "create" -> if (s == first) { Prefs.pin = FS.h(s); ok() } else { Toast.makeText(c, t("Wrong PIN"), Toast.LENGTH_SHORT).show(); s = ""; first = "" }
            else -> if (FS.h(s) == Prefs.pin) ok() else { Toast.makeText(c, t("Wrong PIN"), Toast.LENGTH_SHORT).show(); s = "" }
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(BLUE), Alignment.Center) { Text("🔒", fontSize = 26.sp) }
        Text(t(title), Modifier.padding(16.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.padding(bottom = 28.dp), Arrangement.spacedBy(14.dp)) {
            repeat(4) { i -> Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < s.length) BLUE else K.card2)) }
        }
        listOf("123", "456", "789", " 0<").forEach { row ->
            Row(Modifier.padding(vertical = 9.dp), Arrangement.spacedBy(18.dp)) {
                row.forEach { ch ->
                    Box(Modifier.size(72.dp).clip(CircleShape).background(if (ch == ' ') Color.Transparent else K.card)
                        .clickable(enabled = ch != ' ') { s = if (ch == '<') s.dropLast(1) else if (s.length < 4) s + ch else s }, Alignment.Center) {
                        if (ch != ' ') Text(if (ch == '<') "⌫" else ch.toString(), fontSize = 24.sp)
                    }
                }
            }
        }
    }
}
