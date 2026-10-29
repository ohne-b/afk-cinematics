package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ViewQualityTest {
    @Test void prefersLayeredViewsToCloseWallsAndGround() {
        double layered = score(32, 32, 20, 8, 18, 24, 4, 7, 10);
        assertTrue(layered > score(6, 6, 6, 6, 6, 6, 6, 6, 6));
        assertTrue(layered > score(32, 32, 32, 32, 32, 32, 32, 32, 32));
        assertTrue(layered > score(3, 4, 3, 2, 4, 2, 1, 2, 1));
        assertEquals(0, score(1, 2, 1, 1, 1, 2, 1, 1, 1));
    }

    @Test void invalidSamplesDoNotBecomeAnInfiniteScore() {
        assertTrue(Double.isFinite(score(32, 32, 32, Double.NaN, 12, Double.POSITIVE_INFINITY, 4, 4, 4)));
        assertEquals(score(32, 32, 32, 32, 32, 32, 6, 6, 6), score(64, 64, 64, 64, 64, 64, 6, 6, 6));
    }

    @Test void raysFollowTheCameraAndVisibleFrame() {
        var pose = new Pose(new Vec(4, 7, 9), 83, -25);
        Vec[] wide = ViewQuality.directions(pose, 65, 21.0 / 9, false);
        Vec[] narrow = ViewQuality.directions(pose, 65, 0.5, true);
        assertTrue(wide[4].distance(pose.lookAhead(1).subtract(pose.position())) < 1e-12);
        for (Vec ray : wide) assertEquals(1, ray.length(), 1e-12);
        assertTrue(wide[3].distance(wide[5]) > narrow[3].distance(narrow[5]));
        assertTrue(wide[1].distance(wide[7]) > narrow[1].distance(narrow[7]));
    }

    @Test void unloadedTerrainNeverCountsAsDistantScenery() {
        assertEquals(0, score(-1, -1, -1, 8, 8, 8, 4, 4, 4));
        assertEquals(0, score(32, 32, 32, 12, -1, 12, 6, 6, 6));
        assertTrue(score(32, 32, 32, 12, 12, 12, 6, 6, 6)
            > score(-1, 32, -1, 12, 12, 12, 6, 6, 6));
    }

    @Test void downwardSurfaceViewsHaveALowerPreferenceWithoutExcludingCaves() {
        double[] depth = {16, 14, 12, 10, 8, 8, 5, 5, 5};
        assertTrue(ViewQuality.score(depth, 10, true) > ViewQuality.score(depth, 55, true));
        assertEquals(ViewQuality.score(depth, 10, false), ViewQuality.score(depth, 55, false));
        assertTrue(ViewQuality.score(depth, 55, false) > 0);
    }

    private static double score(double... depth) { return ViewQuality.score(depth, 10, true); }
}
