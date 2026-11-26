package uk.co.deanwild.flowtextview.internal

import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.text.Spanned
import android.text.style.ClickableSpan
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.customview.widget.ExploreByTouchHelper
import uk.co.deanwild.flowtextview.FlowTextView
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Exposes the text to accessibility services and each [ClickableSpan] in it as a virtual view, so
 * that TalkBack and Switch Access users can find and activate links.
 */
internal class LinkAccessibilityHelper(private val view: FlowTextView) : ExploreByTouchHelper(view) {

    private val path = Path()
    private val pathBounds = RectF()

    private fun links(): List<ClickableSpan> {
        val spanned = view.text as? Spanned ?: return emptyList()
        return spanned.getSpans(0, spanned.length, ClickableSpan::class.java)
            .sortedBy { spanned.getSpanStart(it) }
    }

    override fun getVirtualViewAt(x: Float, y: Float): Int {
        val span = view.clickableSpanAt(x, y) ?: return INVALID_ID
        return links().indexOf(span).takeIf { it >= 0 } ?: INVALID_ID
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        links().indices.forEach { virtualViewIds += it }
    }

    override fun onPopulateNodeForHost(node: AccessibilityNodeInfoCompat) {
        node.text = view.text
    }

    override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
        val spanned = view.text as? Spanned
        val span = links().getOrNull(virtualViewId)
        if (spanned == null || span == null) {
            // The text changed under the accessibility service; report an empty, stale node.
            node.text = ""
            @Suppress("DEPRECATION")
            node.setBoundsInParent(Rect(0, 0, 1, 1))
            return
        }
        val start = spanned.getSpanStart(span)
        val end = spanned.getSpanEnd(span)
        node.text = spanned.subSequence(start, end)
        node.className = "android.widget.Button"
        node.isClickable = true
        node.addAction(AccessibilityActionCompat.ACTION_CLICK)
        @Suppress("DEPRECATION")
        node.setBoundsInParent(boundsOf(start, end))
    }

    override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
        val span = links().getOrNull(virtualViewId) ?: return false
        view.performLinkClick(span)
        sendEventForVirtualView(virtualViewId, android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED)
        return true
    }

    /** The union of the areas where the text from [start] to [end] is drawn, in view coordinates. */
    private fun boundsOf(start: Int, end: Int): Rect {
        val bounds = Rect()
        for (segment in view.currentTextLayout.segments) {
            val from = maxOf(start, segment.start)
            val to = minOf(end, segment.end)
            if (from >= to) continue
            segment.layout.getSelectionPath(from, to, path)
            path.computeBounds(pathBounds, true)
            bounds.union(
                floor(pathBounds.left).toInt() + segment.left,
                floor(pathBounds.top).toInt() + segment.top,
                ceil(pathBounds.right).toInt() + segment.left,
                ceil(pathBounds.bottom).toInt() + segment.top,
            )
        }
        if (bounds.isEmpty) bounds.set(0, 0, 1, 1) // ExploreByTouchHelper requires non-empty bounds.
        return bounds
    }
}
