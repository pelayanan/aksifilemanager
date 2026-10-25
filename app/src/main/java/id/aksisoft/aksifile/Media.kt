package id.aksisoft.aksifile

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.nio.ByteBuffer

data class Ed(val rot: Int = 0, val fh: Boolean = false, val fv: Boolean = false, val x0: Float = 0f, val y0: Float = 0f, val x1: Float = 1f, val y1: Float = 1f,
    val fil: Int = 0, val br: Float = 0f, val ct: Float = 1f, val sat: Float = 1f, val warm: Float = 0f, val sharp: Boolean = false)

object Media {
    private val cache = LruCache<String, Bitmap>(60)

    fun thumb(f: File): Bitmap? = cache.get(f.path) ?: runCatching {
        val b = when (FS.kind(f)) {
            1 -> decode(f, 220)
            2 -> MediaMetadataRetriever().run { setDataSource(f.path); val x = getFrameAtTime(1_000_000); release(); x }
            else -> null
        }
        b?.also { cache.put(f.path, it) }
    }.getOrNull()

    fun decode(f: File, max: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / s > max * 2 || o.outHeight / s > max * 2) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    fun scale(b: Bitmap, max: Int): Bitmap {
        val r = max.toFloat() / maxOf(b.width, b.height)
        return if (r >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * r).toInt(), (b.height * r).toInt(), true)
    }

    // image editing: crop(aspect) -> rotate -> filter/brightness/contrast/saturation -> sharpen
    val FIL = listOf(
        ColorMatrix().array,
        ColorMatrix().apply { setSaturation(0f) }.array,
        floatArrayOf(.393f, .769f, .189f, 0f, 0f, .349f, .686f, .168f, 0f, 0f, .272f, .534f, .131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f),
        floatArrayOf(1.1f, 0f, 0f, 0f, 10f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, .9f, 0f, -10f, 0f, 0f, 0f, 1f, 0f),
        floatArrayOf(.9f, 0f, 0f, 0f, -10f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1.1f, 0f, 10f, 0f, 0f, 0f, 1f, 0f),
        floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))

    // rotate/flip -> free crop (fractions) -> filter / brightness / contrast / saturation / tone -> sharpen
    fun render(src: Bitmap, e: Ed, crop: Boolean = true): Bitmap {
        var b = src
        val mx = Matrix()
        if (e.rot % 4 != 0) mx.postRotate((e.rot % 4) * 90f)
        if (e.fh) mx.postScale(-1f, 1f)
        if (e.fv) mx.postScale(1f, -1f)
        if (!mx.isIdentity) b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, mx, true)
        if (crop && (e.x0 > 0f || e.y0 > 0f || e.x1 < 1f || e.y1 < 1f)) {
            val x = (e.x0 * b.width).toInt().coerceIn(0, b.width - 1); val y = (e.y0 * b.height).toInt().coerceIn(0, b.height - 1)
            val w = ((e.x1 - e.x0) * b.width).toInt().coerceIn(1, b.width - x); val h = ((e.y1 - e.y0) * b.height).toInt().coerceIn(1, b.height - y)
            b = Bitmap.createBitmap(b, x, y, w, h)
        }
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val m = ColorMatrix(FIL[e.fil])
        m.postConcat(ColorMatrix().apply { setSaturation(e.sat) })
        val t = 128f * (1 - e.ct) + e.br
        val tr = t + e.warm * 30f; val tb = t - e.warm * 30f
        m.postConcat(ColorMatrix(floatArrayOf(e.ct, 0f, 0f, 0f, tr, 0f, e.ct, 0f, 0f, t, 0f, 0f, e.ct, 0f, tb, 0f, 0f, 0f, 1f, 0f)))
        Canvas(out).drawBitmap(b, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(m) })
        return if (e.sharp) sharpen(out) else out
    }

    private fun sharpen(b: Bitmap): Bitmap {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        val o = px.copyOf()
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x; var rgb = 0
            for (s in intArrayOf(16, 8, 0)) {
                val v = 5 * ((px[i] shr s) and 255) - ((px[i - 1] shr s) and 255) - ((px[i + 1] shr s) and 255) - ((px[i - w] shr s) and 255) - ((px[i + w] shr s) and 255)
                rgb = rgb or (v.coerceIn(0, 255) shl s)
            }
            o[i] = (px[i] and 0xFF000000.toInt()) or rgb
        }
        return Bitmap.createBitmap(o, w, h, Bitmap.Config.ARGB_8888)
    }

    fun save(b: Bitmap, f: File, fmt: String) = f.outputStream().use {
        b.compress(when (fmt) { "png" -> Bitmap.CompressFormat.PNG; "webp" -> Bitmap.CompressFormat.WEBP; else -> Bitmap.CompressFormat.JPEG }, 92, it)
    }

    // video: cut without re-encoding, output MP4 (stream copy)
    fun trim(src: File, dst: File, startMs: Long, endMs: Long) {
        val ex = MediaExtractor().apply { setDataSource(src.path) }
        val mux = MediaMuxer(dst.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val map = HashMap<Int, Int>(); var max = 1 shl 20
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i); val m = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (m.startsWith("video") || m.startsWith("audio")) {
                ex.selectTrack(i); map[i] = mux.addTrack(f)
                if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) max = maxOf(max, f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }
        }
        mux.start(); ex.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val buf = ByteBuffer.allocate(max); val info = MediaCodec.BufferInfo(); var base = -1L
        while (true) {
            val n = ex.readSampleData(buf, 0); if (n < 0) break
            val t = ex.sampleTime; if (t > endMs * 1000) break
            if (base < 0) base = t
            info.set(0, n, t - base, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            mux.writeSampleData(map[ex.sampleTrackIndex]!!, buf, info); ex.advance()
        }
        mux.stop(); mux.release(); ex.release()
    }

    fun duration(f: File) = runCatching { MediaMetadataRetriever().run { setDataSource(f.path); val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong(); release(); d } }.getOrDefault(1000L)

    // PDF: view + merge (merge re-renders pages, so the result is image-based)
    fun pdfOpen(f: File) = PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY))
    fun pdfPage(r: PdfRenderer, i: Int, w: Int): Bitmap {
        val p = r.openPage(i)
        val b = Bitmap.createBitmap(w, (w * p.height / p.width.toFloat()).toInt(), Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.WHITE); p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); p.close(); return b
    }
    fun pdfMerge(files: List<File>, out: File) {
        val d = PdfDocument(); var n = 0
        files.forEach { f ->
            val r = pdfOpen(f)
            for (i in 0 until r.pageCount) {
                val b = pdfPage(r, i, 1240)
                val pg = d.startPage(PdfDocument.PageInfo.Builder(b.width, b.height, ++n).create())
                pg.canvas.drawBitmap(b, 0f, 0f, null); d.finishPage(pg); b.recycle()
            }
            r.close()
        }
        out.outputStream().use { d.writeTo(it) }; d.close()
    }

    fun csv(s: String): List<List<String>> = s.lines().filter { it.isNotBlank() }.take(2000).map { l ->
        val r = mutableListOf<String>(); val sb = StringBuilder(); var q = false
        for (ch in l) when {
            ch == '"' -> q = !q
            ch == ',' && !q -> { r.add(sb.toString()); sb.clear() }
            else -> sb.append(ch)
        }
        r.add(sb.toString()); r
    }

    // QR from an image picked by the user
    fun scanUri(c: Context, u: Uri, cb: (String) -> Unit) {
        runCatching {
            BarcodeScanning.getClient().process(InputImage.fromFilePath(c, u))
                .addOnSuccessListener { cb(it.firstOrNull()?.rawValue ?: "No QR code found") }
                .addOnFailureListener { cb("No QR code found") }
        }.onFailure { cb("No QR code found") }
    }
}
