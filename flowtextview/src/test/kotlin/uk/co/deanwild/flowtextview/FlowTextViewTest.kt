package uk.co.deanwild.flowtextview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannedString
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.view.View.MeasureSpec
import android.widget.RelativeLayout
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlowTextViewTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val lorem = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. In sed massa diam. ".repeat(20)

    private fun flowTextView(text: CharSequence = lorem) = FlowTextView(context).apply {
        textSize = 30f
        this.text = text
    }

    private fun FlowTextView.addBox(width: Int, height: Int, configure: RelativeLayout.LayoutParams.() -> Unit = {}): View {
        val child = View(context)
        addView(child, RelativeLayout.LayoutParams(width, height).apply(configure))
        return child
    }

    private fun View.layoutAt(width: Int, heightSpec: Int = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)) {
        measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightSpec)
        layout(0, 0, measuredWidth, measuredHeight)
    }

    @Test
    fun wrapContentHeightFitsTheText() {
        val view = flowTextView().apply { setPadding(10, 20, 10, 30) }
        view.layoutAt(600)

        assertThat(view.currentTextLayout.segments).isNotEmpty()
        assertThat(view.measuredHeight).isEqualTo(view.currentTextLayout.bottom + 30)
        assertThat(view.currentTextLayout.segments.first().top).isEqualTo(20)
    }

    @Test
    fun moreTextIsTaller() {
        val short = flowTextView("Short").apply { layoutAt(600) }
        val long = flowTextView().apply { layoutAt(600) }
        assertThat(long.measuredHeight).isGreaterThan(short.measuredHeight)
    }

    @Test
    fun heightIsKnownOnTheFirstMeasurePass() {
        // 2.x measured 100px first and corrected itself from onDraw a frame later.
        val view = flowTextView()
        view.measure(
            MeasureSpec.makeMeasureSpec(600, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        assertThat(view.measuredHeight).isEqualTo(view.currentTextLayout.bottom)
        assertThat(view.measuredHeight).isGreaterThan(100)
    }

    @Test
    fun heightCoversChildrenTallerThanTheText() {
        val view = flowTextView("Short")
        view.addBox(100, 500) { bottomMargin = 40 }
        view.layoutAt(600)
        assertThat(view.measuredHeight).isEqualTo(540)
    }

    @Test
    fun exactHeightIsHonoured() {
        val view = flowTextView()
        view.layoutAt(600, MeasureSpec.makeMeasureSpec(200, MeasureSpec.EXACTLY))
        assertThat(view.measuredHeight).isEqualTo(200)
    }

    @Test
    fun atMostHeightIsAnUpperBound() {
        val view = flowTextView()
        view.layoutAt(600, MeasureSpec.makeMeasureSpec(300, MeasureSpec.AT_MOST))
        assertThat(view.measuredHeight).isEqualTo(300)

        val short = flowTextView("Short")
        short.layoutAt(600, MeasureSpec.makeMeasureSpec(300, MeasureSpec.AT_MOST))
        assertThat(short.measuredHeight).isLessThan(300)
    }

    @Test
    fun textFlowsAroundAChildIncludingItsMargins() {
        val view = flowTextView()
        view.addBox(150, 200) {
            addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            topMargin = 100
            leftMargin = 25
        }
        view.layoutAt(600)

        val obstacleLeft = 600 - 150 - 25
        val besideChild = view.linesBetween(100, 300)
        assertThat(besideChild).isNotEmpty()
        besideChild.forEach { (_, right) -> assertThat(right).isAtMost(obstacleLeft.toFloat() + 1) }
        // The top margin positions the child but does not reserve space, as in 2.x.
        assertThat(view.linesBetween(0, 90).maxOf { it.second }).isGreaterThan(obstacleLeft.toFloat())
    }

    @Test
    fun goneChildrenAreIgnored() {
        val withChild = flowTextView().apply { addBox(300, 300).visibility = View.GONE; layoutAt(600) }
        val without = flowTextView().apply { layoutAt(600) }
        assertThat(withChild.measuredHeight).isEqualTo(without.measuredHeight)
    }

    @Test
    fun bottomAlignedChildrenArePlacedAgainstTheFinalHeight() {
        val view = flowTextView()
        val child = view.addBox(100, 100) { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM) }
        view.layoutAt(600)
        assertThat(child.bottom).isEqualTo(view.measuredHeight)
    }

    @Test
    fun paddingIsRespected() {
        val view = flowTextView().apply { setPadding(40, 0, 60, 0) }
        view.layoutAt(600)
        view.currentTextLayout.segments.forEach {
            assertThat(it.left).isEqualTo(40)
            assertThat(it.layout.width).isEqualTo(500)
        }
    }

    @Test
    fun plainInvalidateDoesNotRequestLayout() {
        // 2.x overrode invalidate() to trigger a relayout from onDraw on every frame.
        val view = flowTextView().apply { layoutAt(600) }
        view.invalidate()
        assertThat(view.isLayoutRequested).isFalse()
    }

    @Test
    fun colourChangesDoNotRequestLayout() {
        val view = flowTextView().apply { layoutAt(600) }
        view.textColor = Color.RED
        assertThat(view.isLayoutRequested).isFalse()
        assertThat(view.textPaint.color).isEqualTo(Color.RED)
    }

    @Test
    fun layoutIsReusedWhenNothingChanged() {
        val view = flowTextView().apply { layoutAt(600) }
        val before = view.currentTextLayout
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        assertThat(view.currentTextLayout).isSameInstanceAs(before)
    }

    @Test
    fun textSizeChangesRelayout() {
        val view = flowTextView().apply { layoutAt(600) }
        val before = view.measuredHeight
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, 60f)
        assertThat(view.isLayoutRequested).isTrue()
        view.layoutAt(600)
        assertThat(view.measuredHeight).isGreaterThan(before)
    }

    @Test
    fun lineSpacingAndTypefaceRelayout() {
        val view = flowTextView().apply { layoutAt(600) }
        val before = view.measuredHeight
        view.setLineSpacing(10f, 1.2f)
        view.layoutAt(600)
        assertThat(view.measuredHeight).isGreaterThan(before)
        assertThat(view.lineHeight).isGreaterThan(view.textPaint.getFontMetricsInt(null))

        view.typeface = Typeface.DEFAULT_BOLD
        assertThat(view.isLayoutRequested).isTrue()
    }

    @Test
    fun spannedTextThatIsNotSpannableKeepsItsStyling() {
        // 2.x only recognised Spannable, so SpannedString lost its styling.
        val text = SpannedString(buildSpannedString { append("plain "); inSpans(StyleSpan(Typeface.BOLD)) { append("bold") } })
        val view = flowTextView(text).apply { layoutAt(600) }
        assertThat(view.currentTextLayout.segments.single().layout.text).isSameInstanceAs(text)
    }

    @Test
    fun emptyTextHasOnlyPaddingHeight() {
        val view = flowTextView("").apply { setPadding(0, 5, 0, 7); layoutAt(600) }
        assertThat(view.measuredHeight).isEqualTo(12)
    }

    @Test
    fun drawsText() {
        val view = flowTextView("Hello").apply { textColor = Color.BLACK; layoutAt(300) }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        assertThat(pixels.any { Color.alpha(it) > 0 }).isTrue()
    }

    /** (left, right) extents of the drawn lines whose vertical band lies within [top, bottom). */
    private fun FlowTextView.linesBetween(top: Int, bottom: Int): List<Pair<Float, Float>> =
        currentTextLayout.segments.flatMap { segment ->
            (0 until segment.lineCount)
                .filter { segment.top + segment.layout.getLineTop(it) >= top && segment.top + segment.layout.getLineBottom(it) <= bottom }
                .map { line ->
                    val start = segment.layout.getLineStart(line)
                    var end = segment.layout.getLineEnd(line)
                    while (end > start && segment.layout.text[end - 1].isWhitespace()) end--
                    val bounds = FloatArray((end - start) * 4).also { segment.layout.fillCharacterBounds(start, end, it, 0) }
                    (segment.left + bounds[0]) to (segment.left + bounds[bounds.size - 2])
                }
        }
}
