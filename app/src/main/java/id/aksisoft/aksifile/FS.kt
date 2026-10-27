package id.aksisoft.aksifile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FS {
    val IMG = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    val VID = setOf("mp4", "mkv", "webm", "3gp", "avi", "mov")
    val DOC = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md", "odt", "rtf")
    val AUD = setOf("mp3", "m4a", "wav", "flac", "ogg", "aac")
    val ZIP = setOf("zip", "rar", "7z", "tar", "gz", "tgz")
    val TXT = setOf("txt", "md", "html", "htm", "css", "js", "json", "xml", "kt", "java", "py", "log", "ini", "yml", "csv")

    fun hasAccess(c: Context) = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else c.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == 0

    fun roots(c: Context): List<File> {
        val r = c.getExternalFilesDirs(null).filterNotNull().map { File(it.path.substringBefore("/Android")) }
        return r.ifEmpty { listOf(Environment.getExternalStorageDirectory()) }
    }

    fun list(d: File) = (d.listFiles()?.toList() ?: emptyList()).filter { Prefs.hidden || !it.name.startsWith('.') }
        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

    fun size(f: File): Long = if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun fmt(b: Long): String {
        if (b < 1024) return "$b B"
        var v = b / 1024.0; var i = 0
        while (v >= 1024 && i < 3) { v /= 1024; i++ }
        return "%.1f %sB".format(v, "KMGT"[i])
    }

    fun kind(f: File) = when (f.extension.lowercase()) { in IMG -> 1; in VID -> 2; in DOC -> 3; "apk" -> 4; in AUD -> 5; in ZIP -> 6; else -> 0 }
    fun icon(f: File) = if (f.isDirectory) "📁" else when (kind(f)) { 1 -> "🖼"; 2 -> "🎬"; 3 -> "📄"; 4 -> "🤖"; 5 -> "🎵"; 6 -> "🗜"; else -> "📎" }
    fun mime(f: File) = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
    fun uri(c: Context, f: File) = FileProvider.getUriForFile(c, c.packageName + ".fp", f)
    fun h(s: String) = MessageDigest.getInstance("SHA-256").digest(("aksi$s").toByteArray()).joinToString("") { "%02x".format(it) }

    fun open(c: Context, f: File, m: String = mime(f)) = try {
        c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri(c, f), m)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) { Toast.makeText(c, "No app found", Toast.LENGTH_SHORT).show() }

    fun chooser(c: Context, f: File) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri(c, f), mime(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { c.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun share(c: Context, f: File) {
        val i = Intent(Intent.ACTION_SEND).setType(mime(f)).putExtra(Intent.EXTRA_STREAM, uri(c, f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        c.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun uniq(f: File): File {
        if (!f.exists()) return f
        var i = 1
        while (true) {
            val n = File(f.parentFile, if (f.extension.isEmpty() || f.isDirectory) "${f.name} ($i)" else "${f.nameWithoutExtension} ($i).${f.extension}")
            if (!n.exists()) return n
            i++
        }
    }

    fun isArc(f: File) = f.isFile && listOf(".zip", ".rar", ".7z", ".tar", ".gz", ".tgz").any { f.name.lowercase().endsWith(it) }
    fun fav(f: File) = Prefs.toggle("fav", f.path)
    fun recent(f: File) {
        val l = (listOf(f.path) + Prefs.sp.getString("rec", "")!!.split('\n')).filter { it.isNotBlank() }.distinct().take(30)
        Prefs.sp.edit().putString("rec", l.joinToString("\n")).apply()
    }

    private var scanK = ""; private var scanL: List<File> = emptyList()

    /** Snapshot file-tree cache: 1 x walk utk semua kategori, auto-expire 30 dtk / kalau root beda. */
    fun snapshot(root: File): List<File> {
        val now = System.currentTimeMillis()
        if (snapK == root.path && now - snapAt < 30000) return snapL
        snapL = root.walkTopDown().onEnter { Prefs.hidden || (!it.name.startsWith('.') && it.name != "Android") }
            .filter { it.isFile }.take(8000).toList()
        snapK = root.path; snapAt = now
        return snapL
    }
    private var snapK = ""; private var snapAt = 0L; private var snapL: List<File> = emptyList()

    fun scan(root: File, cat: String, q: String, force: Boolean = false): List<File> {
        val k = "${root.path}|$cat|$q"
        if (!force && k == scanK) return scanL
        val k2 = when (cat) { "img" -> 1; "vid" -> 2; "doc" -> 3; "apk" -> 4; "aud" -> 5; "zip" -> 6; else -> 0 }
        val src: Sequence<File> = when (cat) {
            "fav" -> Prefs.set("fav").map { File(it) }.asSequence()
            "rec" -> Prefs.sp.getString("rec", "")!!.split('\n').filter { it.isNotBlank() }.map { File(it) }.asSequence()
            else -> if (cat.startsWith("g:")) (Gal.all()[cat.drop(2)] ?: emptyList()).asSequence().map { File(it) }
                .flatMap { if (it.isDirectory) it.walkTopDown().filter { x -> x.isFile } else sequenceOf(it) } else snapshot(root).asSequence()
        }
        val out = src.filter { it.exists() && (it.isFile || cat == "fav") && (k2 == 0 || kind(it) == k2) && it.name.contains(q, true) }.take(2000).toList()
        scanL = out; scanK = k
        return out
    }

    fun zip(src: List<File>, out: File) = ZipOutputStream(out.outputStream().buffered()).use { z ->
        src.forEach { s ->
            val base = s.parentFile!!
            s.walkTopDown().filter { it.isFile }.forEach { f ->
                z.putNextEntry(ZipEntry(f.relativeTo(base).path)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
    }

    fun unzip(zip: File, dest: File) {
        dest.mkdirs()
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val o = File(dest, e.name)
                if (!o.canonicalPath.startsWith(dest.canonicalPath + File.separator)) throw SecurityException("zip slip")
                if (e.isDirectory) o.mkdirs() else { o.parentFile?.mkdirs(); o.outputStream().use { z.copyTo(it) } }
                e = z.nextEntry
            }
        }
    }

    fun paste(l: List<File>, cut: Boolean, dir: File) = l.forEach { f ->
        if (dir.path.startsWith(f.path + "/")) return@forEach
        val t = uniq(File(dir, f.name))
        if (!(cut && f.renameTo(t))) { f.copyRecursively(t, true); if (cut) f.deleteRecursively() }
    }

    fun analyze(dir: File, pr: (Long, Long, String) -> Unit = { _, _, _ -> }): Pair<LongArray, List<Pair<File, Long>>> {
        val by = LongArray(7); var n = 0L; var bt = 0L
        dir.walkTopDown().filter { it.isFile }.forEach { f ->
            by[kind(f)] += f.length(); n++; bt += f.length()
            if (n % 40 == 0L) pr(n, bt, f.parent ?: dir.path)
        }
        val kids = (dir.listFiles() ?: emptyArray()).map { it to size(it) }.sortedByDescending { it.second }.take(10)
        return by to kids
    }

    private fun md5(f: File) = runCatching {
        val m = MessageDigest.getInstance("MD5")
        f.inputStream().use { i -> val b = ByteArray(65536); var n: Int; while (i.read(b).also { n = it } > 0) m.update(b, 0, n) }
        m.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault(f.path)

    fun dups(root: File, pr: (Long, String) -> Unit = { _, _ -> }): List<File> {
        var n = 0L
        val cand = root.walkTopDown().onEnter { it.name != "Android" }
            .filter { it.isFile && it.length() > 0 }
            .onEach { n++; if (n % 40 == 0L) pr(n, it.parent ?: root.path) }
            .groupBy { it.length() }.values.filter { it.size > 1 }
        var cnt = 0L
        return cand.flatMap { g ->
            val dup = g.groupBy { md5(it) }.values.filter { it.size > 1 }.flatMap { it.drop(1) }
            cnt += dup.size
            pr(n, "hashing… ${cnt} dup")
            dup
        }
    }
}
