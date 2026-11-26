package uk.co.deanwild.flowtextview.internal

import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.TextDirectionHeuristics
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlowLayoutEngineTest {

    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 32f }
    private val lorem = (
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. In sed massa diam. Integer leo " +
            "tortor, sagittis eget blandit et, malesuada id odio. Proin gravida dui imperdiet, tempor " +
            "augue eu, euismod odio. Vivamus nec euismod neque. Nam sed turpis ac arcu consectetur " +
            "tristique sed et odio. Vivamus posuere aliquet mi vitae bibendum. Suspendisse potenti."
        ).repeat(3)

    private fun layout(
        text: CharSequence,
        obstacles: List<Obstacle> = emptyList(),
        left: Int = 0,
        top: Int = 0,
        right: Int = 600,
        config: FlowLayoutConfig = FlowLayoutConfig(),
    ) = FlowLayoutEngine.layout(text, paint, config, left, top, right, obstacles)

    @Test
    fun emptyTextProducesNothing() {
        val result = layout("", top = 12)
        assertThat(result.segments).isEmpty()
        assertThat(result.bottom).isEqualTo(12)
    }

    @Test
    fun zeroWidthProducesNothing() {
        val result = layout(lorem, right = 0)
        assertThat(result.segments).isEmpty()
    }

    @Test
    fun withoutObstaclesTextIsASingleFullWidthLayout() {
        val result = layout(lorem, top = 10)
        assertThat(result.segments).hasSize(1)
        val segment = result.segments.single()
        assertThat(segment.layout.width).isEqualTo(600)
        assertThat(segment.top).isEqualTo(10)
        assertThat(result.bottom).isEqualTo(10 + segment.layout.height)
        result.assertCovers(lorem)
    }

    @Test
    fun textFlowsBesideALeftObstacleAndReturnsBelowIt() {
        val obstacle = Obstacle(0, 0, 200, 150)
        val result = layout(lorem, listOf(obstacle))

        result.assertAvoids(obstacle)
        result.assertCovers(lorem)
        val lines = result.lines()
        assertThat(lines.first().left).isAtLeast(200f)
        assertThat(lines.last().left).isLessThan(200f) // back to full width below the obstacle
    }

    @Test
    fun textFlowsBesideARightObstacle() {
        val obstacle = Obstacle(400, 100, 600, 300)
        val result = layout(lorem, listOf(obstacle))

        result.assertAvoids(obstacle)
        result.assertCovers(lorem)
        assertThat(result.lines().filter { it.intersectsBand(obstacle) }).isNotEmpty()
    }

    @Test
    fun textFitsBetweenObstaclesOnBothSides() {
        val obstacles = listOf(Obstacle(0, 0, 150, 200), Obstacle(450, 0, 600, 200))
        val result = layout(lorem, obstacles)

        obstacles.forEach { result.assertAvoids(it) }
        result.assertCovers(lorem)
    }

    @Test
    fun contentInsetsAreRespected() {
        val result = layout(lorem, left = 40, right = 560, top = 20)
        result.lines().forEach {
            assertThat(it.left).isAtLeast(40f)
            assertThat(it.right).isAtMost(560f)
            assertThat(it.top).isAtLeast(20)
        }
    }

    @Test
    fun fullWidthObstacleSplitsTextIntoSegments() {
        val banner = Obstacle(0, 100, 600, 220)
        val result = layout(lorem, listOf(banner))

        assertThat(result.segments.size).isAtLeast(2)
        assertThat(result.segments[1].top).isAtLeast(220)
        result.assertAvoids(banner)
        result.assertCovers(lorem)
    }

    @Test
    fun obstacleAtTheTopPushesAllTextBelowIt() {
        val header = Obstacle(0, 0, 600, 90)
        val result = layout(lorem, listOf(header))

        assertThat(result.segments.first().top).isAtLeast(90)
        result.assertCovers(lorem)
    }

    @Test
    fun gapsNarrowerThanOneEmAreSkipped() {
        val obstacles = listOf(Obstacle(0, 0, 290, 200), Obstacle(310, 0, 600, 200))
        val result = layout(lorem, obstacles)

        assertThat(result.segments.first().top).isAtLeast(200)
        result.assertCovers(lorem)
    }

    @Test
    fun viewNarrowerThanOneCharacterStillTerminates() {
        // The 2.x engine looped forever here because breakText kept returning zero.
        val result = layout("Hello", right = 5)
        result.assertCovers("Hello")
    }

    @Test
    fun everyCharacterIsLaidOutExactlyOnce() {
        // The 2.x engine dropped single-character chunks and the last character of a span that
        // reached the end of the text.
        listOf("a", "x\ny", "a\n\nb", "a\n\n\nb", "\nleading", "trailing\n", "word ".repeat(200) + "z")
            .forEach { text ->
                layout(text, listOf(Obstacle(0, 0, 300, 100))).assertCovers(text)
            }
    }

    @Test
    fun spansStayAttachedToTheirCharactersAcrossNewlines() {
        // The 2.x engine miscounted newlines, so styling drifted onto neighbouring characters.
        val text = SpannableStringBuilder()
        listOf("one", "\n", "two", "\n\n", "three", "\n\n\n", "four").forEach { part ->
            val start = text.length
            text.append(part)
            if (part.isNotBlank()) text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val result = layout(text, listOf(Obstacle(0, 0, 100, 60)))

        result.segments.forEach { assertThat(it.layout.text).isSameInstanceAs(text) }
        val bold = text.getSpans(0, text.length, StyleSpan::class.java).map { text.subSequence(text.getSpanStart(it), text.getSpanEnd(it)).toString() }
        assertThat(bold).containsExactly("one", "two", "three", "four").inOrder()
        result.assertCovers(text)
    }

    @Test
    fun mixedLineHeightsStillAvoidObstacles() {
        val text = SpannableStringBuilder(lorem).apply {
            setSpan(RelativeSizeSpan(2.5f), 30, 60, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(3f), 200, 230, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val obstacles = listOf(Obstacle(0, 0, 220, 180), Obstacle(380, 260, 600, 420))
        val result = layout(text, obstacles)

        obstacles.forEach { result.assertAvoids(it) }
        result.assertCovers(text)
    }

    @Test
    fun everyBreakStrategyAvoidsObstacles() {
        // Regression: with per-line indents, Minikin's optimal breaker chose breaks for the full
        // width and text overflowed into obstacles.
        val obstacles = listOf(Obstacle(0, 0, 220, 180), Obstacle(380, 100, 600, 300))
        listOf(Layout.BREAK_STRATEGY_SIMPLE, Layout.BREAK_STRATEGY_HIGH_QUALITY, Layout.BREAK_STRATEGY_BALANCED)
            .forEach { strategy ->
                val result = layout(lorem, obstacles, config = FlowLayoutConfig(breakStrategy = strategy))
                obstacles.forEach { result.assertAvoids(it) }
                result.assertCovers(lorem)
            }
    }

    @Test
    fun textBesideAndBelowAnObstacleAreSeparateSegments() {
        val result = layout(lorem, listOf(Obstacle(0, 0, 200, 100)))

        assertThat(result.segments).hasSize(2)
        assertThat(result.segments[1].top).isAtLeast(100)
        assertThat(result.segments[1].layout.lineCount).isEqualTo(result.segments[1].lineCount)
    }

    @Test
    fun linePitchIsEvenAcrossSegmentBoundaries() {
        listOf(true, false).forEach { includePad ->
            listOf(1f, 1.15f).forEach { mult ->
                val config = FlowLayoutConfig(includeFontPadding = includePad, lineSpacingMultiplier = mult)
                val result = layout(lorem, listOf(Obstacle(0, 0, 200, 100), Obstacle(400, 300, 600, 420)), config = config)
                assertThat(result.segments.size).isAtLeast(2)

                val baselines = result.segments.flatMap { segment ->
                    (0 until segment.lineCount).map { segment.top + segment.layout.getLineBaseline(it) }
                }
                val pitches = baselines.zipWithNext { a, b -> b - a }.distinct()
                assertWithMessage("includePad=$includePad mult=$mult pitches=$pitches")
                    .that(pitches.max() - pitches.min()).isAtMost(1)
            }
        }
    }

    @Test
    fun lineSpacingIncreasesHeight() {
        val normal = layout(lorem).bottom
        val spaced = layout(lorem, config = FlowLayoutConfig(lineSpacingMultiplier = 1.5f)).bottom
        assertThat(spaced).isGreaterThan(normal)
    }

    @Test
    fun rightToLeftTextAvoidsObstacles() {
        val hebrew = "שלום עולם זהו טקסט לדוגמה שזורם סביב תמונות ".repeat(12)
        val obstacle = Obstacle(400, 0, 600, 200)
        val result = layout(hebrew, listOf(obstacle))

        result.assertAvoids(obstacle)
        result.assertCovers(hebrew)
    }

    @Test
    fun justifiedTextAvoidsObstaclesInBothDirections() {
        val hebrew = "שלום עולם זהו טקסט לדוגמה שזורם סביב תמונות ".repeat(12)
        val obstacles = listOf(Obstacle(0, 0, 200, 150), Obstacle(420, 200, 600, 360))
        listOf(lorem to TextDirectionHeuristics.FIRSTSTRONG_LTR, hebrew to TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .forEach { (text, direction) ->
                val config = FlowLayoutConfig(justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD, textDirection = direction)
                val result = layout(text, obstacles, config = config)
                // Character bounds ignore justification (and the spaces it may shrink), so check
                // that each segment's box, which justified lines fill exactly, avoids the obstacles.
                obstacles.forEach { result.assertBoxesAvoid(it) }
                result.assertCovers(text)
            }
    }

    @Test
    fun segmentsResumingMidParagraphKeepTheParagraphsDirection() {
        val text = "שלום עולם FlowTextView\nEnglish paragraph"
        val resume = text.indexOf("FlowTextView")

        val choice = FlowLayoutEngine.DirectionChoice.at(text, resume, TextDirectionHeuristics.FIRSTSTRONG_LTR)
        assertThat(choice.heuristic).isEqualTo(TextDirectionHeuristics.RTL)
        assertThat(choice.paragraphEnd).isEqualTo(text.indexOf('\n'))

        val atParagraphStart = FlowLayoutEngine.DirectionChoice.at(text, text.indexOf("English"), TextDirectionHeuristics.FIRSTSTRONG_LTR)
        assertThat(atParagraphStart.heuristic).isEqualTo(TextDirectionHeuristics.FIRSTSTRONG_LTR)
        assertThat(atParagraphStart.paragraphEnd).isEqualTo(-1)

        val sameDirection = FlowLayoutEngine.DirectionChoice.at(text, text.indexOf("עולם"), TextDirectionHeuristics.FIRSTSTRONG_LTR)
        assertThat(sameDirection.heuristic).isEqualTo(TextDirectionHeuristics.FIRSTSTRONG_LTR)
    }

    @Test
    fun segmentsBesideObstaclesOnlyLayOutTheTextTheyNeed() {
        val paragraphs = (1..300).joinToString("\n") { "Paragraph $it. $lorem" }
        val result = layout(paragraphs, listOf(Obstacle(0, 0, 200, 150)))

        val beside = result.segments.first()
        assertThat(beside.layout.getLineEnd(beside.layout.lineCount - 1)).isLessThan(paragraphs.length / 10)
        result.assertCovers(paragraphs)
    }

    // region helpers

    private data class DrawnLine(val top: Int, val bottom: Int, val left: Float, val right: Float) {
        fun intersectsBand(o: Obstacle) = top < o.bottom && bottom > o.top
    }

    /** Every drawn line, in view coordinates, measured by its visible (non-whitespace) glyphs. */
    private fun FlowLayoutResult.lines(): List<DrawnLine> = segments.flatMap { segment ->
        val layout = segment.layout
        (0 until segment.lineCount).mapNotNull { line ->
            val text = layout.text
            val start = layout.getLineStart(line)
            var end = layout.getLineEnd(line)
            while (end > start && text[end - 1].isWhitespace()) end--
            if (end == start) return@mapNotNull null
            // Per-character bounds resolve bidi runs and per-line indents correctly (API 34+;
            // Robolectric runs these tests on the latest SDK).
            val bounds = FloatArray((end - start) * 4)
            layout.fillCharacterBounds(start, end, bounds, 0)
            val glyphs = (0 until end - start).filterNot { text[start + it].isWhitespace() }
            DrawnLine(
                top = segment.top + layout.getLineTop(line),
                bottom = segment.top + layout.getLineBottom(line),
                left = segment.left + glyphs.minOf { bounds[it * 4] },
                right = segment.left + glyphs.maxOf { bounds[it * 4 + 2] },
            )
        }
    }

    /** Asserts no segment's box (its width across the band of its lines) overlaps [obstacle]. */
    private fun FlowLayoutResult.assertBoxesAvoid(obstacle: Obstacle) {
        segments.forEach { segment ->
            val boxRight = segment.left + segment.layout.width
            for (line in 0 until segment.lineCount) {
                val top = segment.top + segment.layout.getLineTop(line)
                val bottom = segment.top + segment.layout.getLineBottom(line)
                val overlaps = top < obstacle.bottom && bottom > obstacle.top &&
                    segment.left < obstacle.right && boxRight > obstacle.left
                assertWithMessage("segment at (${segment.left}, $top) width ${segment.layout.width} overlaps $obstacle").that(overlaps).isFalse()
            }
        }
    }

    private fun FlowLayoutResult.assertAvoids(obstacle: Obstacle) {
        assertBoxesAvoid(obstacle)
        lines().filter { it.intersectsBand(obstacle) }.forEach { line ->
            val overlaps = line.left < obstacle.right - 1 && line.right > obstacle.left + 1
            assertWithMessage("line $line overlaps $obstacle").that(overlaps).isFalse()
        }
    }

    /** Asserts the segments' lines, concatenated, reproduce [text] with nothing dropped or repeated. */
    private fun FlowLayoutResult.assertCovers(text: CharSequence) {
        val rebuilt = StringBuilder()
        segments.forEach { segment ->
            for (line in 0 until segment.lineCount) {
                rebuilt.append(text, segment.layout.getLineStart(line), segment.layout.getLineEnd(line))
            }
        }
        assertThat(rebuilt.toString()).isEqualTo(text.toString())
        segments.zipWithNext().forEach { (a, b) ->
            assertThat(b.start).isEqualTo(a.end)
            assertThat(b.top).isAtLeast(a.bottom)
        }
    }

    // endregion
}
