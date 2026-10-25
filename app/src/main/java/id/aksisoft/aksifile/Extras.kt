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
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject

// ---------- custom galleries (create gallery / add folder / add file) ----------
object Gal {
    fun all(): Map<String, List<String>> {
        val o = JSONObject(Prefs.sp.getString("gal", "{}")!!)
        return o.keys().asSequence().associateWith { k -> val a = o.getJSONArray(k); (0 until a.length()).map { a.getString(it) } }
    }
    private fun save(m: Map<String, List<String>>) {
        val o = JSONObject(); m.forEach { (k, v) -> o.put(k, JSONArray(v)) }
        Prefs.sp.edit().putString("gal", o.toString()).apply()
    }
    fun create(n: String) { if (n.isBlank()) return; val m = all().toMutableMap(); m.putIfAbsent(n, emptyList()); save(m) }
    fun add(n: String, p: String) { val m = all().toMutableMap(); m[n] = ((m[n] ?: emptyList()) + p).distinct(); save(m) }
    fun delete(n: String) { val m = all().toMutableMap(); m.remove(n); save(m) }
}

// ---------- installed apps: extract / share APK, info, uninstall ----------
class AppInfo(val label: String, val pkg: String, val apk: File, val icon: Bitmap?)

@Composable
fun AppsScreen() {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var menu by remember { mutableStateOf<AppInfo?>(null) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = c.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { ri ->
                AppInfo(ri.loadLabel(pm).toString(), ri.activityInfo.packageName, File(ri.activityInfo.applicationInfo.sourceDir), runCatching { ri.loadIcon(pm).toBitmap(96, 96) }.getOrNull())
            }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Apps"), { Nav.back() })
        val l = apps
        if (l == null) CircularProgressIndicator(Modifier.padding(32.dp)) else LazyColumn(contentPadding = PaddingValues(12.dp)) {
            items(l) { a ->
                Row(Modifier.fillMaxWidth().clickable { menu = a }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val ic = a.icon
                    if (ic != null) Image(ic.asImageBitmap(), null, Modifier.size(40.dp)) else Box(Modifier.size(40.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(a.label, maxLines = 1); Text(a.pkg, fontSize = 11.sp, color = K.sub, maxLines = 1) }
                    Text(FS.fmt(a.apk.length()), fontSize = 12.sp, color = K.sub)
                }
            }
        }
    }
    menu?.let { a ->
        Choice(a.label, listOf("extract" to t("Extract APK"), "share" to t("Share APK"), "info" to t("App info"), "uninstall" to t("Uninstall")), { k ->
            when (k) {
                "extract" -> scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        runCatching {
                            val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Aksi/apps").apply { mkdirs() }
                            val o = FS.uniq(File(d, a.label.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".apk")); a.apk.copyTo(o); o
                        }
                    }
                    Toast.makeText(c, r.map { "✓ " + it.path }.getOrElse { it.message ?: "Error" }, Toast.LENGTH_LONG).show()
                }
                "share" -> runCatching { FS.share(c, a.apk) }
                "info" -> c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "uninstall" -> c.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }) { menu = null }
    }
}

// ---------- cleaner: temp/log files, empty folders, large files ----------
fun scanJunk(root: File): List<Pair<String, List<File>>> {
    val tmp = mutableListOf<File>(); val empty = mutableListOf<File>(); val big = mutableListOf<File>()
    val junk = setOf("tmp", "temp", "log", "bak", "old")
    root.walkTopDown().onEnter { it.name != "Android" }.forEach { f ->
        if (f.isDirectory) { if (f != root && f.list()?.isEmpty() == true) empty.add(f) }
        else {
            if (f.extension.lowercase() in junk || f.parentFile?.name == ".thumbnails") tmp.add(f)
            if (f.length() > 100L * 1024 * 1024) big.add(f)
        }
    }
    return listOf("Temp & log files" to tmp, "Empty folders" to empty, "Large files (>100 MB)" to big.sortedByDescending { it.length() }.take(30))
}

@Composable
fun Cleaner() {
    val c = LocalContext.current
    var groups by remember { mutableStateOf<List<Pair<String, List<File>>>?>(null) }
    var sel by remember { mutableStateOf(setOf<File>()) }
    var ask by remember { mutableStateOf(false) }
    LaunchedEffect(Nav.tick) { groups = null; groups = withContext(Dispatchers.IO) { scanJunk(FS.roots(c)[0]) }; sel = setOf() }
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Cleaner"), { Nav.back() })
        val g = groups
        if (g == null) CircularProgressIndicator(Modifier.padding(32.dp)) else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                g.forEach { (title, files) ->
                    item {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("$title  (${files.size} • ${FS.fmt(files.sumOf { it.length() })})", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            TextButton({ sel = if (files.isNotEmpty() && files.all { it in sel }) sel - files.toSet() else sel + files }) { Text("✓ all") }
                        }
                    }
                    items(files) { f ->
                        Row(Modifier.fillMaxWidth().clickable { sel = if (f in sel) sel - f else sel + f }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(f in sel, { sel = if (it) sel + f else sel - f })
                            Column(Modifier.weight(1f)) { Text(f.name, maxLines = 1, fontSize = 13.sp); Text(f.path, maxLines = 1, fontSize = 10.sp, color = K.sub) }
                            if (f.isFile) Text(FS.fmt(f.length()), fontSize = 11.sp, color = K.sub)
                        }
                    }
                }
            }
            Button({ ask = true }, Modifier.fillMaxWidth().padding(12.dp), enabled = sel.isNotEmpty()) { Text(t("Delete") + " (${sel.size})") }
        }
    }
    if (ask) AlertDialog({ ask = false },
        confirmButton = { TextButton({ sel.forEach { it.deleteRecursively() }; ask = false; Nav.tick++ }) { Text(t("Delete")) } },
        dismissButton = { TextButton({ ask = false }) { Text(t("Cancel")) } }, text = { Text(t("Delete selected?") + " (${sel.size})") })
}
