package uk.co.deanwild.flowtextview.internal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FlowGeometryTest {

    private fun free(vararg obstacles: Obstacle, top: Int = 0, bottom: Int = 20, minWidth: Int = 1) =
        FlowGeometry.freeSpan(0, 100, top, bottom, obstacles.toList(), minWidth)

    @Test
    fun noObstaclesGivesFullWidth() {
        assertThat(free()).isEqualTo(FreeSpan(0, 100))
    }

    @Test
    fun obstacleOnLeftPushesTextRight() {
        assertThat(free(Obstacle(0, 0, 30, 50))).isEqualTo(FreeSpan(30, 100))
    }

    @Test
    fun obstacleOnRightPullsTextLeft() {
        assertThat(free(Obstacle(70, 0, 100, 50))).isEqualTo(FreeSpan(0, 70))
    }

    @Test
    fun obstaclesOnBothSidesLeaveTheMiddle() {
        assertThat(free(Obstacle(0, 0, 20, 50), Obstacle(80, 0, 100, 50))).isEqualTo(FreeSpan(20, 80))
    }

    @Test
    fun centralObstaclePicksTheWiderSide() {
        assertThat(free(Obstacle(40, 0, 50, 50))).isEqualTo(FreeSpan(50, 100))
        assertThat(free(Obstacle(55, 0, 65, 50))).isEqualTo(FreeSpan(0, 55))
    }

    @Test
    fun equalGapsPreferTheLeadingSide() {
        assertThat(free(Obstacle(45, 0, 55, 50))).isEqualTo(FreeSpan(0, 45))
    }

    @Test
    fun picksTheWidestGapAmongSeveralObstacles() {
        // The 2.x implementation compared against the last obstacle in the list rather than the
        // nearest one, so with three obstacles it could produce a "free" area that overlapped one.
        val result = free(Obstacle(10, 0, 20, 50), Obstacle(60, 0, 95, 50), Obstacle(30, 0, 35, 50))
        assertThat(result).isEqualTo(FreeSpan(35, 60))
    }

    @Test
    fun overlappingAndNestedObstaclesAreMerged() {
        val result = free(Obstacle(0, 0, 40, 50), Obstacle(30, 0, 60, 50), Obstacle(35, 0, 45, 50))
        assertThat(result).isEqualTo(FreeSpan(60, 100))
    }

    @Test
    fun obstaclesOutsideTheBandAreIgnored() {
        assertThat(free(Obstacle(0, 30, 50, 60), top = 0, bottom = 20)).isEqualTo(FreeSpan(0, 100))
        assertThat(free(Obstacle(0, 0, 50, 10), top = 20, bottom = 40)).isEqualTo(FreeSpan(0, 100))
    }

    @Test
    fun touchingEdgesDoNotIntersect() {
        assertThat(free(Obstacle(0, 20, 50, 40), top = 0, bottom = 20)).isEqualTo(FreeSpan(0, 100))
        assertThat(free(Obstacle(0, 0, 50, 20), top = 20, bottom = 40)).isEqualTo(FreeSpan(0, 100))
    }

    @Test
    fun partialVerticalOverlapStillBlocks() {
        assertThat(free(Obstacle(0, 15, 50, 60), top = 0, bottom = 20)).isEqualTo(FreeSpan(50, 100))
    }

    @Test
    fun obstaclesExtendingBeyondContentAreClipped() {
        assertThat(free(Obstacle(-50, 0, 25, 50))).isEqualTo(FreeSpan(25, 100))
        assertThat(free(Obstacle(90, 0, 500, 50))).isEqualTo(FreeSpan(0, 90))
    }

    @Test
    fun fullWidthObstacleBlocksTheBand() {
        assertThat(free(Obstacle(0, 0, 100, 50))).isNull()
    }

    @Test
    fun gapsNarrowerThanMinWidthBlockTheBand() {
        assertThat(free(Obstacle(0, 0, 45, 50), Obstacle(55, 0, 100, 50), minWidth = 20)).isNull()
        assertThat(free(Obstacle(0, 0, 45, 50), Obstacle(55, 0, 100, 50), minWidth = 10))
            .isEqualTo(FreeSpan(45, 55))
    }

    @Test
    fun contentNarrowerThanMinWidthIsBlocked() {
        assertThat(FlowGeometry.freeSpan(0, 5, 0, 20, emptyList(), minWidth = 10)).isNull()
    }

    @Test
    fun emptyObstaclesAreIgnored() {
        assertThat(free(Obstacle(50, 0, 50, 50))).isEqualTo(FreeSpan(0, 100))
    }

    @Test
    fun nextObstacleBottomIsTheNearestBottomInTheBand() {
        val obstacles = listOf(Obstacle(0, 0, 100, 80), Obstacle(0, 0, 50, 40), Obstacle(0, 200, 100, 300))
        assertThat(FlowGeometry.nextObstacleBottom(10, 30, obstacles)).isEqualTo(40)
        assertThat(FlowGeometry.nextObstacleBottom(100, 120, obstacles)).isNull()
    }
}
