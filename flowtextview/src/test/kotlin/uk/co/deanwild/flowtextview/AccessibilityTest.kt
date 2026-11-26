package uk.co.deanwild.flowtextview

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.View.MeasureSpec
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.RelativeLayout
import androidx.core.text.HtmlCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccessibilityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clicked = mutableListOf<String>()

    private val view = FlowTextView(context).apply {
        textSize = 30f
        text = HtmlCompat.fromHtml(
            "Read <a href=\"https://one.example/\">the first link</a> or " +
                "<a href=\"https://two.example/\">the second</a> for more.",
            HtmlCompat.FROM_HTML_MODE_LEGACY,
        )
        onLinkClickListener = { clicked += it }
        addView(View(context), RelativeLayout.LayoutParams(200, 200))
        measure(MeasureSpec.makeMeasureSpec(600, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        layout(0, 0, measuredWidth, measuredHeight)
    }

    private val provider get() = view.accessibilityNodeProvider!!

    @Test
    fun hostNodeExposesTheText() {
        val node = provider.createAccessibilityNodeInfo(View.NO_ID)!!
        assertThat(node.text.toString()).isEqualTo("Read the first link or the second for more.")
        assertThat(view.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
    }

    @Test
    fun eachLinkIsAClickableVirtualView() {
        val first = provider.createAccessibilityNodeInfo(0)!!
        val second = provider.createAccessibilityNodeInfo(1)!!

        assertThat(first.text.toString()).isEqualTo("the first link")
        assertThat(second.text.toString()).isEqualTo("the second")
        assertThat(first.isClickable).isTrue()
        assertThat(first.actionList).contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
    }

    @Test
    fun linkBoundsMatchWhereTheLinkIsDrawn() {
        val bounds = Rect().also { @Suppress("DEPRECATION") provider.createAccessibilityNodeInfo(0)!!.getBoundsInParent(it) }
        assertThat(bounds.isEmpty).isFalse()
        assertThat(bounds.left).isAtLeast(200) // beside the child, not underneath it
        assertThat(view.clickableSpanAt(bounds.exactCenterX(), bounds.exactCenterY())).isNotNull()
    }

    @Test
    fun clickActionActivatesTheLink() {
        assertThat(provider.performAction(1, AccessibilityNodeInfo.ACTION_CLICK, null)).isTrue()
        assertThat(clicked).containsExactly("https://two.example/")
    }
}
