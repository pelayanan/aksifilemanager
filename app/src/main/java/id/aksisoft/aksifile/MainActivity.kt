package id.aksisoft.aksifile

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.compose.ui.text.input.ImeAction
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.ads.MobileAds
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

object Nav {
    val stack = mutableStateListOf<Pair<String, Any?>>("home" to null)
    var cwd by mutableStateOf<File?>(null)
    var tick by mutableIntStateOf(0)
    var clip by mutableStateOf(listOf<File>()); var cut = false
    fun go(r: String, a: Any? = null) { stack.add(r to a) }
    fun tab(r: String, a: Any? = null) { stack.clear(); stack.add(r to a) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun browse(f: File) { cwd = f; if (stack.last().first == "files") tick++ else tab("files") }

    fun open(c: Context, f: File) {
        if (f.isDirectory) { cwd = f; go("files"); return }
        FS.recent(f)
        when (f.extension.lowercase()) {
            "apk" -> if (Prefs.pin.isEmpty()) FS.open(c, f) else go("pin_apk", f)
            "docx" -> go("docx", f)
            "xlsx" -> go("xlsx", f)
            "pptx" -> go("pptx", f)
            in FS.ZIP -> go("archive", f)
            "csv" -> go("csv", f)
            "pdf" -> go("pdf", f)
            in FS.IMG -> go("image", f)
            in FS.VID -> go("video", f)
            in FS.TXT -> go("editor", f)
            else -> FS.open(c, f)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        installSplashScreen(); super.onCreate(b)
        Prefs.load(this); runCatching { MobileAds.initialize(this) }; PDFBoxResourceLoader.init(applicationContext)
        setContent { App() }
    }
}

@Composable
fun App() {
    val c = LocalContext.current
    var splash by remember { mutableStateOf(true) }
    var unlocked by remember { mutableStateOf(Prefs.pin.isEmpty()) }
    var granted by remember { mutableStateOf(FS.hasAccess(c)) }
    LaunchedEffect(Unit) { delay(900); splash = false }
    LaunchedEffect(granted) { while (!granted) { delay(600); granted = FS.hasAccess(c) } }
    val dark = when (Prefs.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val pal = if (dark) DarkP else LightP
    CompositionLocalProvider(LP provides pal) {
        val scheme = if (dark) darkColorScheme(primary = BLUE, surface = pal.card, background = pal.bg)
        else lightColorScheme(primary = BLUE, surface = pal.card, background = pal.bg)
        MaterialTheme(scheme) {
            Surface(Modifier.fillMaxSize(), color = pal.bg, contentColor = pal.tx) {
                when {
                    splash -> Splash()
                    !unlocked -> PinFlow("unlock") { unlocked = true }
                    !granted -> Grant()
                    else -> Main()
                }
            }
        }
    }
}

@Composable
fun Grant() {
    val c = LocalContext.current
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("📁", fontSize = 56.sp)
        Text(t("Allow file access to start"), Modifier.padding(16.dp))
        Button({
            if (Build.VERSION.SDK_INT >= 30) c.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + c.packageName)))
            else (c as Activity).requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }) { Text(t("Grant")) }
    }
}

@Composable
fun Main() {
    val c = LocalContext.current
    val (r, a) = Nav.stack.last()
    BackHandler(Nav.stack.size > 1) { Nav.back() }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            key(Nav.stack.size, r, a) { when (r) {
                "home" -> Home(); "files" -> Browser(); "tools" -> Tools(); "more" -> More()
                "gallery" -> Gallery(a as String); "editor" -> Editor(a as File); "image" -> ImageScreen(a as File)
                "video" -> VideoScreen(a as File); "pdf" -> PdfScreen(a as File); "csv" -> CsvScreen(a as File)
                "qr" -> QrScreen(); "ftp" -> FtpScreen(); "remote" -> RemoteScreen(a as Srv)
                "analyzer" -> Analyzer(a as File); "dups" -> Dups(); "settings" -> SettingsScreen()
                "pin_set" -> PinFlow("create") { Nav.back() }
                "pin_off" -> PinFlow("verify") { Prefs.pin = ""; Nav.back() }
                "pin_apk" -> PinFlow("verify") { Nav.back(); FS.open(c, a as File) }
                "archive" -> ArchiveScreen(a as File); "docx" -> DocxScreen(a as File); "xlsx" -> XlsxScreen(a as File)
                "pptx" -> PptxScreen(a as File); "apps" -> AppsScreen(); "cleaner" -> Cleaner()
            } }
        }
        if (r in listOf("home", "files", "tools", "more")) { Ad(); BottomBar(r) }
    }
}

@Composable
fun RowScope.NavItem(icon: String, label: String, route: String, cur: String) {
    val on = cur == route
    Column(Modifier.weight(1f).clickable { Nav.tab(route) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 20.sp, color = if (on) BLUE else K.sub)
        Text(t(label), fontSize = 10.sp, color = if (on) BLUE else K.sub)
    }
}

@Composable
fun BottomBar(cur: String) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var mk by remember { mutableStateOf<Pair<String, String>?>(null) }
    val dir = Nav.cwd ?: File(Prefs.path.ifEmpty { FS.roots(c)[0].path })
    Row(Modifier.fillMaxWidth().background(K.card).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        NavItem("🏠", "Home", "home", cur); NavItem("📁", "Files", "files", cur)
        Box(Modifier.weight(1f), Alignment.Center) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(BLUE).clickable { menu = true }, Alignment.Center) { Text("＋", fontSize = 26.sp, color = Color.White) }
            DropdownMenu(menu, { menu = false }) {
                listOf("New folder" to "folder", "New text file" to "note.txt", "New Markdown" to "note.md", "New HTML" to "index.html").forEach { (n, d) ->
                    DropdownMenuItem({ Text(t(n)) }, { menu = false; mk = d to (if (d == "folder") "New folder" else d) })
                }
                if (Nav.clip.isNotEmpty()) DropdownMenuItem({ Text(t("Paste") + " (${Nav.clip.size})") }, {
                    menu = false
                    scope.launch { withContext(Dispatchers.IO) { runCatching { FS.paste(Nav.clip, Nav.cut, dir) } }; if (Nav.cut) Nav.clip = emptyList(); Nav.tick++ }
                })
            }
        }
        NavItem("🧰", "Tools", "tools", cur); NavItem("⋯", "More", "more", cur)
    }
    mk?.let { (kind, name0) ->
        var name by remember { mutableStateOf(name0) }
        AlertDialog({ mk = null },
            confirmButton = { TextButton({
                val f = File(dir, name); mk = null
                if (kind == "folder") f.mkdirs() else { runCatching { f.createNewFile() }; Nav.go("editor", f) }
                Nav.tick++
            }) { Text("OK") } },
            dismissButton = { TextButton({ mk = null }) { Text(t("Cancel")) } },
            title = { Text(dir.path, fontSize = 12.sp) }, text = { OutlinedTextField(name, { name = it }, singleLine = true) })
    }
}

class HomeTile(val i: String, val l: String, val c: Color, val a: () -> Unit)

@Composable
fun Home() {
    val c = LocalContext.current
    val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val tiles = listOf(
        HomeTile("🖼", "Images", BLUE) { Nav.go("gallery", "img") },
        HomeTile("🎬", "Videos", Color(0xFFEF4444)) { Nav.go("gallery", "vid") },
        HomeTile("📄", "Documents", BLUE) { Nav.go("gallery", "doc") },
        HomeTile("🤖", "APKs", Color(0xFF22C55E)) { Nav.go("gallery", "apk") },
        HomeTile("🎵", "Audio", Color(0xFFA855F7)) { Nav.go("gallery", "aud") },
        HomeTile("📦", "Apps", BLUE) { Nav.go("apps") },
        HomeTile("🗜", "Compressed", Color(0xFFF97316)) { Nav.go("gallery", "zip") },
        HomeTile("⭐", "Favorites", Color(0xFFF59E0B)) { Nav.go("gallery", "fav") },
        HomeTile("🕘", "Recent", BLUE) { Nav.go("gallery", "rec") })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("Aksi File Manager") {
            TextButton({ Nav.go("gallery", "all") }) { Text("🔍") }
            TextButton({ Nav.tab("more") }) { Text("⋮", color = K.tx) }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp, 4.dp).clip(RoundedCornerShape(24.dp)).background(K.card).clickable { Nav.go("gallery", "all") }.padding(16.dp)) {
            Text(t("Search files, folders..."), color = K.sub, modifier = Modifier.weight(1f)); Text("🔍")
        }
        tiles.chunked(3).forEach { row ->
            Row(Modifier.padding(horizontal = 8.dp)) { row.forEach { Tile(it.i, it.l, it.c, Modifier.weight(1f).padding(4.dp), it.a) } }
        }
        FS.roots(c).forEachIndexed { i, r ->
            val used = r.totalSpace - r.freeSpace
            Column(Modifier.fillMaxWidth().padding(12.dp, 4.dp).clip(RoundedCornerShape(18.dp)).background(K.card).clickable { Nav.go("analyzer", r) }.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (i == 0) "📱" else "💾", fontSize = 22.sp)
                    Text(if (i == 0) t("Internal Storage") else "SD Card $i", Modifier.weight(1f).padding(start = 10.dp))
                    Text("${FS.fmt(used)} / ${FS.fmt(r.totalSpace)}", fontSize = 12.sp, color = K.sub)
                }
                LinearProgressIndicator({ used.toFloat() / r.totalSpace.coerceAtLeast(1) }, Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp).clip(CircleShape))
            }
        }
    }
}

@Composable
fun Entry(f: File, mode: Int, on: Boolean, tap: () -> Unit, act: (String) -> Unit) {
    var m by remember { mutableStateOf(false) }
    val bg = if (on) BLUE.copy(alpha = .25f) else K.card
    val acts = listOf("Open", "Open with", "Share", "Open in browser", "Favorite", "Add to Gallery", "Copy", "Cut", "Rename", "Compress", "Extract", "Analyze", "Size", "Select", "Delete")
        .filter { (it != "Extract" || FS.isArc(f)) && (f.isFile || it !in setOf("Share", "Open in browser", "Open with")) }
    val menu: @Composable () -> Unit = {
        Box {
            Text(if (on) "✓" else "⋮", Modifier.clickable { m = true }.padding(8.dp))
            DropdownMenu(m, { m = false }) { acts.forEach { a -> DropdownMenuItem({ Text(t(a)) }, { m = false; act(a) }) } }
        }
    }
    if (mode == 0) Column(Modifier.clip(RoundedCornerShape(18.dp)).background(bg).clickable(onClick = tap).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Thumb(f, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
        Text(f.name, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center); menu()
    } else {
        val card = mode == 2
        Row(Modifier.fillMaxWidth().padding(vertical = if (card) 5.dp else 1.dp).clip(RoundedCornerShape(if (card) 22.dp else 10.dp))
            .background(bg).clickable(onClick = tap).padding(if (card) 14.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(f, Modifier.size(if (card) 52.dp else 38.dp).clip(RoundedCornerShape(12.dp)), 22.sp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(f.name, maxLines = 1)
                Text(if (f.isFile) FS.fmt(f.length()) else "${f.list()?.size ?: 0} ${t("items")}", fontSize = 12.sp, color = K.sub)
            }
            menu()
        }
    }
}

@Composable
fun Browser() {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val roots = remember { FS.roots(c) }
    var dir by remember { mutableStateOf(Nav.cwd?.takeIf { it.isDirectory } ?: File(Prefs.path.ifEmpty { roots[0].path }).takeIf { it.isDirectory } ?: roots[0]) }
    var pathTxt by remember { mutableStateOf(dir.path) }
    var sel by remember { mutableStateOf(setOf<File>()) }
    var files by remember { mutableStateOf(listOf<File>()) }
    var info by remember { mutableStateOf<String?>(null) }
    var ren by remember { mutableStateOf<File?>(null) }
    var del by remember { mutableStateOf<List<File>?>(null) }
    var cmp by remember { mutableStateOf<List<File>?>(null) }
    var gal by remember { mutableStateOf<File?>(null) }
    var newG by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    val root = roots.firstOrNull { dir.path.startsWith(it.path) } ?: roots[0]
    SideEffect { Nav.cwd = dir }
    fun nav(f: File) { dir = f; pathTxt = f.path; sel = setOf() }
    LaunchedEffect(Nav.cwd) { Nav.cwd?.let { if (it != dir && it.isDirectory) nav(it) } }
    LaunchedEffect(dir, Nav.tick, Prefs.hidden) { files = withContext(Dispatchers.IO) { FS.list(dir) } }
    BackHandler(sel.isNotEmpty() || dir != root) { if (sel.isNotEmpty()) sel = setOf() else dir.parentFile?.let { nav(it) } }

    fun tap(f: File) {
        if (sel.isNotEmpty()) { sel = if (f in sel) sel - f else sel + f }
        else if (f.isDirectory) nav(f)
        else Nav.open(c, f)
    }
    fun fail(r: Result<Unit>) { r.onFailure { Toast.makeText(c, it.message ?: "Error", Toast.LENGTH_LONG).show() } }
    fun act(f: File, a: String) {
        val l = if (f in sel) sel.toList() else listOf(f)
        when (a) {
            "Open" -> if (f.isDirectory) nav(f) else Nav.open(c, f)
            "Open with" -> FS.chooser(c, f)
            "Share" -> FS.share(c, f)
            "Open in browser" -> FS.open(c, f, "text/html")
            "Favorite" -> Toast.makeText(c, if (FS.fav(f)) "⭐" else "☆", Toast.LENGTH_SHORT).show()
            "Add to Gallery" -> gal = f
            "Copy" -> { Nav.clip = l; Nav.cut = false; sel = setOf() }
            "Cut" -> { Nav.clip = l; Nav.cut = true; sel = setOf() }
            "Rename" -> ren = f
            "Compress" -> cmp = l
            "Extract" -> scope.launch {
                busy = true
                val r = withContext(Dispatchers.IO) { runCatching { Arc.extract(f, FS.uniq(File(dir, Arc.baseName(f)))) } }
                busy = false; Nav.tick++; fail(r)
            }
            "Analyze" -> Nav.go("analyzer", if (f.isDirectory) f else dir)
            "Size" -> scope.launch { info = FS.fmt(withContext(Dispatchers.IO) { FS.size(f) }) }
            "Select" -> sel = sel + f
            "Delete" -> del = l
        }
    }

    Column(Modifier.fillMaxSize()) {
        val back: (() -> Unit)? = if (dir != root) ({ nav(dir.parentFile ?: dir) }) else null
        TopBar(dir.name.ifEmpty { "/" }, back) { TextButton({ Nav.go("gallery", "all") }) { Text("🔍") } }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                roots.forEachIndexed { i, r ->
                    Text("💽 " + (if (i == 0) "Internal" else "SD $i"), Modifier.clip(RoundedCornerShape(14.dp)).background(if (r == root) BLUE.copy(alpha = .25f) else K.card)
                        .clickable { nav(r) }.padding(10.dp, 6.dp), fontSize = 12.sp)
                }
            }
            listOf("▦", "☰", "▭").forEachIndexed { i, s ->
                Text(s, Modifier.clip(RoundedCornerShape(10.dp)).background(if (Prefs.view == i) BLUE.copy(alpha = .25f) else Color.Transparent)
                    .clickable { Prefs.view = i; Prefs.save() }.padding(10.dp))
            }
        }
        OutlinedTextField(pathTxt, { pathTxt = it }, Modifier.fillMaxWidth().padding(12.dp, 6.dp), singleLine = true, shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { File(pathTxt).takeIf { it.isDirectory }?.let { nav(it) } }))
        if (sel.isNotEmpty()) Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("✓ ${sel.size}", Modifier.padding(end = 8.dp))
            listOf("Copy", "Cut", "Compress", "Delete").forEach { a -> TextButton({ act(sel.first(), a) }) { Text(t(a), fontSize = 12.sp) } }
            TextButton({ sel = setOf() }) { Text("✕") }
        }
        if (Prefs.view == 0) LazyVerticalGrid(GridCells.Adaptive(100.dp), Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files) { f -> Entry(f, 0, f in sel, { tap(f) }) { act(f, it) } }
        } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
            items(files) { f -> Entry(f, Prefs.view, f in sel, { tap(f) }) { act(f, it) } }
        }
    }

    info?.let { AlertDialog({ info = null }, confirmButton = { TextButton({ info = null }) { Text("OK") } }, text = { Text(t("Size") + ": " + it) }) }
    ren?.let { f ->
        var n by remember { mutableStateOf(f.name) }
        AlertDialog({ ren = null }, confirmButton = { TextButton({ f.renameTo(File(f.parentFile, n)); ren = null; Nav.tick++ }) { Text("OK") } },
            dismissButton = { TextButton({ ren = null }) { Text(t("Cancel")) } }, text = { OutlinedTextField(n, { n = it }, singleLine = true) })
    }
    del?.let { l ->
        AlertDialog({ del = null },
            confirmButton = { TextButton({ l.forEach { it.deleteRecursively() }; sel = setOf(); del = null; Nav.tick++ }) { Text(t("Delete")) } },
            dismissButton = { TextButton({ del = null }) { Text(t("Cancel")) } }, text = { Text(t("Delete selected?") + " (${l.size})") })
    }
    cmp?.let { l ->
        Choice("Compress", listOf("zip" to "ZIP", "7z" to "7Z", "tgz" to "TAR.GZ"), { fmt ->
            scope.launch {
                busy = true
                val nm = (if (l.size == 1) l[0].nameWithoutExtension else "archive") + (if (fmt == "tgz") ".tar.gz" else ".$fmt")
                val r = withContext(Dispatchers.IO) { runCatching { Arc.compress(l, FS.uniq(File(dir, nm)), fmt) } }
                busy = false; sel = setOf(); Nav.tick++; fail(r)
            }
        }) { cmp = null }
    }
    gal?.let { f ->
        Choice("Add to Gallery", Gal.all().keys.map { it to it } + ("__new" to "＋ " + t("New gallery")), { k ->
            if (k == "__new") newG = f else { Gal.add(k, f.path); Toast.makeText(c, "✓ $k", Toast.LENGTH_SHORT).show() }
        }) { gal = null }
    }
    newG?.let { f ->
        var n by remember { mutableStateOf("") }
        AlertDialog({ newG = null },
            confirmButton = { TextButton({ if (n.isNotBlank()) { Gal.create(n.trim()); Gal.add(n.trim(), f.path) }; newG = null }) { Text("OK") } },
            dismissButton = { TextButton({ newG = null }) { Text(t("Cancel")) } },
            title = { Text(t("New gallery")) }, text = { OutlinedTextField(n, { n = it }, singleLine = true) })
    }
}
