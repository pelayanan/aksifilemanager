package id.aksisoft.aksifile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Environment
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [26])
@Suppress("unused")
class AppSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    val shots = File("/root/aksifile-shots").apply { mkdirs() }

    var n = 0
    fun shot(name: String) {
        rule.waitForIdle()
        val decor = rule.activity.window.decorView
        val w = if (decor.width > 0) decor.width else 412
        val h = if (decor.height > 0) decor.height else 892
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        decor.draw(Canvas(bmp))
        val f = File(shots, name)
        f.outputStream().use { o -> bmp.compress(Bitmap.CompressFormat.PNG, 95, o) }
        val blank = bmp.getPixel(w / 2, h / 2) == Color.WHITE &&
            bmp.getPixel(w / 4, h / 2) == Color.WHITE && bmp.getPixel(w - 10, h - 10) == Color.WHITE
        println("SHOT ${f.path} blank=$blank size=${f.length()}")
    }

    fun pause(ms: Long = 400) { Thread.sleep(ms); rule.waitForIdle() }

    fun tap(text: String) {
        rule.onAllNodes (hasText(text) and hasClickAction()).onFirst().performClick()
        rule.waitForIdle()
    }

    fun tapEmoji(e: String) {
        rule.onAllNodes (hasText(e, substring = true) and hasClickAction()).onFirst().performClick()
        rule.waitForIdle()
    }

    fun typeInto(s: String) {
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(s)
        rule.waitForIdle()
    }

    @Test
    fun fullSmoke() {
        val root = Environment.getExternalStorageDirectory()
        val tmp = File(root, "smoketest").apply { mkdirs() }

        // ---------- 00 SPLASH ----------
        org.robolectric.Shadows.shadowOf(rule.activity).grantPermissions(
            android.Manifest.permission.READ_EXTERNAL_STORAGE)
        shot("00_splash")
        rule.mainClock.advanceTimeBy(1800); rule.waitForIdle(); pause(700)

        // ---------- 01 HOME ----------
        assert(rule.onAllNodesWithText("Images").fetchSemanticsNodes().isNotEmpty()) { "Home tersedia" }
        shot("01_home")

        // ---------- 02 FILES ----------
        tap("Files"); shot("02_files")          // listing storage root (Home tab juga ada Files di bottom)
        Nav.tab("home"); rule.waitForIdle()

        // ---------- 03 TOOLS ----------
        tap("Tools"); shot("03_tools")

        // ---------- 04 MORE ----------
        tap("More"); shot("04_more")
        tap("Settings"); shot("05_settings")
        Nav.back(); rule.waitForIdle()

        // ---------- 06 CREATE TEXT FILE (form input + save) ----------
        tapEmoji("＋")                            // bottom FAB
        tap("New text file"); shot("06_dialog_new_file")
        val nf = File(tmp, "note.txt"); nf.createNewFile()
        Nav.go("editor", nf); rule.waitForIdle()
        typeInto("Halo AksiFile!\nBaris dua uji simpan.\nChar unicode: éü ✓")
        shot("07_editor_input")
        tap("Save"); pause(300)
        assert(nf.readText().contains("Halo AksiFile!")) { "Save editor gagal" }

        // find/replace form
        tap("🔎"); typeInto("dua"); rule.waitForIdle()
        shot("08_find_form"); tap("🔎")          // tutup
        Nav.back(); Nav.back(); rule.waitForIdle()

        // ---------- 10 MARKDOWN + PREVIEW ----------
        val md = File(tmp, "note.md"); md.createNewFile()
        Nav.go("editor", md); rule.waitForIdle()
        typeInto("# Judul Demo\n- paket [A](x)\n`kode` teks")
        tap("Preview"); pause(600); shot("09_markdown_preview")
        Nav.back(); Nav.back(); rule.waitForIdle()
        // ---------- 11 CSV ----------
        val csv = File(tmp, "test.csv"); csv.writeText("Nama,Nilai\nAndi,90\nBudi,88\nCica,95\n")
        Nav.go("csv", csv); rule.waitForIdle(); shot("10_csv")
        Nav.back(); rule.waitForIdle()

        // ---------- 12 DOCX ----------
        val docx = File(tmp, "sample.docx")
        val para = """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t>PARA1 FIRST</w:t></w:r></w:p><w:p><w:r><w:t>PARA2 SECOND</w:t></w:r></w:p></w:body></w:document>"""
        zipOf(docx, mapOf("word/document.xml" to para))
        Nav.go("docx", docx); rule.waitForIdle(); shot("11_docx")
        Nav.back(); rule.waitForIdle()
        // save docx -> .bak berisi konten awal
        Office.docxSave(docx, listOf("PARA1 FIRST", "PARA2 EDITED"), File(tmp, "out.docx"))
        assert(File(tmp, "out.docx").exists()) { "docxSave gagal" }

        // ---------- 13 XLSX ----------
        val xlsx = File(tmp, "sheet.xlsx")
        val wb = """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheet name="Data" sheetId="1" r:id="rId1" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/></workbook>"""
        val s1 = """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c t="s"><v>0</v></c><c t="s"><v>1</v></c></row><row r="2"><c><v>100</v></c><c><v>250</v></c></row></sheetData></worksheet>"""
        val ss = """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>Nominal</t></si><si><t>Terminal</t></si></sst>"""
        zipOf(xlsx, mapOf("xl/workbook.xml" to wb, "xl/worksheets/sheet1.xml" to s1, "xl/sharedStrings.xml" to ss))
        Nav.go("xlsx", xlsx); rule.waitForIdle(); shot("12_xlsx")
        Nav.back(); rule.waitForIdle()

        // ---------- 14 PPTX ----------
        val pptx = File(tmp, "deck.pptx")
        val sl = """<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:txBody><a:p><a:r><a:t>PPTX Judul Slide</a:t></a:r></a:p><a:p><a:r><a:t>Rincian item penting</a:t></a:r></a:p></p:txBody></p:sld>"""
        zipOf(pptx, mapOf("ppt/slides/slide1.xml" to sl))
        Nav.go("pptx", pptx); rule.waitForIdle(); shot("13_pptx")
        Nav.back(); rule.waitForIdle()

        // ---------- 15 PDF: generate 2 halaman + merge (sesungguhnya) ----------
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { d ->
            for (i in 1..2) {
                val p = com.tom_roush.pdfbox.pdmodel.PDPage()
                d.addPage(p)
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream(d, p).use { cs ->
                    cs.beginText(); cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 20f)
                    cs.newLineAtOffset(60f, 700f); cs.showText("AksiFile uji PDF halaman $i"); cs.endText()
                }
            }
            d.save(File(tmp, "in1.pdf"))
        }
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { d ->
            val p = com.tom_roush.pdfbox.pdmodel.PDPage(); d.addPage(p)
            d.save(File(tmp, "in2.pdf"))
        }
        val merged = File(tmp, "merged-out.pdf")
        Pdf.merge(listOf(File(tmp, "in1.pdf"), File(tmp, "in2.pdf")), merged)
        assert(merged.exists() && merged.length() > 1000) { "Pdf merge gagal: ${merged.length()}" }
        val txt = Pdf.text(merged); assert(txt.contains("uji PDF halaman 2")) { "PDF text extract gagal" }
        val stamped = File(tmp, "stamped.pdf")
        Pdf.stamp(merged, stamped, 1, "CNN approved", 70f, 60f, 16f); assert(stamped.exists())
        Nav.go("pdf", merged); rule.waitForIdle(); pause(500); shot("14_pdf_view")
        // tombol adil: extract text
        tap("Aa"); pause(400); shot("15_pdf_text_dialog")
        Nav.back(); rule.waitForIdle()

        // ---------- 16 ZIP: compress + browse + extract ----------
        listOf("a.txt" to "AAA", "b.txt" to "BBB").forEach { (nn, cc) -> File(tmp, nn).writeText(cc) }
        val zip = File(tmp, "bundle.zip")
        Arc.compress(listOf(File(tmp, "a.txt"), File(tmp, "b.txt"), File(tmp, "note.md")), zip, "zip")
        assert(zip.length() > 50) { "Arc.compress gagal" }
        Nav.go("archive", zip); rule.waitForIdle(); pause(500); shot("16_archive")
        val dest = File(tmp, "extracted"); dest.mkdirs()
        Arc.extract(zip, dest)
        assert(dest.listFiles()!!.size >= 2) { "Arc.extract gagal" }
        Nav.back(); rule.waitForIdle()

        // ---------- 17 IMAGE EDITOR ----------
        val img = File(tmp, "img.jpg")
        val bit = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bit); cv.drawColor(Color.MAGENTA)
        img.outputStream().use { bit.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        Nav.go("image", img); rule.waitForIdle(); pause(300); shot("17_image_editor")
        Nav.back(); rule.waitForIdle()

        // ---------- 18-21 GALLERY views ----------
        for ((name, key) in listOf("18_gallery_img" to "img", "19_gallery_doc" to "doc", "20_gallery_apk" to "apk")) {
            Nav.go("gallery", key); rule.waitForIdle(); pause(400); shot(name); Nav.back(); rule.waitForIdle()
        }

        // ---------- 22 ANALYZER ----------
        Nav.go("analyzer", root); rule.waitForIdle(); pause(800); shot("21_analyzer")
        Nav.back(); rule.waitForIdle()

        // ---------- 23 DUPLICATES ----------
        Nav.go("dups"); rule.waitForIdle(); pause(600); shot("22_duplicates")
        Nav.back(); rule.waitForIdle()

        // ---------- 24 CLEANER ----------
        Nav.go("cleaner"); rule.waitForIdle(); pause(600); shot("23_cleaner")
        Nav.back(); rule.waitForIdle()

        // ---------- 25 APPS ----------
        Nav.go("apps"); rule.waitForIdle(); pause(600); shot("24_apps")
        Nav.back(); rule.waitForIdle()

        // ---------- 26 FTP form ----------
        Nav.go("ftp"); rule.waitForIdle(); shot("25_ftp")
        // buka form add server, isi form (jan disimpen - AndroidKeyStore ora ono ing JVM)
        tapEmoji("＋"); pause(300); shot("26_ftp_add_form")
        val nodes = rule.onAllNodes(hasSetTextAction())
        if (nodes.fetchSemanticsNodes().size >= 2) {
            nodes[0].performTextInput("Demo FTP");
            
            rule.waitForIdle(); shot("27_ftp_form_filled")
        }
        Nav.back(); rule.waitForIdle()

        // ---------- 28 QR ----------
        Nav.go("qr"); rule.waitForIdle(); pause(400); shot("28_qr")
        Nav.back(); rule.waitForIdle()

        // ---------- 29 SETTINGS: LANG + THEME (klik asli) ----------
        Nav.tab("home"); rule.waitForIdle()
        Nav.go("settings"); rule.waitForIdle()
        if (rule.onAllNodes(hasText("Language") and hasClickAction()).fetchSemanticsNodes().size > 0) tap("Language") else tap("Bahasa")
        pause(300); tap("Indonesia"); pause(500); shot("29_settings_lang_id")
        tap("Tema"); pause(300); tap("Gelap"); pause(600); shot("30_settings_dark")
        Nav.tab("home")
        rule.mainClock.advanceTimeBy(1800); rule.waitForIdle(); pause(300)
        shot("31_home_dark_id")

        // ---------- 32 PIN ----------
        Prefs.pin = ""
        Nav.go("pin_set"); rule.waitForIdle(); shot("32_pin_create")
        listOf("1", "2", "3", "4").forEach { tap(it) }
        pause(500); listOf("1", "2", "3", "4").forEach { tap(it) }
        pause(500); shot("33_pin_confirmed")
        assert(Prefs.pin.isNotEmpty()) { "PIN gagal disimpan" }
        Prefs.pin = ""   // cleanup

        println("FULL SMOKE OK")
    }
}

private fun zipOf(out: File, files: Map<String, String>) {
    ZipOutputStream(out.outputStream()).use { z ->
        files.forEach { (name, content) ->
            z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray()); z.closeEntry()
        }
    }
}

