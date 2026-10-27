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
import androidx.compose.ui.graphics.Brush
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
import android.os.Environment

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Gallery(init: String) {
    val c = LocalContext.current
    var cat by remember { mutableStateOf(init) }
    var q by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<File>?>(null) }
    var sel by remember { mutableStateOf(setOf<File>()) }
    var del by remember { mutableStateOf<List<File>?>(null) }
    var gt by remember { mutableStateOf(0) }
    var dlg by remember { mutableStateOf("") }
    var lastTick by remember { mutableIntStateOf(-1) }
    var lastQ by remember { mutableStateOf("") }
    val custom = remember(gt) { Gal.all().keys.toList() }
    LaunchedEffect(cat, q, Nav.tick, gt) {
        if (q != lastQ) delay(350) // debounce ketik biar scan tidak jalan di setiap huruf
        lastQ = q
        val force = lastTick != Nav.tick; lastTick = Nav.tick
        list = null
        list = withContext(Dispatchers.IO) { FS.scan(FS.roots(c)[0], cat, q, force) }
    }
    val title = CATS.firstOrNull { it.first == cat }?.second?.let { t(it) } ?: cat.removePrefix("g:")
    Column(Modifier.fillMaxSize()) {
        // Search inline di top bar (hemat satu baris layar); judul jadi placeholder.
        Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF1E5BFF), Color(0xFF2F80FF))))
            .statusBarsPadding().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("←", Modifier.clip(CircleShape).background(Color(0x26FFFFFF)).clickable { Nav.back() }
                .padding(horizontal = 12.dp, vertical = 2.dp), color = Color.White, fontSize = 20.sp)
            OutlinedTextField(q, { q = it }, Modifier.weight(1f).padding(start = 8.dp), singleLine = true,
                shape = RoundedCornerShape(22.dp), textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                placeholder = { Text("🔍 $title", color = Color.White.copy(alpha = .75f), maxLines = 1) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0x26FFFFFF), unfocusedContainerColor = Color(0x1AFFFFFF),
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                    cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
            if (cat.startsWith("g:")) {
                TextButton({ dlg = "addf" }) { Text("＋📁") }
                TextButton({ Gal.delete(cat.drop(2)); cat = "all"; gt++ }) { Text("🗑") }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CATS.forEach { (k, n) -> FilterChip(cat == k, { cat = k }, { Text(t(n)) }) }
            custom.forEach { n -> FilterChip(cat == "g:$n", { cat = "g:$n" }, { Text("🖼 $n") }) }
            FilterChip(false, { dlg = "newg" }, { Text("＋ " + t("New gallery")) })
        }
        if (sel.isNotEmpty()) Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("✓ ${sel.size}", Modifier.padding(end = 6.dp))
            listOf("Share", "Copy", "Move", "Delete").forEach { a -> TextButton({
                when (a) {
                    "Share" -> { sel.firstOrNull()?.let { FS.share(c, it) } }
                    "Copy" -> { Nav.clip = sel.toList(); Nav.cut = false }
                    "Move" -> { Nav.clip = sel.toList(); Nav.cut = true }
                    "Delete" -> del = sel.toList()
                }
                if (a != "Delete") sel = setOf()
            }) { Text(t(a), fontSize = 12.sp) } }
            TextButton({ sel = setOf() }) { Text("✕") }
        }
        val l = list
        if (l == null) ScanBar(t("Loading") + "…")
        else if (l.isEmpty()) Text(t("No files"), Modifier.padding(20.dp), color = K.sub)
        else if (cat == "img" || cat == "vid" || cat.startsWith("g:")) LazyVerticalGrid(GridCells.Adaptive(100.dp), Modifier.weight(1f).padding(top = 8.dp),
            contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(l) { f ->
                Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(if (f in sel) BLUE else Color.Transparent)
                    .padding(2.dp)) {
                    Thumb(f, Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).combinedClickable(
                        onClick = { if (sel.isNotEmpty()) sel = if (f in sel) sel - f else sel + f else Nav.open(c, f) },
                        onLongClick = { sel = sel + f }))
                }
            }
        } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
            items(l) { f ->
                Row(Modifier.fillMaxWidth().background(if (f in sel) BLUE.copy(alpha = .25f) else Color.Transparent, RoundedCornerShape(10.dp))
                    .combinedClickable(onClick = { if (sel.isNotEmpty()) sel = if (f in sel) sel - f else sel + f else Nav.open(c, f) }, onLongClick = { sel = sel + f })
                    .padding(vertical = 6.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Thumb(f, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)), 22.sp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(f.name, maxLines = 1)
                        Text(if (f.isFile) FS.fmt(f.length()) else f.path, fontSize = 12.sp, color = K.sub, maxLines = 1)
                    }
                }
            }
        }
    }
    del?.let { dl ->
        AlertDialog({ del = null },
            confirmButton = { TextButton({ dl.forEach { it.deleteRecursively() }; del = null; sel = setOf(); Nav.tick++ }) { Text(t("Delete")) } },
            dismissButton = { TextButton({ del = null }) { Text(t("Cancel")) } }, text = { Text(t("Delete selected?") + " (${dl.size})") })
    }
    when (dlg) {
        "newg" -> {
            var n by remember { mutableStateOf("") }
            AlertDialog({ dlg = "" }, confirmButton = { TextButton({ if (n.isNotBlank()) { Gal.create(n.trim()); cat = "g:" + n.trim(); gt++ }; dlg = "" }) { Text("OK") } },
                dismissButton = { TextButton({ dlg = "" }) { Text(t("Cancel")) } }, title = { Text(t("New gallery")) }, text = { OutlinedTextField(n, { n = it }, singleLine = true) })
        }
        "addf" -> {
            var p by remember { mutableStateOf(Nav.cwd?.path ?: "") }
            AlertDialog({ dlg = "" }, confirmButton = { TextButton({ if (File(p).exists()) { Gal.add(cat.drop(2), p); gt++ }; dlg = "" }) { Text("OK") } },
                dismissButton = { TextButton({ dlg = "" }) { Text(t("Cancel")) } }, title = { Text(t("Add folder")) }, text = { OutlinedTextField(p, { p = it }, singleLine = true) })
        }
    }
}

@Composable
fun More() = Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    TopBar(t("More"))
    SRow("Favorites") { Nav.go("gallery", "fav") }
    SRow("Recent") { Nav.go("gallery", "rec") }
    SRow("Download Folder") { Nav.browse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)) }
    SRow("Apps") { Nav.go("apps") }
    SRow("Cleaner") { Nav.go("cleaner") }
    SRow("FTP / SFTP") { Nav.go("ftp") }
    SRow("Scan QR Code") { Nav.go("qr") }
    SRow("Storage Analyzer") { Nav.go("analyzer", Environment.getExternalStorageDirectory()) }
    SRow("Duplicates") { Nav.go("dups") }
    SRow("Settings") { Nav.go("settings") }
    SRow("About", "Aksi File Manager 1.0") {}
}

@Composable
fun Tools() {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    fun newFile(n: String) {
        val d = Nav.cwd ?: Environment.getExternalStorageDirectory()
        val f = FS.uniq(File(d, n)); runCatching { f.createNewFile() }; Nav.go("editor", f)
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { us ->
        if (us.size > 1) scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val fs = us.mapIndexed { i, u -> File(c.cacheDir, "m$i.pdf").also { f -> c.contentResolver.openInputStream(u)!!.use { s -> f.outputStream().use { o -> s.copyTo(o) } } } }
                    val o = FS.uniq(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "merged.pdf"))
                    runCatching { Pdf.merge(fs, o) }.recoverCatching { Media.pdfMerge(fs, o) }.getOrThrow(); o
                }
            }
            Toast.makeText(c, r.map { "✓ " + it.name }.getOrElse { it.message ?: "Error" }, Toast.LENGTH_LONG).show()
        }
    }
    val tiles = listOf(
        ToolItem("🔎", "File Search", BLUE) { Nav.go("gallery", "all") },
        ToolItem("⧉", "Remove Duplicate", Color(0xFFEF4444)) { Nav.go("dups") },
        ToolItem("📊", "Storage Analyzer", Color(0xFF22C55E)) { Nav.go("analyzer", Environment.getExternalStorageDirectory()) },
        ToolItem("🧹", "Cleaner", Color(0xFFF59E0B)) { Nav.go("cleaner") },
        ToolItem("📦", "Apps", BLUE) { Nav.go("apps") },
        ToolItem("🗜", "Compress and extract", Color(0xFFF97316)) { Nav.tab("files"); Toast.makeText(c, t("Use the item menu to compress or extract"), Toast.LENGTH_LONG).show() },
        ToolItem("📡", "FTP / SFTP", BLUE) { Nav.go("ftp") },
        ToolItem("▦", "Scan QR Code", Color(0xFFA855F7)) { Nav.go("qr") },
        ToolItem("📕", "PDF Merge", Color(0xFFEF4444)) { pick.launch("application/pdf") },
        ToolItem("📑", "Office Files", BLUE) { Nav.go("gallery", "doc") },
        ToolItem("📝", "Markdown", Color(0xFF22C55E)) { newFile("note.md") },
        ToolItem("🌐", "HTML", Color(0xFFA855F7)) { newFile("index.html") },
        ToolItem("📥", "Download Folder", BLUE) { Nav.browse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)) },
        ToolItem("🖼", "Images", BLUE) { Nav.go("gallery", "img") },
        ToolItem("⚙", "Settings", Color(0xFF8B94A7)) { Nav.go("settings") })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(t("Tools"))
        tiles.chunked(3).forEach { row ->
            Row(Modifier.padding(horizontal = 8.dp)) {
                row.forEach { Tile(it.i, it.l, it.c, Modifier.weight(1f).padding(4.dp), it.a) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
