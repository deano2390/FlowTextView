package uk.co.deanwild.flowtextview.internal

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristic
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Text layout options mirrored from the equivalent [android.widget.TextView] attributes. */
internal data class FlowLayoutConfig(
    val alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    val lineSpacingExtra: Float = 0f,
    val lineSpacingMultiplier: Float = 1f,
    val includeFontPadding: Boolean = true,
    val breakStrategy: Int = Layout.BREAK_STRATEGY_HIGH_QUALITY,
    val hyphenationFrequency: Int = Layout.HYPHENATION_FREQUENCY_NONE,
    /** One of `LineBreaker.JUSTIFICATION_MODE_*`; ignored before API 26. */
    val justificationMode: Int = 0,
    val textDirection: TextDirectionHeuristic = TextDirectionHeuristics.FIRSTSTRONG_LTR,
)

/**
 * A run of consecutive lines that share the same horizontal space, laid out by one [StaticLayout]
 * whose width is that space and drawn with its top-left corner at ([left], [top]) in view
 * coordinates. Only the first [lineCount] lines of [layout] belong to this segment; the text after
 * them is laid out again by the next segment, in the space available there.
 */
internal class FlowSegment(
    val layout: StaticLayout,
    val left: Int,
    val top: Int,
    val lineCount: Int,
    private val justified: Boolean,
) {
    /** Offset in the source text of the first character in this segment. */
    val start: Int get() = layout.getLineStart(0)

    /** Offset in the source text just past the last character in this segment. */
    val end: Int get() = layout.getLineEnd(lineCount - 1)

    /** Bottom of the last line in view coordinates. */
    val bottom: Int get() = top + layout.getLineTop(lineCount)

    /**
     * Draws the segment. If [highlight] is given, it is a path in this segment's coordinates
     * (see [Layout.getSelectionPath]) filled with [highlightPaint] behind the text.
     */
    fun draw(canvas: Canvas, highlight: Path? = null, highlightPaint: Paint? = null) {
        val save = canvas.save()
        canvas.translate(left.toFloat(), top.toFloat())
        if (lineCount < layout.lineCount) {
            canvas.clipRect(-UNBOUNDED, -UNBOUNDED, UNBOUNDED, layout.getLineTop(lineCount).toFloat())
        }
        layout.draw(canvas, highlight, highlightPaint, 0)
        canvas.restoreToCount(save)
    }

    /**
     * Returns the index of the character drawn under ([x], [y]) in view coordinates, or -1 if the
     * point is not over this segment's text (including the empty space beside a short line).
     */
    fun characterAt(x: Float, y: Float): Int {
        if (y < top || y >= bottom) return -1
        val line = layout.getLineForVertical((y - top).toInt())
        if (line >= lineCount) return -1

        val text = layout.text
        val start = layout.getLineStart(line)
        var end = layout.getLineEnd(line)
        while (end > start && text[end - 1].isWhitespace()) end--
        if (end == start) return -1

        // Justified lines are stretched to the full width, but getLineLeft()/getLineRight()
        // report their natural extent.
        val lx = x - left
        val rtl = layout.getParagraphDirection(line) == Layout.DIR_RIGHT_TO_LEFT
        val stretched = justified && layout.getLineEnd(line) < text.length && text[layout.getLineEnd(line) - 1] != '\n'
        val lo = if (stretched) 0f else layout.getLineLeft(line)
        val hi = if (stretched) layout.width.toFloat() else layout.getLineRight(line)
        if (lx < lo || lx > hi) return -1

        // getOffsetForHorizontal() returns the nearest caret position; pick the character on the
        // side of it that the point falls on.
        val offset = layout.getOffsetForHorizontal(line, lx)
        val char = when {
            offset >= end -> end - 1
            (!rtl && lx < layout.getPrimaryHorizontal(offset)) ||
                (rtl && lx > layout.getPrimaryHorizontal(offset)) -> offset - 1
            else -> offset
        }
        return char.coerceIn(start, end - 1)
    }

    private companion object {
        const val UNBOUNDED = 1_000_000f
    }
}

/** The result of laying out text around obstacles. [bottom] is the y just below the last line. */
internal class FlowLayoutResult(val segments: List<FlowSegment>, val bottom: Int) {
    companion object {
        fun empty(top: Int) = FlowLayoutResult(emptyList(), top)
    }

    /** Index of the character drawn under ([x], [y]) in view coordinates, or -1. */
    fun characterAt(x: Float, y: Float): Int {
        for (segment in segments) {
            if (y >= segment.top && y < segment.bottom) return segment.characterAt(x, y)
        }
        return -1
    }
}

/**
 * Lays text out around rectangular obstacles.
 *
 * Line breaking, styling, bidi, hyphenation and justification are all delegated to [StaticLayout].
 * The text is divided into segments: runs of consecutive lines that have the same free horizontal
 * space beside the obstacles. Each segment is a [StaticLayout] exactly as wide as that space, so
 * every platform feature behaves as it does in a TextView of that width.
 *
 * Per-line indents ([StaticLayout.Builder.setIndents]) would avoid the extra layouts but are not
 * usable: Minikin's optimal line breaker ignores them when choosing breaks, and justification only
 * accounts for the indent on the leading side, so text overflows into obstacles on the other.
 *
 * Segment heights are not known until laid out, so a segment's line count is decided from the
 * real line positions. Bands with no usable gap (e.g. beside a full-width child) are skipped.
 */
internal object FlowLayoutEngine {

    private const val MAX_REFINEMENTS = 4
    private const val MAX_STEPS = 10_000

    fun layout(
        text: CharSequence,
        paint: TextPaint,
        config: FlowLayoutConfig,
        left: Int,
        top: Int,
        right: Int,
        obstacles: List<Obstacle>,
    ): FlowLayoutResult {
        if (text.isEmpty() || right - left <= 0) return FlowLayoutResult.empty(top)

        val context = Context(text, paint, config, left, right, obstacles)
        val segments = ArrayList<FlowSegment>()
        var start = 0
        var y = top
        var steps = 0

        while (start < text.length && steps++ < MAX_STEPS) {
            val segment = context.buildSegment(start, y)
            if (segment == null) {
                y = context.skipBlockedBand(y)
                continue
            }
            segments += segment
            y = segment.bottom
            start = segment.end
        }

        return FlowLayoutResult(segments, y)
    }

    private class Context(
        val text: CharSequence,
        val paint: TextPaint,
        val config: FlowLayoutConfig,
        val left: Int,
        val right: Int,
        val obstacles: List<Obstacle>,
    ) {
        private val width = right - left
        private val nominalLineHeight: Int = max(
            1,
            (paint.getFontMetricsInt(null) * config.lineSpacingMultiplier + config.lineSpacingExtra).roundToInt(),
        )

        /** Gaps narrower than about one em are not worth filling with text. */
        private val minLineWidth: Int = ceil(paint.textSize).toInt().let { em -> if (width >= em) max(em, 1) else 1 }
        private val lowestObstacleBottom: Int = obstacles.maxOfOrNull { it.bottom } ?: Int.MIN_VALUE

        /** A rough upper bound on the characters in one line, used to size layout chunks. */
        private val maxCharsPerLine: Int = max(16, (width / max(1f, paint.textSize * 0.2f)).toInt())

        private fun freeSpan(bandTop: Int, bandBottom: Int): FreeSpan? =
            FlowGeometry.freeSpan(left, right, bandTop, bandBottom, obstacles, minLineWidth)

        /** Moves [y] past whatever blocks a line starting there. */
        fun skipBlockedBand(y: Int): Int {
            val next = FlowGeometry.nextObstacleBottom(y, y + nominalLineHeight, obstacles)
            return if (next != null && next > y) next else y + nominalLineHeight
        }

        /**
         * Lays out the text from [start] in the space free at [y], keeping the lines that fit in that
         * same space. Returns null if no line fits at [y].
         */
        fun buildSegment(start: Int, y: Int): FlowSegment? {
            val direction = DirectionChoice.at(text, start, config.textDirection)
            var span = freeSpan(y, y + nominalLineHeight) ?: return null

            repeat(MAX_REFINEMENTS) {
                val layout = build(start, span, direction, y)
                // The first line may be taller than estimated and reach a narrower band.
                val firstLineSpan = freeSpan(y, y + layout.getLineBottom(0)) ?: return null
                if (firstLineSpan == span) return segmentOf(layout, span, y, direction)
                span = firstLineSpan
            }
            return segmentOf(build(start, span, direction, y), span, y, direction)
        }

        private fun segmentOf(layout: StaticLayout, span: FreeSpan, y: Int, direction: DirectionChoice): FlowSegment? {
            // Keep lines while the free space beside them stays the same. Once the obstacles are
            // passed it can no longer change, so the rest of the layout is kept.
            var keep = 0
            while (keep < layout.lineCount) {
                val top = y + layout.getLineTop(keep)
                if (top >= lowestObstacleBottom && span.start == left && span.end == right) {
                    keep = layout.lineCount
                    break
                }
                if (freeSpan(top, y + layout.getLineBottom(keep)) != span) break
                keep++
            }
            if (direction.paragraphEnd >= 0) {
                keep = minOf(keep, layout.getLineForOffset(direction.paragraphEnd) + 1)
            }
            if (keep == 0) return null
            return FlowSegment(layout, span.start, y, keep, justified = config.justificationMode != 0)
        }

        /**
         * Builds a layout of the text from [start] in [span]. Only as much text is laid out as the
         * segment can use, rounded up to whole paragraphs so line breaks are unaffected.
         */
        private fun build(start: Int, span: FreeSpan, direction: DirectionChoice, y: Int): StaticLayout {
            val linesBeside = if (lowestObstacleBottom <= y) Int.MAX_VALUE else (lowestObstacleBottom - y) / nominalLineHeight + 2
            var chunk = if (linesBeside == Int.MAX_VALUE) Int.MAX_VALUE else linesBeside.toLong().times(maxCharsPerLine).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            while (true) {
                val end = paragraphEndAfter(start, chunk)
                val layout = build(start, end, span.width, direction.heuristic)
                // If every line of a truncated chunk fits beside the obstacles, the chunk was too
                // short to know where the segment ends; lay out more.
                val lastTop = y + layout.getLineTop(layout.lineCount - 1)
                if (end >= text.length || lastTop >= lowestObstacleBottom) return layout
                chunk = if (chunk > Int.MAX_VALUE / 2) Int.MAX_VALUE else chunk * 2
            }
        }

        /** The end of the paragraph containing `start + chunk`, or the end of the text. */
        private fun paragraphEndAfter(start: Int, chunk: Int): Int {
            if (chunk >= text.length - start) return text.length
            val newline = TextUtils.indexOf(text, '\n', start + chunk)
            return if (newline < 0) text.length else newline + 1
        }

        private fun build(start: Int, end: Int, width: Int, heuristic: TextDirectionHeuristic): StaticLayout {
            val builder = StaticLayout.Builder.obtain(text, start, end, paint, width)
                .setAlignment(config.alignment)
                .setTextDirection(heuristic)
                .setLineSpacing(config.lineSpacingExtra, config.lineSpacingMultiplier)
                // Font padding belongs above the first line of the text only; a segment that
                // continues the text must not add it again, or the line pitch jumps.
                .setIncludePad(config.includeFontPadding && start == 0)
                .setBreakStrategy(config.breakStrategy)
                .setHyphenationFrequency(config.hyphenationFrequency)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setJustificationMode(config.justificationMode)
            }
            return builder.build()
        }
    }

    /**
     * The direction to lay out a segment in. StaticLayout treats the start of its text as the start
     * of a paragraph, so a segment that resumes mid-paragraph could guess a different direction
     * from the paragraph's own (e.g. an RTL paragraph continuing with a Latin word). In that case
     * the paragraph's direction is forced, and the segment ends with that paragraph.
     */
    internal class DirectionChoice(val heuristic: TextDirectionHeuristic, val paragraphEnd: Int) {
        companion object {
            fun at(text: CharSequence, start: Int, heuristic: TextDirectionHeuristic): DirectionChoice {
                if (start == 0 || text[start - 1] == '\n') return DirectionChoice(heuristic, -1)
                val paragraphStart = TextUtils.lastIndexOf(text, '\n', start - 1) + 1
                val newline = TextUtils.indexOf(text, '\n', start)
                val paragraphEnd = if (newline < 0) text.length else newline
                val paragraphRtl = heuristic.isRtl(text, paragraphStart, paragraphEnd - paragraphStart)
                val restRtl = heuristic.isRtl(text, start, paragraphEnd - start)
                if (paragraphRtl == restRtl) return DirectionChoice(heuristic, -1)
                val forced = if (paragraphRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
                return DirectionChoice(forced, if (newline < 0) -1 else newline)
            }
        }
    }
}
