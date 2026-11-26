package uk.co.deanwild.flowtextview

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.View.MeasureSpec
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AttributesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val metrics = context.resources.displayMetrics

    private fun attrs(vararg pairs: Pair<Int, String>): AttributeSet =
        Robolectric.buildAttributeSet().apply { pairs.forEach { (attr, value) -> addAttribute(attr, value) } }.build()

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, metrics)
    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics)

    @Test
    fun defaultsFollowTheThemeLikeTextView() {
        val view = FlowTextView(context)
        val textView = TextView(context)
        assertThat(view.textSize).isEqualTo(textView.textSize)
        val primary = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
        assertThat(view.textColors.defaultColor).isEqualTo(primary.getColorStateList(0)!!.defaultColor)
        primary.recycle()
        assertThat(view.linkTextColors).isEqualTo(textView.linkTextColors)
        assertThat(view.highlightColor).isEqualTo(textView.highlightColor)
        assertThat(view.lineSpacingMultiplier).isEqualTo(1f)
        assertThat(view.lineSpacingExtra).isEqualTo(0f)
        assertThat(view.text.toString()).isEmpty()
    }

    @Test
    fun readsTextAndSizing() {
        val view = FlowTextView(
            context,
            attrs(
                android.R.attr.text to "Hello from XML",
                android.R.attr.textSize to "18sp",
                android.R.attr.lineSpacingExtra to "4dp",
                android.R.attr.lineSpacingMultiplier to "1.5",
                android.R.attr.letterSpacing to "0.1",
            ),
        )
        assertThat(view.text.toString()).isEqualTo("Hello from XML")
        assertThat(view.textSize).isEqualTo(sp(18f))
        assertThat(view.lineSpacingExtra).isEqualTo(dp(4f))
        assertThat(view.lineSpacingMultiplier).isEqualTo(1.5f)
        assertThat(view.letterSpacing).isEqualTo(0.1f)
    }

    @Test
    fun readsColours() {
        val view = FlowTextView(
            context,
            attrs(
                android.R.attr.textColor to "#ff112233",
                android.R.attr.textColorLink to "#ff00aa00",
                android.R.attr.textColorHighlight to "#80ff0000",
            ),
        )
        assertThat(view.textColor).isEqualTo(0xff112233.toInt())
        assertThat(view.textPaint.color).isEqualTo(0xff112233.toInt())
        assertThat(view.linkTextColor).isEqualTo(0xff00aa00.toInt())
        assertThat(view.textPaint.linkColor).isEqualTo(0xff00aa00.toInt())
        assertThat(view.highlightColor).isEqualTo(0x80ff0000.toInt())
    }

    @Test
    fun readsTypefaceAndStyle() {
        val view = FlowTextView(
            context,
            attrs(android.R.attr.fontFamily to "serif", android.R.attr.textStyle to "bold|italic"),
        )
        assertThat(view.typeface).isNotNull()
        val style = view.typeface!!.style
        val synthetic = (if (view.textPaint.isFakeBoldText) Typeface.BOLD else 0) or
            (if (view.textPaint.textSkewX != 0f) Typeface.ITALIC else 0)
        assertThat(style or synthetic).isEqualTo(Typeface.BOLD_ITALIC)
    }

    @Test
    fun readsLayoutOptions() {
        val view = FlowTextView(
            context,
            attrs(
                android.R.attr.includeFontPadding to "false",
                android.R.attr.breakStrategy to "balanced",
                android.R.attr.hyphenationFrequency to "full",
                android.R.attr.justificationMode to "inter_word",
            ),
        )
        assertThat(view.includeFontPadding).isFalse()
        assertThat(view.breakStrategy).isEqualTo(Layout.BREAK_STRATEGY_BALANCED)
        assertThat(view.hyphenationFrequency).isEqualTo(Layout.HYPHENATION_FREQUENCY_FULL)
        assertThat(view.justificationMode).isEqualTo(LineBreaker.JUSTIFICATION_MODE_INTER_WORD)
    }

    @Test
    fun textAppearanceSuppliesDefaultsThatAttributesOverride() {
        val fromAppearance = FlowTextView(
            context,
            attrs(android.R.attr.textAppearance to "@android:style/TextAppearance.Large"),
        )
        assertThat(fromAppearance.textSize).isEqualTo(sp(22f))

        val overridden = FlowTextView(
            context,
            attrs(
                android.R.attr.textAppearance to "@android:style/TextAppearance.Large",
                android.R.attr.textSize to "12sp",
            ),
        )
        assertThat(overridden.textSize).isEqualTo(sp(12f))
    }

    @Test
    fun layoutOptionsReachTheLayout() {
        // View only resolves textAlignment when the app declares android:supportsRtl.
        context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val view = FlowTextView(context).apply {
            text = "Centred text"
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            includeFontPadding = false
            measure(MeasureSpec.makeMeasureSpec(600, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        }
        val layout = view.currentTextLayout.segments.single().layout
        assertThat(layout.alignment).isEqualTo(Layout.Alignment.ALIGN_CENTER)
        assertThat(layout.getLineTop(0)).isEqualTo(0)
        assertThat(layout.getLineDescent(0) - layout.getLineAscent(0)).isEqualTo(view.textPaint.getFontMetricsInt(null))
    }

    @Test
    fun settingAnOptionRequestsLayoutOnlyWhenItChanges() {
        val view = FlowTextView(context).apply {
            text = "Some text"
            measure(MeasureSpec.makeMeasureSpec(600, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            layout(0, 0, measuredWidth, measuredHeight)
        }
        view.breakStrategy = view.breakStrategy
        assertThat(view.isLayoutRequested).isFalse()
        view.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
        assertThat(view.isLayoutRequested).isTrue()
    }
}
