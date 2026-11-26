package uk.co.deanwild.flowtextview.internal

/** An axis-aligned rectangle in view pixels that text must not overlap. */
internal data class Obstacle(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** A horizontal run `[start, end)` that a line of text may occupy. */
internal data class FreeSpan(val start: Int, val end: Int) {
    val width: Int get() = end - start
}

/**
 * Pure geometry used by the layout engine: given the obstacles in the view, works out where a line
 * occupying a vertical band may be placed. Contains no Android dependencies so it can be unit tested
 * on the JVM.
 */
internal object FlowGeometry {

    /**
     * Returns the widest gap between [left] and [right] that no obstacle intersecting the band
     * `[bandTop, bandBottom)` covers, or `null` if every gap is narrower than [minWidth].
     *
     * Ties are broken in favour of the gap nearest [left].
     */
    fun freeSpan(
        left: Int,
        right: Int,
        bandTop: Int,
        bandBottom: Int,
        obstacles: List<Obstacle>,
        minWidth: Int = 1,
    ): FreeSpan? {
        if (right - left < minWidth) return null

        val blocked = obstacles
            .asSequence()
            .filter { it.intersectsBand(bandTop, bandBottom) }
            .map { maxOf(it.left, left) to minOf(it.right, right) }
            .filter { (start, end) -> end > start }
            .sortedBy { it.first }
            .toList()

        var best: FreeSpan? = null
        var cursor = left
        for ((start, end) in blocked) {
            if (start > cursor) best = widest(best, FreeSpan(cursor, start))
            cursor = maxOf(cursor, end)
        }
        if (right > cursor) best = widest(best, FreeSpan(cursor, right))

        return best?.takeIf { it.width >= minWidth }
    }

    /**
     * The smallest bottom edge, below [bandTop], of any obstacle intersecting the band. When a band
     * is fully blocked, this is the next y position at which the available space can change.
     * Returns `null` if no obstacle intersects the band.
     */
    fun nextObstacleBottom(bandTop: Int, bandBottom: Int, obstacles: List<Obstacle>): Int? =
        obstacles
            .filter { it.intersectsBand(bandTop, bandBottom) }
            .minOfOrNull { it.bottom }

    private fun Obstacle.intersectsBand(bandTop: Int, bandBottom: Int): Boolean =
        top < bandBottom && bottom > bandTop && right > left

    private fun widest(current: FreeSpan?, candidate: FreeSpan): FreeSpan =
        if (current == null || candidate.width > current.width) candidate else current
}
