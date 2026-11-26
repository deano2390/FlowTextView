package uk.co.deanwild.flowtextview

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.text.LineBreaker
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.ScrollView
import androidx.core.text.HtmlCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden-image tests of a sample-like layout. Record with `./gradlew recordRoborazziDebug` after an
 * intentional visual change and review the images in src/test/screenshots.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-xhdpi")
class ScreenshotTest {

    private val latin = HtmlCompat.fromHtml(
        ("Lorem <b>ipsum dolor</b> sit amet, consectetur adipiscing elit. <a href=\"https://example.com\">In sed " +
            "massa diam</a>. Integer leo tortor, sagittis eget blandit et, malesuada id odio. Proin " +
            "gravida dui imperdiet, <i>tempor augue eu</i>, euismod odio. Vivamus nec euismod neque. ").repeat(6),
        HtmlCompat.FROM_HTML_MODE_LEGACY,
    )
    private val hebrew = HtmlCompat.fromHtml(
        "זהו טקסט לדוגמה שזורם סביב תמונות. <b>FlowTextView</b> תומך בכתיבה מימין לשמאל. ".repeat(14),
        HtmlCompat.FROM_HTML_MODE_LEGACY,
    )

    private val options = RoborazziOptions(
        // Tolerate anti-aliasing differences between machines.
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f),
    )

    @Test
    fun leftToRight() = capture("ltr") { text = latin }

    @Test
    fun rightToLeft() = capture("rtl", rtl = true) { text = hebrew }

    @Test
    fun justified() = capture("justified") {
        text = latin
        justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
    }

    private fun capture(name: String, rtl: Boolean = false, configure: FlowTextView.() -> Unit) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.applicationInfo.flags = activity.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        activity.setTheme(android.R.style.Theme_Material_Light_NoActionBar)

        val view = FlowTextView(activity).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            textColor = Color.BLACK
            layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            addView(box(Color.rgb(0x9C, 0xCC, 0x00)), RelativeLayout.LayoutParams(dp(110), dp(130)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START)
                marginEnd = dp(12)
                bottomMargin = dp(8)
            })
            addView(box(Color.rgb(0x3D, 0xDC, 0x84)), RelativeLayout.LayoutParams(dp(70), dp(110)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                topMargin = dp(260)
                marginStart = dp(12)
                bottomMargin = dp(8)
            })
            configure()
        }
        activity.setContentView(ScrollView(activity).apply { addView(view) }, ViewGroup.LayoutParams(-1, -1))
        shadowOf(Looper.getMainLooper()).idle()

        // Draw the view directly (Roborazzi's View capture relies on Espresso, which does not yet
        // support API 37) so the image is exactly what FlowTextView renders.
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        bitmap.captureRoboImage("src/test/screenshots/$name.png", options)
    }

    private fun box(color: Int) = View(org.robolectric.RuntimeEnvironment.getApplication()).apply { setBackgroundColor(color) }

    private fun View.dp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
