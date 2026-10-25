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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

// ---------- Video cutter: trim + save as MP4 (stream copy, no re-encode) ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(f: File) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val dur = remember { Media.duration(f) }
    var range by remember { mutableStateOf(0f..dur.toFloat()) }
    var dlg by remember { mutableStateOf(false) }
    fun tm(ms: Float) = "%02d:%02d".format((ms / 60000).toInt(), (ms / 1000 % 60).toInt())
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Video Cutter"), { Nav.back() })
        AndroidView({ ctx ->
            VideoView(ctx).apply { setVideoPath(f.path); setMediaController(MediaController(ctx).also { it.setAnchorView(this) }); seekTo(100) }
        }, Modifier.fillMaxWidth().weight(1f))
        Column(Modifier.padding(16.dp)) {
            Text("${tm(range.start)}  →  ${tm(range.endInclusive)}   /   ${tm(dur.toFloat())}", fontSize = 13.sp)
            RangeSlider(range, { range = it }, valueRange = 0f..dur.toFloat())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton({}, Modifier.weight(1f)) { Text("MP4 (H.264)") }
                Button({ dlg = true }, Modifier.weight(1f)) { Text(t("Save As")) }
            }
        }
    }
    if (dlg) {
        var name by remember { mutableStateOf(f.nameWithoutExtension + "_cut") }
        AlertDialog({ dlg = false },
            confirmButton = { TextButton({
                dlg = false
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Media.trim(f, FS.uniq(File(f.parentFile, "$name.mp4")), range.start.toLong(), range.endInclusive.toLong()) } }
                    Nav.tick++; Toast.makeText(c, if (r.isSuccess) "✓ MP4" else (r.exceptionOrNull()?.message ?: "Error"), Toast.LENGTH_LONG).show()
                }
            }) { Text(t("Save")) } },
            dismissButton = { TextButton({ dlg = false }) { Text(t("Cancel")) } },
            title = { Text(t("Save As") + " .mp4") }, text = { OutlinedTextField(name, { name = it }, singleLine = true) })
    }
}

// ---------- QR scanner: live camera + from image ----------
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun QrScreen() {
    val c = LocalContext.current
    val lo = LocalLifecycleOwner.current
    var res by remember { mutableStateOf<String?>(null) }
    var cam by remember { mutableStateOf(c.checkSelfPermission(Manifest.permission.CAMERA) == 0) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cam = it }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) Media.scanUri(c, u) { res = it } }
    LaunchedEffect(Unit) { if (!cam) perm.launch(Manifest.permission.CAMERA) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (cam) AndroidView({ ctx ->
            val pv = PreviewView(ctx)
            val fut = ProcessCameraProvider.getInstance(ctx)
            fut.addListener({
                val pre = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                val an = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                val sc = BarcodeScanning.getClient()
                an.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { px ->
                    val m = px.image
                    if (m != null && res == null) sc.process(InputImage.fromMediaImage(m, px.imageInfo.rotationDegrees))
                        .addOnSuccessListener { b -> b.firstOrNull()?.rawValue?.let { v -> if (res == null) res = v } }
                        .addOnCompleteListener { px.close() }
                    else px.close()
                }
                val p = fut.get(); p.unbindAll(); p.bindToLifecycle(lo, CameraSelector.DEFAULT_BACK_CAMERA, pre, an)
            }, ContextCompat.getMainExecutor(ctx))
            pv
        }, Modifier.fillMaxSize())
        Text("←", Modifier.align(Alignment.TopStart).clickable { Nav.back() }.padding(20.dp), color = Color.White, fontSize = 22.sp)
        Box(Modifier.align(Alignment.Center).size(240.dp).border(3.dp, BLUE, RoundedCornerShape(20.dp)))
        Text(t("Align the QR code within the frame"), Modifier.align(Alignment.Center).padding(top = 300.dp), color = Color.White, fontSize = 13.sp)
        Row(Modifier.align(Alignment.BottomCenter).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button({ if (cam) res = null else perm.launch(Manifest.permission.CAMERA) }) { Text("📷 " + t("Camera")) }
            OutlinedButton({ pick.launch("image/*") }) { Text("🖼 " + t("From Image"), color = Color.White) }
        }
    }
    res?.let { r ->
        AlertDialog({ res = null },
            confirmButton = { TextButton({
                (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("qr", r)); res = null
            }) { Text(t("Copy")) } },
            dismissButton = { Row {
                if (r.startsWith("http")) TextButton({ runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; res = null }) { Text(t("Open")) }
                TextButton({ res = null }) { Text("OK") }
            } },
            text = { Text(r) })
    }
}
