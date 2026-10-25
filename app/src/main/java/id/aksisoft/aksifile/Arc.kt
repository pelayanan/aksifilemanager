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
import com.github.junrar.Archive
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class AEntry(val name: String, val size: Long, val dir: Boolean)

// ZIP / 7Z / RAR (extract only) / TAR / TAR.GZ / GZ
object Arc {
    fun type(f: File): String {
        val n = f.name.lowercase()
        return when {
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> "tgz"
            n.endsWith(".tar") -> "tar"
            n.endsWith(".gz") -> "gz"
            n.endsWith(".7z") -> "7z"
            n.endsWith(".rar") -> "rar"
            else -> "zip"
        }
    }

    fun baseName(f: File) = f.name.replace(Regex("(?i)\\.(tar\\.gz|tgz|tar|gz|zip|rar|7z)$"), "")
    private fun nm(s: String) = s.replace('\\', '/')
    private fun tarIn(f: File): TarArchiveInputStream {
        val b = f.inputStream().buffered()
        return TarArchiveInputStream(if (type(f) == "tgz") GzipCompressorInputStream(b) else b)
    }

    fun list(f: File): List<AEntry> = when (type(f)) {
        "zip" -> ZipFile(f).use { z -> z.entries().asSequence().map { AEntry(nm(it.name), it.size, it.isDirectory) }.toList() }
        "7z" -> SevenZFile(f).use { z -> generateSequence { z.nextEntry }.map { AEntry(nm(it.name), it.size, it.isDirectory) }.toList() }
        "rar" -> Archive(f).use { a -> a.fileHeaders.map { AEntry(nm(it.fileName), it.fullUnpackSize, it.isDirectory) } }
        "tgz", "tar" -> tarIn(f).use { t -> generateSequence { t.nextTarEntry }.map { AEntry(nm(it.name), it.size, it.isDirectory) }.toList() }
        else -> listOf(AEntry(f.nameWithoutExtension, f.length(), false))
    }

    fun extract(f: File, dest: File, only: Set<String>? = null) {
        dest.mkdirs()
        fun out(raw: String): File? {
            val name = nm(raw)
            if (only != null && only.none { name == it || name.startsWith(it.trimEnd('/') + "/") }) return null
            val o = File(dest, name)
            if (!o.canonicalPath.startsWith(dest.canonicalPath + File.separator)) throw SecurityException("Blocked unsafe path: $name")
            return o
        }
        fun w(o: File, dir: Boolean, write: (OutputStream) -> Unit) { if (dir) o.mkdirs() else { o.parentFile?.mkdirs(); o.outputStream().use(write) } }
        when (type(f)) {
            "zip" -> ZipFile(f).use { z -> z.entries().asSequence().forEach { e -> out(e.name)?.let { o -> w(o, e.isDirectory) { s -> z.getInputStream(e).use { it.copyTo(s) } } } } }
            "7z" -> SevenZFile(f).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val o = out(e.name)
                    if (o != null) w(o, e.isDirectory) { s -> val b = ByteArray(65536); var n: Int; while (z.read(b).also { n = it } > 0) s.write(b, 0, n) }
                    e = z.nextEntry
                }
            }
            "rar" -> Archive(f).use { a -> a.fileHeaders.forEach { h -> out(h.fileName)?.let { o -> w(o, h.isDirectory) { s -> a.extractFile(h, s) } } } }
            "tgz", "tar" -> tarIn(f).use { t ->
                var e = t.nextTarEntry
                while (e != null) {
                    val o = out(e.name)
                    if (o != null) w(o, e.isDirectory) { s -> t.copyTo(s) }
                    e = t.nextTarEntry
                }
            }
            else -> GzipCompressorInputStream(f.inputStream().buffered()).use { g -> File(dest, f.nameWithoutExtension).outputStream().use { g.copyTo(it) } }
        }
    }

    fun compress(src: List<File>, out: File, fmt: String) {
        val items = src.flatMap { s ->
            val base = s.parentFile!!
            (if (s.isDirectory) s.walkTopDown().toList() else listOf(s)).map { it to it.relativeTo(base).path }
        }
        when (fmt) {
            "zip" -> ZipOutputStream(out.outputStream().buffered()).use { z ->
                items.forEach { (f, n) ->
                    if (f.isDirectory) { z.putNextEntry(ZipEntry("$n/")); z.closeEntry() }
                    else { z.putNextEntry(ZipEntry(n)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
                }
            }
            "7z" -> SevenZOutputFile(out).use { z ->
                items.forEach { (f, n) ->
                    z.putArchiveEntry(z.createArchiveEntry(f, n))
                    if (f.isFile) f.inputStream().use { i -> val b = ByteArray(65536); var k: Int; while (i.read(b).also { k = it } > 0) z.write(b, 0, k) }
                    z.closeArchiveEntry()
                }
            }
            else -> TarArchiveOutputStream(GzipCompressorOutputStream(out.outputStream().buffered())).use { t ->
                t.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                items.forEach { (f, n) ->
                    t.putArchiveEntry(TarArchiveEntry(f, n))
                    if (f.isFile) f.inputStream().use { it.copyTo(t) }
                    t.closeArchiveEntry()
                }
            }
        }
    }
}

@Composable
fun ArchiveScreen(f: File) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<AEntry>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var sel by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(f) {
        withContext(Dispatchers.IO) { runCatching { Arc.list(f) } }.onSuccess { list = it }.onFailure { err = it.message ?: "Error" }
    }
    fun extract(only: Set<String>?) {
        scope.launch {
            busy = true
            val dest = FS.uniq(File(f.parentFile, Arc.baseName(f)))
            val r = withContext(Dispatchers.IO) { runCatching { Arc.extract(f, dest, only) } }
            busy = false; Nav.tick++
            Toast.makeText(c, if (r.isSuccess) "✓ ${dest.name}" else (r.exceptionOrNull()?.message ?: "Error"), Toast.LENGTH_LONG).show()
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(f.name, { Nav.back() })
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ extract(null) }, enabled = !busy) { Text(t("Extract all")) }
            OutlinedButton({ extract(sel) }, enabled = !busy && sel.isNotEmpty()) { Text(t("Extract selected")) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        val l = list
        when {
            err != null -> Text(err!!, Modifier.padding(20.dp), color = Color(0xFFEF4444))
            l == null -> CircularProgressIndicator(Modifier.padding(32.dp))
            else -> LazyColumn(contentPadding = PaddingValues(12.dp)) {
                items(l) { e ->
                    Row(Modifier.fillMaxWidth().clickable { sel = if (e.name in sel) sel - e.name else sel + e.name }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(e.name in sel, { sel = if (it) sel + e.name else sel - e.name })
                        Text(if (e.dir) "📁" else "📄")
                        Text(e.name, Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, fontSize = 13.sp)
                        if (!e.dir) Text(FS.fmt(e.size), fontSize = 11.sp, color = K.sub)
                    }
                }
            }
        }
    }
}
