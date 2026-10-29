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
                var search = new TravelSearch(ZERO, kind, config, new ShotHistory(), new Random(3));
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

    @Test void highFlightFramingLeavesRoomForFollowingLag() {
        Geometry range = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) { return a.distance(b) + 2 * margin <= 32; }
        };
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.HIGH_TRAIL);
        var moving = new MovingGeometry(range, ZERO, 0);
        moving.course(0);
        var search = new TravelSearch(ZERO, Scene.Kind.FLIGHT, config, new ShotHistory(), new Random(3));
        var plan = search.next(moving);
        assertNotNull(plan);
        assertTrue(moving.follow(new Vec(0, 0, 2), 1, false));
        assertNotNull(moving.apply(plan.shot().sample(0), plan.shot().focus(0), 0, 1, false),
            "Acceleration in open air must not immediately exhaust the sightline range");
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

    @Test void courseChangesEaseAcrossNorthAndReversalsWaitForCover() {
        var moving = new MovingGeometry(OPEN, ZERO, 0);
        moving.course(170);
        Pose local = new Pose(new Vec(0, 2, -10), 0, 0);
        Pose previous = moving.apply(local, ZERO, 0, 1, false);
        moving.course(-170);
        assertFalse(moving.needsReframe(), "Crossing north must take the short turn");
        for (long now = 20_000_000; now <= 3_000_000_000L; now += 20_000_000) {
            assertTrue(moving.follow(ZERO, now, false));
            Pose next = moving.apply(local, ZERO, 0, 1, false);
            assertTrue(next.position().distance(previous.position()) < .08, "The following view must turn gradually");
            previous = next;
        }
        assertEquals(-170, Math.IEEEremainder(previous.yaw(), 360), .1);
        moving.course(10);
        assertTrue(moving.needsReframe());
        assertFalse(moving.needsReframe(), "A reversal requests one cut, not a queue of cuts");
        assertTrue(moving.follow(ZERO, 3_020_000_000L, true));
        assertEquals(previous, moving.apply(local, ZERO, 0, 1, true));
        Pose covered = moving.apply(local, ZERO, 1, 1, false);
        assertEquals(previous.position(), covered.position());
        Pose destination = moving.apply(local, ZERO, 1, 2, false);
        assertTrue(destination.position().distance(covered.position()) > 19);
        assertEquals(10, destination.yaw(), .001);
        assertFalse(moving.needsReframe());
    }

    @Test void captureStillRequiresVisibilityOfTheMovingSubject() {
        Geometry hidden = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) { return b.x() < .5; }
        };
        var moving = new MovingGeometry(hidden, ZERO, 0);
        Pose local = new Pose(new Vec(0, 2, -10), 0, 0);
        assertNotNull(moving.apply(local, ZERO, 0, 1, false));
        assertTrue(moving.follow(new Vec(1, 0, 0), 100_000_000L, true));
        assertNull(moving.apply(local, ZERO, 0, 1, true));
    }
}
