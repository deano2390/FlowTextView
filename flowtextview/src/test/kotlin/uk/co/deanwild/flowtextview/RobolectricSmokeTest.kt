package uk.co.deanwild.flowtextview

import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Guards the test infrastructure itself: the layout tests rely on Robolectric's native graphics
 * so that StaticLayout measures and breaks text exactly as a device would.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RobolectricSmokeTest {

    @Test
    fun staticLayoutMeasuresRealText() {
        val paint = TextPaint().apply { textSize = 40f }
        val text = "The quick brown fox jumps over the lazy dog"
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 200).build()

        assertThat(paint.measureText(text)).isGreaterThan(200f)
        assertThat(layout.lineCount).isGreaterThan(1)
    }
}
