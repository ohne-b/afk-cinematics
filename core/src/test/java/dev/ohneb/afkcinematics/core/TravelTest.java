package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class TravelTest {
    private static final Vec ZERO = new Vec(0, 0, 0);
    private static final Geometry OPEN = new Geometry() {
        public boolean clear(Vec a, Vec b, double margin) { return true; }
        public boolean visible(Vec a, Vec b, double margin) { return true; }
    };

    @Test void travelViewsKeepTheirOffsetAndFindCloseFallbacks() {
        Geometry narrow = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return a.distance(ZERO) < 14 && b.distance(ZERO) < 14; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
        };
        for (var kind : new Scene.Kind[]{Scene.Kind.BOAT, Scene.Kind.FLIGHT}) {
            for (var type : Shot.Type.values()) {
                if (!type.travelOnly()) continue;
                var config = new CinematicConfig();
                config.shots = EnumSet.of(type);
                var search = new TravelSearch(ZERO, kind, 0, config, new ShotHistory(), new Random(3));
                ShotPlanner.Plan found = null;
                while (!search.exhausted() && found == null) found = search.next(narrow);
                assertNotNull(found, kind + " " + type);
                assertTrue(found.cut());
                assertEquals(0, found.shot().start().distance(found.shot().sample(10).position()), 1e-10);
                assertEquals(type, found.shot().type());
            }
        }
    }

    @Test void movingViewsSweepFromTheLastWorldPositionEvenUnderCover() {
        Geometry wall = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return Math.min(a.x(), b.x()) >= 0 || Math.max(a.x(), b.x()) < 0; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
        };
        var moving = new MovingGeometry(wall, ZERO, 0);
        Pose left = new Pose(new Vec(-1, 2, -3), 0, 0);
        assertNotNull(moving.apply(left, ZERO, 1, 1, false));
        assertTrue(moving.follow(new Vec(3, 0, 0), 100_000_000, false));
        assertNull(moving.apply(left, ZERO, 1, 1, false), "A black frame cannot excuse travel through a wall");
        assertNotNull(moving.apply(left, ZERO, 1, 2, false), "An explicit covered cut may change sides");
        assertFalse(moving.follow(new Vec(50, 0, 0), 200_000_000, false), "Covered cuts cannot excuse a teleport");
    }

    @Test void screenshotsHoldTheWorldPoseAndResumeSmoothly() {
        var moving = new MovingGeometry(OPEN, ZERO, 0);
        Pose local = new Pose(new Vec(3, 2, -7), 0, 0);
        Pose before = moving.apply(local, ZERO, 0, 1, false);
        assertTrue(moving.follow(new Vec(4, 0, 0), 100_000_000, true));
        assertEquals(before, moving.apply(local, ZERO, 0, 1, true));
        assertTrue(moving.follow(new Vec(4, 0, 0), 110_000_000, false));
        Pose resumed = moving.apply(local, ZERO, 0, 1, false);
        assertTrue(resumed.position().distance(before.position()) < 0.5);
        assertTrue(resumed.position().distance(before.position()) > 0);
    }
}
