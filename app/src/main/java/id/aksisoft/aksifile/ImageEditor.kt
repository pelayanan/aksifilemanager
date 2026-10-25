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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot

private fun cropFor(a: Float, w: Int, h: Int): FloatArray {
    if (a <= 0f) return floatArrayOf(0f, 0f, 1f, 1f)
    val ia = w.toFloat() / h
    val fw: Float; val fh: Float
    if (a >= ia) { fw = 1f; fh = ia / a } else { fh = 1f; fw = a / ia }
    return floatArrayOf((1 - fw) / 2, (1 - fh) / 2, (1 + fw) / 2, (1 + fh) / 2)
}

// 0 none, 1 move, 2..5 corners (TL, TR, BL, BR)
private fun hit(o: Offset, e: Ed, g: FloatArray, r: Float): Int {
    val xs = floatArrayOf(g[0] + e.x0 * g[2], g[0] + e.x1 * g[2]); val ys = floatArrayOf(g[1] + e.y0 * g[3], g[1] + e.y1 * g[3])
    listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1).forEachIndexed { i, p -> if (hypot(o.x - xs[p.first], o.y - ys[p.second]) < r) return i + 2 }
    return if (o.x in xs[0]..xs[1] && o.y in ys[0]..ys[1]) 1 else 0
}

private fun drag(e: Ed, m: Int, dx: Float, dy: Float): Ed {
    val mn = 0.08f
    return when (m) {
        1 -> {
            val w = e.x1 - e.x0; val h = e.y1 - e.y0
            val nx = (e.x0 + dx).coerceIn(0f, 1f - w); val ny = (e.y0 + dy).coerceIn(0f, 1f - h)
            e.copy(x0 = nx, x1 = nx + w, y0 = ny, y1 = ny + h)
        }
        2 -> e.copy(x0 = (e.x0 + dx).coerceIn(0f, e.x1 - mn), y0 = (e.y0 + dy).coerceIn(0f, e.y1 - mn))
        3 -> e.copy(x1 = (e.x1 + dx).coerceIn(e.x0 + mn, 1f), y0 = (e.y0 + dy).coerceIn(0f, e.y1 - mn))
        4 -> e.copy(x0 = (e.x0 + dx).coerceIn(0f, e.x1 - mn), y1 = (e.y1 + dy).coerceIn(e.y0 + mn, 1f))
        5 -> e.copy(x1 = (e.x1 + dx).coerceIn(e.x0 + mn, 1f), y1 = (e.y1 + dy).coerceIn(e.y0 + mn, 1f))
        else -> e
    }
}

// Image viewer + editor: free crop (drag box/corners + ratios), flip/rotate, filters, adjust, tone, sharpen, Save As
@Composable
fun ImageScreen(f: File) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val src by produceState<Bitmap?>(null) { value = withContext(Dispatchers.IO) { Media.decode(f, 1200) } }
    val small = remember(src) { src?.let { Media.scale(it, 900) } }
    var ed by remember { mutableStateOf(Ed()) }
    val edNow by rememberUpdatedState(ed)
    var tab by remember { mutableStateOf(0) }
    var prev by remember { mutableStateOf<Bitmap?>(null) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var dlg by remember { mutableStateOf(false) }
    val key = if (tab == 0) ed.copy(x0 = 0f, y0 = 0f, x1 = 1f, y1 = 1f) else ed
    LaunchedEffect(small, key, tab) { val s = small ?: return@LaunchedEffect; delay(30); prev = withContext(Dispatchers.IO) { Media.render(s, ed, crop = tab != 0) } }

    val p = prev
    val ar = if (p != null) p.width.toFloat() / p.height else 1f
    val bw = box.width.toFloat(); val bh = box.height.toFloat()
    val dw = if (bw / bh > ar) bh * ar else bw
    val dh = dw / ar
    val ox = (bw - dw) / 2; val oy = (bh - dh) / 2
    val geo by rememberUpdatedState(floatArrayOf(ox, oy, dw, dh))

    Column(Modifier.fillMaxSize()) {
        TopBar(f.name, { Nav.back() })
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black).onSizeChanged { box = it }, Alignment.Center) {
            if (p != null) Image(p.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) else CircularProgressIndicator()
            if (tab == 0 && p != null) Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                var mode = 0
                detectDragGestures(onDragStart = { o -> mode = hit(o, edNow, geo, 56f) }, onDragEnd = { mode = 0 }) { change, d ->
                    change.consume(); ed = drag(edNow, mode, d.x / geo[2], d.y / geo[3])
                }
            }) {
                val x0 = ox + ed.x0 * dw; val x1 = ox + ed.x1 * dw; val y0 = oy + ed.y0 * dh; val y1 = oy + ed.y1 * dh
                val dim = Color(0x99000000)
                drawRect(dim, Offset(ox, oy), Size(dw, y0 - oy)); drawRect(dim, Offset(ox, y1), Size(dw, oy + dh - y1))
                drawRect(dim, Offset(ox, y0), Size(x0 - ox, y1 - y0)); drawRect(dim, Offset(x1, y0), Size(ox + dw - x1, y1 - y0))
                drawRect(Color.White, Offset(x0, y0), Size(x1 - x0, y1 - y0), style = Stroke(3f))
                listOf(x0 to y0, x1 to y0, x0 to y1, x1 to y1).forEach { drawCircle(BLUE, 14f, Offset(it.first, it.second)) }
            }
        }
        Column(Modifier.padding(12.dp)) {
            val chips = Modifier.horizontalScroll(rememberScrollState())
            when (tab) {
                0 -> Row(chips, Arrangement.spacedBy(8.dp)) {
                    val pw = p?.width ?: 1; val ph = p?.height ?: 1
                    listOf(0f to "Free", 1f to "1:1", 4f / 3f to "4:3", 16f / 9f to "16:9", 3f / 4f to "3:4", 9f / 16f to "9:16").forEach { (a, n) ->
                        FilterChip(false, { val r = cropFor(a, pw, ph); ed = ed.copy(x0 = r[0], y0 = r[1], x1 = r[2], y1 = r[3]) }, { Text(n) })
                    }
                }
                1 -> Row(chips, Arrangement.spacedBy(8.dp)) {
                    listOf("None", "B&W", "Sepia", "Warm", "Cool", "Invert").forEachIndexed { i, n -> FilterChip(ed.fil == i, { ed = ed.copy(fil = i) }, { Text(n) }) }
                }
                2 -> Column(Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState())) {
                    Text("Brightness", fontSize = 11.sp); Slider(ed.br, { ed = ed.copy(br = it) }, valueRange = -100f..100f)
                    Text("Contrast", fontSize = 11.sp); Slider(ed.ct, { ed = ed.copy(ct = it) }, valueRange = 0.5f..1.5f)
                    Text("Saturation", fontSize = 11.sp); Slider(ed.sat, { ed = ed.copy(sat = it) }, valueRange = 0f..2f)
                    Text("Tone (cool ↔ warm)", fontSize = 11.sp); Slider(ed.warm, { ed = ed.copy(warm = it) }, valueRange = -1f..1f)
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Sharpen", Modifier.weight(1f)); Switch(ed.sharp, { ed = ed.copy(sharp = it) }) }
                }
                else -> Row(chips, Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ ed = ed.copy(rot = (ed.rot + 3) % 4, x0 = 0f, y0 = 0f, x1 = 1f, y1 = 1f) }) { Text("⟲ 90°") }
                    OutlinedButton({ ed = ed.copy(rot = (ed.rot + 1) % 4, x0 = 0f, y0 = 0f, x1 = 1f, y1 = 1f) }) { Text("⟳ 90°") }
                    OutlinedButton({ ed = ed.copy(fh = !ed.fh) }) { Text("⇋") }
                    OutlinedButton({ ed = ed.copy(fv = !ed.fv) }) { Text("⇅") }
                    OutlinedButton({ ed = Ed() }) { Text("Reset") }
                }
            }
            Row(Modifier.padding(top = 10.dp)) {
                listOf("✂" to "Crop", "✨" to "Filter", "🎚" to "Adjust", "⟳" to "Rotate").forEachIndexed { i, (ic, n) ->
                    Column(Modifier.weight(1f).clickable { tab = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ic, color = if (tab == i) BLUE else K.sub, fontSize = 20.sp); Text(t(n), fontSize = 11.sp, color = if (tab == i) BLUE else K.sub)
                    }
                }
            }
            Button({ dlg = true }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(t("Save As")) }
        }
    }
    if (dlg) {
        var name by remember { mutableStateOf(f.nameWithoutExtension + "_edit") }
        var fmt by remember { mutableStateOf("jpg") }
        AlertDialog({ dlg = false },
            confirmButton = { TextButton({
                dlg = false; val s = src
                if (s != null) scope.launch {
                    withContext(Dispatchers.IO) { runCatching { Media.save(Media.render(s, ed), FS.uniq(File(f.parentFile, "$name.$fmt")), fmt) } }
                    Nav.tick++; Toast.makeText(c, "✓", Toast.LENGTH_SHORT).show()
                }
            }) { Text(t("Save")) } },
            dismissButton = { TextButton({ dlg = false }) { Text(t("Cancel")) } },
            title = { Text(t("Save As")) },
            text = { Column {
                OutlinedTextField(name, { name = it }, singleLine = true)
                Row(Modifier.padding(top = 8.dp), Arrangement.spacedBy(8.dp)) { listOf("jpg", "png", "webp").forEach { x -> FilterChip(fmt == x, { fmt = x }, { Text(x.uppercase()) }) } }
            } })
    }
}
