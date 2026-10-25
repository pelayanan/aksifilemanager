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
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// Word / Excel / PowerPoint (OOXML) without heavy libraries: read via zip + xml, edit docx text.
object Office {
    class Sheet(val name: String, val rows: List<List<String>>)

    fun unesc(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun entry(f: File, name: String): String? = ZipFile(f).use { z -> z.getEntry(name)?.let { e -> z.getInputStream(e).bufferedReader().readText() } }

    private val P = Regex("<w:p(?:\\s[^>]*)?/>|<w:p(?:\\s[^>]*)?>[\\s\\S]*?</w:p>")
    private val T = Regex("<w:t(?:\\s[^>]*)?>([\\s\\S]*?)</w:t>|<w:tab/>")
    private fun paraText(p: String) = T.findAll(p).joinToString("") { if (it.value.startsWith("<w:tab")) "\t" else unesc(it.groupValues[1]) }

    fun docx(f: File): List<String> = entry(f, "word/document.xml")?.let { x -> P.findAll(x).map { paraText(it.value) }.toList() } ?: emptyList()

    private fun run(rPr: String, t: String) = "<w:r>$rPr" + t.split('\t').joinToString("<w:tab/>") { "<w:t xml:space=\"preserve\">${esc(it)}</w:t>" } + "</w:r>"

    private fun rebuild(p: String, t: String): String {
        val open = Regex("^<w:p(?:\\s[^>]*?)?/?>").find(p)!!.value.removeSuffix("/>").removeSuffix(">") + ">"
        val pPr = Regex("<w:pPr>[\\s\\S]*?</w:pPr>").find(p)?.value ?: ""
        val rPr = Regex("<w:r(?:\\s[^>]*)?>\\s*(<w:rPr>[\\s\\S]*?</w:rPr>)").find(p)?.groupValues?.get(1) ?: ""
        return open + pPr + run(rPr, t) + "</w:p>"
    }

    // paragraph i of the original becomes lines[i]; untouched paragraphs keep their original XML (formatting)
    fun docxSave(f: File, lines: List<String>, out: File) {
        val xml = entry(f, "word/document.xml") ?: throw IOException("Not a .docx file")
        var i = 0
        var res = P.replace(xml) { m ->
            val txt = lines.getOrElse(i++) { "" }
            if (txt == paraText(m.value)) m.value else rebuild(m.value, txt)
        }
        if (lines.size > i) {
            val extra = lines.drop(i).joinToString("") { "<w:p>${run("", it)}</w:p>" }
            val endP = res.lastIndexOf("</w:p>"); val sect = res.lastIndexOf("<w:sectPr")
            val at = if (sect > endP) sect else res.lastIndexOf("</w:body>")
            res = res.substring(0, at) + extra + res.substring(at)
        }
        ZipFile(f).use { zf ->
            ZipOutputStream(out.outputStream().buffered()).use { z ->
                zf.entries().asSequence().forEach { e ->
                    z.putNextEntry(ZipEntry(e.name))
                    if (e.name == "word/document.xml") z.write(res.toByteArray()) else zf.getInputStream(e).use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
    }

    private fun col(ref: String): Int { var n = 0; for (ch in ref) { if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else break }; return n - 1 }

    private fun sheetRows(x: String, ss: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        Regex("<row\\b[^>]*[^/]>([\\s\\S]*?)</row>").findAll(x).take(2000).forEach { rm ->
            val cells = mutableMapOf<Int, String>()
            Regex("<c ([^>]*?)(?:/>|>([\\s\\S]*?)</c>)").findAll(rm.groupValues[1]).forEach { cm ->
                val at = cm.groupValues[1]; val body = cm.groupValues[2]
                val ref = Regex("r=\"([A-Z]+)\\d+\"").find(at)?.groupValues?.get(1) ?: return@forEach
                val tp = Regex("t=\"(\\w+)\"").find(at)?.groupValues?.get(1)
                val v = Regex("<v>([\\s\\S]*?)</v>").find(body)?.groupValues?.get(1)
                cells[col(ref)] = when (tp) {
                    "s" -> ss.getOrElse(v?.toIntOrNull() ?: -1) { "" }
                    "inlineStr" -> Regex("<t(?:\\s[^>]*)?>([\\s\\S]*?)</t>").find(body)?.groupValues?.get(1)?.let { unesc(it) } ?: ""
                    else -> unesc(v ?: "")
                }
            }
            val w = (cells.keys.maxOrNull() ?: -1) + 1
            rows.add(List(w) { cells[it] ?: "" })
        }
        return rows
    }

    fun xlsx(f: File): List<Sheet> = ZipFile(f).use { z ->
        fun rd(n: String) = z.getEntry(n)?.let { e -> z.getInputStream(e).bufferedReader().readText() }
        val ss = rd("xl/sharedStrings.xml")?.let { x ->
            Regex("<si>([\\s\\S]*?)</si>").findAll(x).map { m ->
                Regex("<t(?:\\s[^>]*)?>([\\s\\S]*?)</t>").findAll(m.groupValues[1]).joinToString("") { unesc(it.groupValues[1]) }
            }.toList()
        } ?: emptyList()
        val names = rd("xl/workbook.xml")?.let { x -> Regex("<sheet [^>]*name=\"([^\"]*)\"").findAll(x).map { unesc(it.groupValues[1]) }.toList() } ?: emptyList()
        names.mapIndexed { i, n -> Sheet(n, rd("xl/worksheets/sheet${i + 1}.xml")?.let { sheetRows(it, ss) } ?: emptyList()) }
    }

    fun pptx(f: File): List<List<String>> = ZipFile(f).use { z ->
        z.entries().asSequence().filter { Regex("ppt/slides/slide\\d+\\.xml").matches(it.name) }
            .sortedBy { it.name.filter(Char::isDigit).toInt() }
            .map { e ->
                val x = z.getInputStream(e).bufferedReader().readText()
                Regex("<a:p>([\\s\\S]*?)</a:p>").findAll(x).map { p ->
                    Regex("<a:t>([\\s\\S]*?)</a:t>").findAll(p.groupValues[1]).joinToString("") { unesc(it.groupValues[1]) }
                }.filter { it.isNotBlank() }.toList()
            }.toList()
    }
}

@Composable
fun Table(rows: List<List<String>>, mod: Modifier = Modifier) {
    Box(mod.horizontalScroll(rememberScrollState())) {
        LazyColumn {
            items(rows.size) { i ->
                Row(Modifier.background(if (i == 0) K.card2 else if (i % 2 == 0) K.card else Color.Transparent)) {
                    rows[i].forEach { Text(it, Modifier.width(130.dp).padding(8.dp), fontSize = 12.sp, maxLines = 2, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal) }
                }
            }
        }
    }
}

@Composable
fun CsvScreen(f: File) {
    val rows = remember { Media.csv(runCatching { f.readText() }.getOrDefault("")) }
    Column(Modifier.fillMaxSize()) {
        TopBar(f.name, { Nav.back() }) { TextButton({ Nav.go("editor", f) }) { Text("✎") } }
        Table(rows, Modifier.weight(1f))
    }
}

@Composable
fun DocxScreen(f: File) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(f) { withContext(Dispatchers.IO) { runCatching { Office.docx(f).joinToString("\n") } }.onSuccess { text = it }.onFailure { err = it.message ?: "Error" } }
    Column(Modifier.fillMaxSize()) {
        TopBar(f.name, { Nav.back() }) {
            Button({
                val s = text
                if (s != null) scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        runCatching {
                            val tmp = File(f.parentFile, f.name + ".tmp"); val bak = File(f.parentFile, f.name + ".bak")
                            Office.docxSave(f, s.split("\n"), tmp)
                            if (!bak.exists()) f.copyTo(bak)
                            tmp.renameTo(f)
                        }
                    }
                    Nav.tick++; Toast.makeText(c, if (r.isSuccess) "✓" else (r.exceptionOrNull()?.message ?: "Error"), Toast.LENGTH_SHORT).show()
                }
            }) { Text(t("Save")) }
        }
        Text("One line = one paragraph (.bak backup kept)", Modifier.padding(horizontal = 16.dp), fontSize = 11.sp, color = K.sub)
        val s = text
        when {
            err != null -> Text(err!!, Modifier.padding(20.dp), color = Color(0xFFEF4444))
            s == null -> CircularProgressIndicator(Modifier.padding(32.dp))
            else -> BasicTextField(s, { text = it }, Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                textStyle = TextStyle(color = K.tx, fontSize = 15.sp, lineHeight = 22.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(BLUE))
        }
    }
}

@Composable
fun XlsxScreen(f: File) {
    val c = LocalContext.current
    var sheets by remember { mutableStateOf<List<Office.Sheet>?>(null) }
    var cur by remember { mutableStateOf(0) }
    LaunchedEffect(f) { sheets = withContext(Dispatchers.IO) { runCatching { Office.xlsx(f) }.getOrDefault(emptyList()) } }
    Column(Modifier.fillMaxSize()) {
        TopBar(f.name, { Nav.back() }) {
            TextButton({
                sheets?.getOrNull(cur)?.let { sh ->
                    val o = FS.uniq(File(f.parentFile, f.nameWithoutExtension + "_" + sh.name + ".csv"))
                    o.writeText(sh.rows.joinToString("\n") { r -> r.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" } })
                    Nav.tick++; Toast.makeText(c, "✓ " + o.name, Toast.LENGTH_SHORT).show()
                }
            }) { Text("CSV") }
        }
        val s = sheets
        if (s == null) CircularProgressIndicator(Modifier.padding(32.dp)) else {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.forEachIndexed { i, sh -> FilterChip(cur == i, { cur = i }, { Text(sh.name) }) }
            }
            Table(s.getOrNull(cur)?.rows ?: emptyList(), Modifier.weight(1f).padding(top = 8.dp))
        }
    }
}

@Composable
fun PptxScreen(f: File) {
    var slides by remember { mutableStateOf<List<List<String>>?>(null) }
    LaunchedEffect(f) { slides = withContext(Dispatchers.IO) { runCatching { Office.pptx(f) }.getOrDefault(emptyList()) } }
    Column(Modifier.fillMaxSize()) {
        TopBar(f.name, { Nav.back() })
        val s = slides
        if (s == null) CircularProgressIndicator(Modifier.padding(32.dp)) else LazyColumn(contentPadding = PaddingValues(8.dp)) {
            itemsIndexed(s) { i, lines ->
                Column(Modifier.fillMaxWidth().padding(6.dp).clip(RoundedCornerShape(18.dp)).background(K.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Slide ${i + 1}", color = BLUE, fontSize = 12.sp)
                    lines.forEachIndexed { j, l -> Text(l, fontSize = if (j == 0) 18.sp else 14.sp, fontWeight = if (j == 0) FontWeight.Bold else FontWeight.Normal) }
                }
            }
        }
    }
}
