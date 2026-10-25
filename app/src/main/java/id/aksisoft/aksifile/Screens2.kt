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
import android.os.Environment

@Composable
fun Gallery(init: String) {
    val c = LocalContext.current
    var cat by remember { mutableStateOf(init) }
    var q by remember { mutableStateOf("") }
    var list by remember { mutableStateOf(listOf<File>()) }
    var gt by remember { mutableStateOf(0) }
    var dlg by remember { mutableStateOf("") }
    val custom = remember(gt) { Gal.all().keys.toList() }
    LaunchedEffect(cat, q, Nav.tick, gt) { list = withContext(Dispatchers.IO) { FS.scan(FS.roots(c)[0], cat, q) } }
    val title = CATS.firstOrNull { it.first == cat }?.second?.let { t(it) } ?: cat.removePrefix("g:")
    Column(Modifier.fillMaxSize()) {
        TopBar(title, { Nav.back() }) {
            if (cat.startsWith("g:")) {
                TextButton({ dlg = "addf" }) { Text("＋📁") }
                TextButton({ Gal.delete(cat.drop(2)); cat = "all"; gt++ }) { Text("🗑") }
            }
        }
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(12.dp, 4.dp), singleLine = true, shape = RoundedCornerShape(24.dp),
            placeholder = { Text("🔍 " + t("Search")) })
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CATS.forEach { (k, n) -> FilterChip(cat == k, { cat = k }, { Text(t(n)) }) }
            custom.forEach { n -> FilterChip(cat == "g:$n", { cat = "g:$n" }, { Text("🖼 $n") }) }
            FilterChip(false, { dlg = "newg" }, { Text("＋ " + t("New gallery")) })
        }
        if (cat == "img" || cat == "vid" || cat.startsWith("g:")) LazyVerticalGrid(GridCells.Adaptive(100.dp), Modifier.weight(1f).padding(top = 8.dp),
            contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(list) { f -> Thumb(f, Modifier.aspectRatio(1f).clickable { Nav.open(c, f) }) }
        } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
            items(list) { f ->
                Row(Modifier.fillMaxWidth().clickable { Nav.open(c, f) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Thumb(f, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)), 22.sp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(f.name, maxLines = 1)
                        Text(if (f.isFile) FS.fmt(f.length()) else f.path, fontSize = 12.sp, color = K.sub, maxLines = 1)
                    }
                }
            }
        }
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
