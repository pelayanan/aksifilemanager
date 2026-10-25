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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper

// Real PDF operations (PdfBox-Android): merge keeps text/vectors, split, extract text, stamp text
object Pdf {
    fun merge(files: List<File>, out: File) {
        val m = PDFMergerUtility()
        files.forEach { m.addSource(it) }
        m.destinationFileName = out.path
        m.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
    }
    fun text(f: File): String = PDDocument.load(f).use { PDFTextStripper().getText(it) }
    fun split(f: File, from: Int, to: Int, out: File) {
        PDDocument.load(f).use { src ->
            val a = from.coerceIn(1, src.numberOfPages); val b = to.coerceIn(a, src.numberOfPages)
            PDDocument().use { d -> for (i in a..b) d.importPage(src.getPage(i - 1)); d.save(out) }
        }
    }
    fun stamp(f: File, out: File, page: Int, text: String, x: Float, y: Float, size: Float) {
        PDDocument.load(f).use { d ->
            val pg = d.getPage(page.coerceIn(1, d.numberOfPages) - 1)
            PDPageContentStream(d, pg, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, size); cs.newLineAtOffset(x, y)
                cs.showText(text.map { if (it.code in 32..255) it else '?' }.joinToString("")); cs.endText()
            }
            d.save(out)
        }
    }
}

@Composable
fun PdfScreen(f: File) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val r = remember { runCatching { Media.pdfOpen(f) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { runCatching { r?.close() } } }
    var dlg by remember { mutableStateOf("") }
    var txt by remember { mutableStateOf("") }
    fun job(block: () -> File) {
        scope.launch {
            val res = withContext(Dispatchers.IO) { runCatching(block) }
            Nav.tick++
            Toast.makeText(c, res.map { "✓ " + it.name }.getOrElse { it.message ?: "Error" }, Toast.LENGTH_LONG).show()
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(f.name, { Nav.back() }) {
            TextButton({ scope.launch { txt = withContext(Dispatchers.IO) { runCatching { Pdf.text(f) }.getOrElse { it.message ?: "Error" } }; dlg = "text" } }) { Text("Aa") }
            TextButton({ dlg = "stamp" }) { Text("✎") }
            TextButton({ dlg = "split" }) { Text("✂") }
        }
        if (r == null) Text("Cannot open PDF", Modifier.padding(24.dp)) else LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(r.pageCount) { i ->
                val b by produceState<Bitmap?>(null, i) { value = withContext(Dispatchers.IO) { synchronized(r) { runCatching { Media.pdfPage(r, i, 1080) }.getOrNull() } } }
                val bm = b
                if (bm != null) Image(bm.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                else Box(Modifier.fillMaxWidth().height(400.dp).background(K.card))
            }
        }
    }
    when (dlg) {
        "text" -> AlertDialog({ dlg = "" },
            confirmButton = { TextButton({ (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("pdf", txt)); dlg = "" }) { Text(t("Copy")) } },
            dismissButton = { TextButton({ dlg = "" }) { Text("OK") } }, title = { Text(t("Text")) },
            text = { Text(txt.ifBlank { "—" }, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), fontSize = 13.sp) })
        "stamp" -> {
            var pg by remember { mutableStateOf("1") }; var tx by remember { mutableStateOf("") }
            var x by remember { mutableStateOf("72") }; var y by remember { mutableStateOf("720") }; var sz by remember { mutableStateOf("14") }
            AlertDialog({ dlg = "" },
                confirmButton = { TextButton({
                    dlg = ""
                    job { val o = FS.uniq(File(f.parentFile, f.nameWithoutExtension + "_edit.pdf")); Pdf.stamp(f, o, pg.toIntOrNull() ?: 1, tx, x.toFloatOrNull() ?: 72f, y.toFloatOrNull() ?: 720f, sz.toFloatOrNull() ?: 14f); o }
                }) { Text(t("Save")) } },
                dismissButton = { TextButton({ dlg = "" }) { Text(t("Cancel")) } }, title = { Text(t("Add text")) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(tx, { tx = it }, singleLine = true, label = { Text(t("Text")) })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(pg, { pg = it }, Modifier.weight(1f), singleLine = true, label = { Text("Page") })
                        OutlinedTextField(sz, { sz = it }, Modifier.weight(1f), singleLine = true, label = { Text("Size") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(x, { x = it }, Modifier.weight(1f), singleLine = true, label = { Text("X") })
                        OutlinedTextField(y, { y = it }, Modifier.weight(1f), singleLine = true, label = { Text("Y") })
                    }
                } })
        }
        "split" -> {
            var a by remember { mutableStateOf("1") }; var b by remember { mutableStateOf((r?.pageCount ?: 1).toString()) }
            AlertDialog({ dlg = "" },
                confirmButton = { TextButton({
                    dlg = ""
                    job { val i = a.toIntOrNull() ?: 1; val j = b.toIntOrNull() ?: i; val o = FS.uniq(File(f.parentFile, f.nameWithoutExtension + "_p$i-$j.pdf")); Pdf.split(f, i, j, o); o }
                }) { Text(t("Save")) } },
                dismissButton = { TextButton({ dlg = "" }) { Text(t("Cancel")) } }, title = { Text(t("Split")) },
                text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(a, { a = it }, Modifier.weight(1f), singleLine = true, label = { Text("From") })
                    OutlinedTextField(b, { b = it }, Modifier.weight(1f), singleLine = true, label = { Text("To") })
                } })
        }
    }
}
