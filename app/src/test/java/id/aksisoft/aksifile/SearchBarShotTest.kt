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
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [26])
@Suppress("unused")
class SearchBarShotTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    val shots = File("/root/aksifile-shots").apply { mkdirs() }

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
        println("SHOT ${f.path} size=${f.length()}")
    }

    fun pause(ms: Long = 500) { Thread.sleep(ms); rule.waitForIdle() }

    @Test fun searchInTopBar() {
        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        listOf("appInfo.txt" to "hello", "notes.pdf.txt" to "world").forEach { (n, t) ->
            File(dl, n).writeText(t)
        }
        org.robolectric.Shadows.shadowOf(rule.activity).grantPermissions(
            android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        rule.mainClock.advanceTimeBy(1800); rule.waitForIdle(); pause(700)
        Nav.go("gallery", "doc"); pause(1000)
        shot("34_gallery_topbar_search")
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("appInfo")
        rule.waitForIdle()
        repeat(10) { rule.mainClock.advanceTimeBy(300); Thread.sleep(400); rule.waitForIdle() }
        val nAppInfo = rule.onAllNodesWithText("appInfo.txt").fetchSemanticsNodes().size
        val nNotes = rule.onAllNodesWithText("notes.pdf.txt").fetchSemanticsNodes().size
        println("FILTER appInfo=$nAppInfo notes=$nNotes")
        shot("35_gallery_topbar_query")
    }
}
