package id.aksisoft.aksifile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
class AdBannerShotTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    val shots = File("/root/aksifile-shots").apply { mkdirs() }

    fun shot(name: String, tab: String?) {
        rule.waitForIdle()
        if (tab != null) rule.onAllNodes(hasText(tab) and hasClickAction()).onFirst().performClick()
        rule.waitForIdle(); Thread.sleep(600); rule.waitForIdle()
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

    @Test fun bannerTabs() {
        org.robolectric.Shadows.shadowOf(rule.activity).grantPermissions(
            android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        rule.mainClock.advanceTimeBy(1800); rule.waitForIdle(); Thread.sleep(700); rule.waitForIdle()
        shot("36_ad_home", null)
        shot("37_ad_files", "Files")
        shot("38_ad_tools", "Tools")
        shot("39_ad_more", "More")
    }
}
