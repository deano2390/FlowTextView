package uk.co.deanwild.flowtextview

import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.View
import android.view.View.MeasureSpec
import android.widget.RelativeLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RtlTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val hebrew = "זהו טקסט לדוגמה שזורם סביב תמונות. FlowTextView תומך בכתיבה מימין לשמאל. ".repeat(30)
    private val latin = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. In sed massa diam. ".repeat(30)

    @Before
    fun enableRtl() {
        context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
    }

    private fun sampleLikeView(): FlowTextView = FlowTextView(context).apply {
        textSize = 40f
        setPadding(40, 40, 40, 40)
        addView(View(context), RelativeLayout.LayoutParams(300, 350).apply {
            addRule(RelativeLayout.ALIGN_PARENT_START)
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
            marginEnd = 30
        })
        addView(View(context), RelativeLayout.LayoutParams(140, 240).apply {
            addRule(RelativeLayout.ALIGN_PARENT_END)
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
            marginStart = 30
            topMargin = 900
        })
    }

    private fun FlowTextView.layoutAt(width: Int) {
        measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        layout(0, 0, measuredWidth, measuredHeight)
    }

    @Test
    fun rtlTextAvoidsMirroredChildren() {
        val view = sampleLikeView().apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            text = hebrew
            layoutAt(1080)
        }
        view.assertTextAvoidsChildren()
    }

    @Test
    fun switchingDirectionAfterLayoutReflowsAroundTheNewPositions() {
        val view = sampleLikeView().apply {
            text = latin
            layoutAt(1080)
        }
        view.assertTextAvoidsChildren()

        view.layoutDirection = View.LAYOUT_DIRECTION_RTL
        view.text = hebrew
        view.justificationMode = 1
        view.layoutAt(1080)
        view.assertTextAvoidsChildren()
    }

    private fun FlowTextView.assertTextAvoidsChildren() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            for (segment in currentTextLayout.segments) {
                val layout = segment.layout
                for (line in 0 until segment.lineCount) {
                    val top = segment.top + layout.getLineTop(line)
                    val bottom = segment.top + layout.getLineBottom(line)
                    if (top >= child.bottom || bottom <= child.top) continue
                    val start = layout.getLineStart(line)
                    var end = layout.getLineEnd(line)
                    while (end > start && layout.text[end - 1].isWhitespace()) end--
                    if (end == start) continue
                    val bounds = FloatArray((end - start) * 4).also { layout.fillCharacterBounds(start, end, it, 0) }
                    val glyphs = (0 until end - start).filterNot { layout.text[start + it].isWhitespace() }
                    val left = segment.left + glyphs.minOf { bounds[it * 4] }
                    val right = segment.left + glyphs.maxOf { bounds[it * 4 + 2] }
                    assertWithMessage("line $line [$left, $right] y[$top, $bottom] overlaps child $i [${child.left}, ${child.right}] y[${child.top}, ${child.bottom}]")
                        .that(left < child.right - 1 && right > child.left + 1).isFalse()
                }
            }
        }
    }
}
