package uk.co.deanwild.flowtextview.internal

import android.graphics.Canvas
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristic
import android.text.TextDirectionHeuristics
import android.text.TextPaint
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
 * A run of consecutive lines laid out by one [StaticLayout], drawn with its top-left corner at
 * ([left], [top]) in view coordinates. Only the first [lineCount] lines of [layout] belong to this
 * segment; any further lines ran into a fully blocked band and are re-laid-out by the next segment.
 */
internal class FlowSegment(
    val layout: StaticLayout,
    val left: Int,
    val top: Int,
    val lineCount: Int,
) {
    /** Offset in the source text of the first character in this segment. */
    val start: Int get() = layout.getLineStart(0)

    /** Offset in the source text just past the last character in this segment. */
    val end: Int get() = layout.getLineEnd(lineCount - 1)

    /** Bottom of the last line in view coordinates. */
    val bottom: Int get() = top + layout.getLineTop(lineCount)

    fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(left.toFloat(), top.toFloat())
        if (lineCount < layout.lineCount) {
            canvas.clipRect(-UNBOUNDED, -UNBOUNDED, UNBOUNDED, layout.getLineTop(lineCount).toFloat())
        }
        layout.draw(canvas)
        canvas.restoreToCount(save)
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
}

/**
 * Lays text out around rectangular obstacles.
 *
 * Line breaking, styling, bidi, hyphenation and justification are all delegated to [StaticLayout].
 * The engine's job is to tell it, line by line, how much horizontal space is free, which it does
 * with per-line indents ([StaticLayout.Builder.setIndents]). Indents are first estimated from the
 * nominal line height, then refined from the real line positions until they agree, so lines of
 * mixed height (e.g. with a `RelativeSizeSpan`) still avoid obstacles.
 *
 * A band that has no usable gap at all, such as one crossed by a full-width child, cannot be
 * expressed as an indent. When the text reaches one, the current [StaticLayout] is cut short and a
 * new segment continues below the obstacle.
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
        val width = right - left
        if (text.isEmpty() || width <= 0) return FlowLayoutResult.empty(top)

        val context = Context(text, paint, config, left, right, obstacles)
        val segments = ArrayList<FlowSegment>()
        var start = 0
        var y = top
        var steps = 0

        while (start < text.length && steps++ < MAX_STEPS) {
            val firstBand = context.freeSpan(y, y + context.nominalLineHeight)
            if (firstBand == null) {
                y = context.skipBlockedBand(y, y + context.nominalLineHeight)
                continue
            }

            val built = context.buildSegment(start, y)
            if (built.keep == 0) {
                // The first line turned out taller than estimated and hits a blocked band.
                val lineBottom = y + built.layout.getLineBottom(0)
                y = context.skipBlockedBand(y, lineBottom)
                continue
            }

            val segment = FlowSegment(built.layout, left, y, built.keep)
            segments += segment
            y = segment.bottom
            start = if (built.keep < built.layout.lineCount) segment.end else text.length
        }

        return FlowLayoutResult(segments, y)
    }

    /**
     * Per-line indents for one segment.
     *
     * @property blockedAt index of the first line whose band is fully blocked, or -1.
     * @property clearFrom index of the first line below every obstacle, or -1 if not reached.
     */
    private class IndentPlan(val left: IntArray, val right: IntArray, val blockedAt: Int, val clearFrom: Int) {
        /** True if any line is narrowed by an obstacle. */
        val indented: Boolean = left.any { it != 0 } || right.any { it != 0 }

        /**
         * Index of the line at which this segment must end, or -1 if it may run to the end of the
         * text. An indented segment also ends once the obstacles are passed, so the remaining text
         * can be laid out with the caller's break strategy (see [Context.build]).
         */
        private val cutAt: Int = when {
            blockedAt >= 0 -> blockedAt
            indented -> clearFrom
            else -> -1
        }

        /** Number of lines of a layout with [lineCount] lines that belong to this segment. */
        fun usableLines(lineCount: Int): Int = if (cutAt < 0) lineCount else minOf(lineCount, cutAt)

        fun sameIndents(other: IndentPlan, lines: Int): Boolean =
            (0 until lines).all { left.at(it) == other.left.at(it) && right.at(it) == other.right.at(it) }

        // StaticLayout repeats the last indent for every line past the end of the array.
        private fun IntArray.at(i: Int) = if (isEmpty()) 0 else this[minOf(i, size - 1)]
    }

    private class BuiltSegment(val layout: StaticLayout, val keep: Int)

    private class Context(
        val text: CharSequence,
        val paint: TextPaint,
        val config: FlowLayoutConfig,
        val left: Int,
        val right: Int,
        val obstacles: List<Obstacle>,
    ) {
        val width = right - left
        val nominalLineHeight: Int = max(
            1,
            (paint.getFontMetricsInt(null) * config.lineSpacingMultiplier + config.lineSpacingExtra).roundToInt(),
        )

        /** Gaps narrower than about one em are not worth filling with text. */
        private val minLineWidth: Int = ceil(paint.textSize).toInt().let { em -> if (width >= em) max(em, 1) else 1 }
        private val lowestObstacleBottom: Int = obstacles.maxOfOrNull { it.bottom } ?: Int.MIN_VALUE

        fun freeSpan(bandTop: Int, bandBottom: Int): FreeSpan? =
            FlowGeometry.freeSpan(left, right, bandTop, bandBottom, obstacles, minLineWidth)

        fun skipBlockedBand(bandTop: Int, bandBottom: Int): Int {
            val next = FlowGeometry.nextObstacleBottom(bandTop, bandBottom, obstacles)
            return if (next != null && next > bandTop) next else bandTop + nominalLineHeight
        }

        fun buildSegment(start: Int, y: Int): BuiltSegment {
            var plan = plan { i -> estimatedBand(y, i) }
            var layout = build(start, plan)
            repeat(MAX_REFINEMENTS) {
                val current = layout
                val keep = plan.usableLines(current.lineCount)
                val actual = plan { i -> actualBand(current, y, i) }
                if (actual.usableLines(current.lineCount) == keep && plan.sameIndents(actual, keep)) {
                    return BuiltSegment(current, keep)
                }
                plan = actual
                layout = build(start, plan)
            }
            // Did not converge (pathological oscillation): keep only lines both plans agree are usable.
            val final = layout
            val actual = plan { i -> actualBand(final, y, i) }
            return BuiltSegment(final, minOf(plan.usableLines(final.lineCount), actual.usableLines(final.lineCount)))
        }

        private fun estimatedBand(y: Int, line: Int): Pair<Int, Int> {
            val top = y + line * nominalLineHeight
            return top to top + nominalLineHeight
        }

        private fun actualBand(layout: StaticLayout, y: Int, line: Int): Pair<Int, Int> {
            if (line < layout.lineCount) return (y + layout.getLineTop(line)) to (y + layout.getLineBottom(line))
            val top = y + layout.getLineBottom(layout.lineCount - 1) + (line - layout.lineCount) * nominalLineHeight
            return top to top + nominalLineHeight
        }

        /** Computes indents line by line until the obstacles are passed or a band is fully blocked. */
        private inline fun plan(band: (Int) -> Pair<Int, Int>): IndentPlan {
            val lefts = ArrayList<Int>()
            val rights = ArrayList<Int>()
            var line = 0
            var blockedAt = -1
            var clearFrom = -1
            while (true) {
                val (bandTop, bandBottom) = band(line)
                if (bandTop >= lowestObstacleBottom) {
                    // StaticLayout repeats the last indent, so one zero covers all remaining lines.
                    lefts += 0
                    rights += 0
                    clearFrom = line
                    break
                }
                val span = freeSpan(bandTop, bandBottom)
                if (span == null) {
                    blockedAt = line
                    break
                }
                lefts += span.start - left
                rights += right - span.end
                line++
            }
            return IndentPlan(lefts.toIntArray(), rights.toIntArray(), blockedAt, clearFrom)
        }

        /**
         * Minikin's optimal line breaker (BREAK_STRATEGY_HIGH_QUALITY and BALANCED) picks breaks as if
         * every line had the first line's width, then applies the indents only when positioning, so
         * text overflows into obstacles. The greedy SIMPLE breaker honours per-line indents, so it
         * is used for lines beside obstacles; text below them gets the configured strategy.
         */
        private fun build(start: Int, plan: IndentPlan): StaticLayout {
            val breakStrategy = if (plan.indented) Layout.BREAK_STRATEGY_SIMPLE else config.breakStrategy
            val builder = StaticLayout.Builder.obtain(text, start, text.length, paint, width)
                .setAlignment(config.alignment)
                .setTextDirection(config.textDirection)
                .setLineSpacing(config.lineSpacingExtra, config.lineSpacingMultiplier)
                .setIncludePad(config.includeFontPadding)
                .setBreakStrategy(breakStrategy)
                .setHyphenationFrequency(config.hyphenationFrequency)
            if (plan.left.isNotEmpty()) builder.setIndents(plan.left, plan.right)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setJustificationMode(config.justificationMode)
            }
            return builder.build()
        }
    }
}
