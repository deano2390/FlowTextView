package uk.co.deanwild.flowtextview

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextPaint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The deprecated 2.x methods kept so existing callers still compile and behave sensibly. */
@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LegacyApiTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val view = FlowTextView(context)

    @Test
    fun textSizeAliases() {
        view.textSize = 42f
        assertThat(view.getTextsize()).isEqualTo(42f)
    }

    @Test
    fun colorAliasesTextColor() {
        view.setColor(Color.RED)
        assertThat(view.textColor).isEqualTo(Color.RED)
        assertThat(view.getColor()).isEqualTo(Color.RED)
    }

    @Test
    fun typeFaceAliasesTypeface() {
        view.typeface = Typeface.MONOSPACE
        assertThat(view.getTypeFace()).isSameInstanceAs(Typeface.MONOSPACE)
    }

    @Test
    fun setTextPaintCopiesIntoTheViewsPaint() {
        val paint = TextPaint().apply {
            textSize = 33f
            color = Color.GREEN
            typeface = Typeface.SERIF
        }
        view.setTextPaint(paint)

        assertThat(view.textPaint).isNotSameInstanceAs(paint)
        assertThat(view.textSize).isEqualTo(33f)
        assertThat(view.textColor).isEqualTo(Color.GREEN)
        assertThat(view.typeface).isSameInstanceAs(Typeface.SERIF)
        assertThat(view.isLayoutRequested).isTrue()
    }

    @Test
    fun linkPaintMapsToLinkTextColor() {
        view.setLinkPaint(TextPaint().apply { color = Color.MAGENTA })
        assertThat(view.linkTextColor).isEqualTo(Color.MAGENTA)
        assertThat(view.getLinkPaint().color).isEqualTo(Color.MAGENTA)
        assertThat(view.getLinkPaint().isUnderlineText).isTrue()
    }
}
