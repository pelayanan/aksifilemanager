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
import android.graphics.Bitmap
import android.os.Environment

val CATS = listOf("all" to "All", "img" to "Images", "vid" to "Videos", "doc" to "Documents", "apk" to "APKs",
    "aud" to "Audio", "zip" to "Compressed", "fav" to "Favorites", "rec" to "Recent")

class ToolItem(val i: String, val l: String, val c: Color, val a: () -> Unit)

@Composable
fun Analyzer(dir: File) {
    var res by remember { mutableStateOf<Pair<LongArray, List<Pair<File, Long>>>?>(null) }
    var pr by remember { mutableStateOf<Pair<Long, Pair<Long, String>>?>(null) }
    LaunchedEffect(dir) {
        res = withContext(Dispatchers.IO) { FS.analyze(dir) { n, b, p -> pr = (n to (b to p)) } }
        pr = null
    }
    val names = listOf("Others", "Images", "Videos", "Documents", "Apps", "Audio", "Compressed")
    val cols = listOf(Color(0xFF9AA4B2), BLUE, Color(0xFFEF4444), Color(0xFF22C55E), Color(0xFFF59E0B), Color(0xFFA855F7), Color(0xFFF97316))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(t("Storage Analyzer"), { Nav.back() })
        Text(dir.path, Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, color = K.sub)
        val r = res
        if (r == null) {
            val p = pr
            ScanBar("🔍 " + t("Scanning") + " ${p?.first ?: 0} " + t("files") + " • ${FS.fmt(p?.second?.first ?: 0)}" +
                (p?.second?.second?.let { " • $it" } ?: ""))
        } else {
            val tot = r.first.sum().coerceAtLeast(1)
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(140.dp), Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        var a = -90f
                        r.first.forEachIndexed { i, v ->
                            val sw = 360f * v / tot
                            drawArc(cols[i], a, sw, false, Offset(14f, 14f), Size(size.width - 28f, size.height - 28f), style = Stroke(28f)); a += sw
                        }
                    }
                    Text(FS.fmt(tot), fontWeight = FontWeight.Bold)
                }
                Column(Modifier.padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    r.first.forEachIndexed { i, v ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(cols[i], CircleShape))
                            Text("  ${t(names[i])}  ${FS.fmt(v)}", fontSize = 13.sp)
                        }
                    }
                }
            }
            Sec("Largest Folders")
            r.second.forEach { (f, s) ->
                Row(Modifier.fillMaxWidth().clickable { if (f.isDirectory) Nav.go("analyzer", f) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(FS.icon(f) + " " + f.name, Modifier.weight(1f), maxLines = 1); Text(FS.fmt(s), color = K.sub)
                }
            }
        }
    }
}

@Composable
fun Dups() {
    val c = LocalContext.current
    var d by remember { mutableStateOf<List<File>?>(null) }
    var pr by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var ask by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        d = withContext(Dispatchers.IO) { FS.dups(FS.roots(c)[0]) { n, p -> pr = n to p } }
        pr = null
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Duplicates"), { Nav.back() })
        val l = d
        if (l == null) {
            val p = pr
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                CircularProgressIndicator(Modifier.padding(bottom = 14.dp))
                ScanBar("🐞 LeafExplore — leafscan debug (streaming)\n"+(p?.first ?: 0) + t("files") + (p?.second?.let { " • $it" } ?: ""))
            }
        } else {
            Text("${l.size} ${t("Duplicates").lowercase()} • ${FS.fmt(l.sumOf { it.length() })}", Modifier.padding(horizontal = 20.dp), fontSize = 16.sp)
            Button({ ask = true }, Modifier.padding(20.dp, 8.dp), enabled = l.isNotEmpty()) { Text(t("Delete")) }
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp)) { items(l) { Text(it.path, Modifier.padding(vertical = 4.dp), fontSize = 12.sp, maxLines = 2, color = K.sub) } }
            if (ask) AlertDialog({ ask = false },
                confirmButton = { TextButton({ l.forEach { it.delete() }; d = emptyList(); ask = false }) { Text(t("Delete")) } },
                dismissButton = { TextButton({ ask = false }) { Text(t("Cancel")) } }, text = { Text(t("Delete selected?")) })
        }
    }
}

@Composable
fun SettingsScreen() {
    val c = LocalContext.current
    var dlg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(t("Settings"), { Nav.back() })
        Sec("General")
        SRow("Language", if (Prefs.lang == "id") "Indonesia" else "English (Default)") { dlg = "lang" }
        SRow("Theme", Prefs.theme.replaceFirstChar { it.uppercase() }) { dlg = "theme" }
        SRow("PIN Lock", if (Prefs.pin.isEmpty()) "Disabled" else "Enabled") { Nav.go(if (Prefs.pin.isEmpty()) "pin_set" else "pin_off") }
        SRow("AdMob (Test)", t("Show test banner"), Prefs.ads) { Prefs.ads = !Prefs.ads; Prefs.save() }
        Sec("Tools")
        SRow("Default View", listOf("Grid", "List", "Card")[Prefs.view]) { dlg = "view" }
        SRow("Default Path", Prefs.path.ifEmpty { "/storage/emulated/0" }) { dlg = "path" }
        SRow("Show Hidden Files", sw = Prefs.hidden) { Prefs.hidden = !Prefs.hidden; Prefs.save() }
        SRow("Clear Cache", FS.fmt(c.cacheDir.walkTopDown().sumOf { it.length() })) { c.cacheDir.listFiles()?.forEach { it.deleteRecursively() }; Toast.makeText(c, "✓", Toast.LENGTH_SHORT).show() }
    }
    when (dlg) {
        "lang" -> Choice("Language", listOf("en" to "English", "id" to "Indonesia"), { Prefs.lang = it; Prefs.save() }) { dlg = "" }
        "theme" -> Choice("Theme", listOf("system" to t("System"), "light" to t("Light"), "dark" to t("Dark")), { Prefs.theme = it; Prefs.save() }) { dlg = "" }
        "view" -> Choice("Default View", listOf("0" to t("Grid"), "1" to t("List"), "2" to t("Card")), { Prefs.view = it.toInt(); Prefs.save() }) { dlg = "" }
        "path" -> {
            var p by remember { mutableStateOf(Prefs.path) }
            AlertDialog({ dlg = "" }, confirmButton = { TextButton({ Prefs.path = p; Prefs.save(); dlg = "" }) { Text(t("Save")) } },
                text = { OutlinedTextField(p, { p = it }, singleLine = true, label = { Text(t("Default Path")) }) })
        }
    }
}
