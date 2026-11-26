package uk.co.deanwild.flowtextview

import android.content.Context
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.widget.RelativeLayout
import androidx.core.text.HtmlCompat
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
class LinkClickTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clickedUrls = mutableListOf<String>()

    private fun laidOut(text: CharSequence, configure: FlowTextView.() -> Unit = {}) = FlowTextView(context).apply {
        textSize = 30f
        this.text = text
        onLinkClickListener = { clickedUrls += it }
        configure()
        measure(
            MeasureSpec.makeMeasureSpec(600, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        layout(0, 0, measuredWidth, measuredHeight)
    }

    private val html = HtmlCompat.fromHtml(
        "Some text before <a href=\"https://example.com/\">the link</a> and some text after it.",
        HtmlCompat.FROM_HTML_MODE_LEGACY,
    )

    @Test
    fun tappingAUrlSpanNotifiesTheListener() {
        val view = laidOut(html)
        assertThat(view.tapCharacter(html.indexOf("link"))).isTrue()
        assertThat(clickedUrls).containsExactly("https://example.com/")
    }

    @Test
    fun clickableSpansReceiveOnClick() {
        var clicked: View? = null
        val span = object : ClickableSpan() {
            override fun onClick(widget: View) {
                clicked = widget
            }
        }
        val text = buildSpannedString { append("Tap "); inSpans(span) { append("here") }; append(" please") }
        val view = laidOut(text)

        view.tapCharacter(text.indexOf("here") + 1)
        assertThat(clicked).isSameInstanceAs(view)
        assertThat(clickedUrls).isEmpty()
    }

    @Test
    fun withoutAListenerUrlSpansHandleTheClickThemselves() {
        var opened: String? = null
        val span = object : URLSpan("https://example.org/") {
            override fun onClick(widget: View) {
                opened = url
            }
        }
        val text = buildSpannedString { inSpans(span) { append("link") } }
        val view = laidOut(text) { onLinkClickListener = null }

        view.tapCharacter(1)
        assertThat(opened).isEqualTo("https://example.org/")
    }

    @Test
    fun tapsOnPlainTextAreNotConsumed() {
        // 2.x's ClickHandler swallowed every touch, which is why it had to be disabled.
        val view = laidOut(html)
        assertThat(view.tapCharacter(html.indexOf("before"))).isFalse()
        assertThat(clickedUrls).isEmpty()
    }

    @Test
    fun tapsInTheEmptySpaceAfterALineAreNotConsumed() {
        val text = buildSpannedString { append("short "); inSpans(URLSpan("https://example.com/")) { append("link") } }
        val view = laidOut(text)
        val segment = view.currentTextLayout.segments.single()
        val y = segment.top + segment.layout.getLineBaseline(0) - 5f

        assertThat(view.tap(580f, y)).isFalse()
        assertThat(clickedUrls).isEmpty()
    }

    @Test
    fun draggingOffALinkCancelsTheClick() {
        val view = laidOut(html)
        val (x, y) = view.positionOf(html.indexOf("link"))

        assertThat(view.dispatch(MotionEvent.ACTION_DOWN, x, y)).isTrue()
        view.dispatch(MotionEvent.ACTION_MOVE, x, y + 200)
        view.dispatch(MotionEvent.ACTION_UP, x, y + 200)
        assertThat(clickedUrls).isEmpty()
    }

    @Test
    fun cancelledGesturesDoNotClick() {
        val view = laidOut(html)
        val (x, y) = view.positionOf(html.indexOf("link"))

        view.dispatch(MotionEvent.ACTION_DOWN, x, y)
        view.dispatch(MotionEvent.ACTION_CANCEL, x, y)
        view.dispatch(MotionEvent.ACTION_UP, x, y)
        assertThat(clickedUrls).isEmpty()
    }

    @Test
    fun linksBesideChildrenAreHitTestedWhereTheyAreDrawn() {
        val view = laidOut(html) {
            addView(View(context), RelativeLayout.LayoutParams(250, 400))
        }
        val (x, _) = view.positionOf(html.indexOf("link"))
        assertThat(x).isGreaterThan(250f)

        view.tapCharacter(html.indexOf("link"))
        assertThat(clickedUrls).containsExactly("https://example.com/")
    }

    @Test
    fun linksAreFoundAcrossSegments() {
        val text = HtmlCompat.fromHtml(
            "word ".repeat(60) + "<a href=\"https://example.com/late\">late link</a>",
            HtmlCompat.FROM_HTML_MODE_LEGACY,
        )
        val view = laidOut(text) {
            // A full-width child splits the text into two segments.
            addView(View(context), RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, 100).apply { topMargin = 80 })
        }
        assertThat(view.currentTextLayout.segments.size).isAtLeast(2)

        view.tapCharacter((text as Spanned).indexOf("late") + 1)
        assertThat(clickedUrls).containsExactly("https://example.com/late")
    }

    // region helpers

    /** The centre of the character at [index], in view coordinates. */
    private fun FlowTextView.positionOf(index: Int): Pair<Float, Float> {
        val segment = currentTextLayout.segments.first { index >= it.start && index < it.end }
        val layout = segment.layout
        val line = layout.getLineForOffset(index)
        val x = (layout.getPrimaryHorizontal(index) + layout.getPrimaryHorizontal(index + 1)) / 2
        val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
        return (segment.left + x) to (segment.top + y)
    }

    private fun FlowTextView.tapCharacter(index: Int): Boolean {
        val (x, y) = positionOf(index)
        return tap(x, y)
    }

    private fun View.tap(x: Float, y: Float): Boolean {
        val consumed = dispatch(MotionEvent.ACTION_DOWN, x, y)
        dispatch(MotionEvent.ACTION_UP, x, y)
        return consumed
    }

    private fun View.dispatch(action: Int, x: Float, y: Float): Boolean {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        return dispatchTouchEvent(event).also { event.recycle() }
    }

    // endregion
}
