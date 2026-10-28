package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class CameraTest {
    private static final Vec SUBJECT = new Vec(0, 1, 0);
    private static final Pose ENTRY = new Pose(new Vec(0, 1.6, 0), 0, 0);
    private static final Geometry OPEN = new Geometry() {
        public boolean clear(Vec from, Vec to) { return true; }
        public boolean visible(Vec from, Vec target) { return true; }
    };

    @Test void turnsAcrossTheShortSideOfTheAngleBoundary() {
        Pose pose = new Pose(SUBJECT, 179, 0).blend(new Pose(SUBJECT, -179, 0), 0.5);
        assertEquals(180, pose.yaw(), 0.001);
        assertThrows(IllegalArgumentException.class, () -> new Vec(Double.NaN, 0, 0));
    }

    @Test void cameraPositionDoesNotDependOnFrameRate() {
        Pose slow = runAt(37), fast = runAt(144);
        assertEquals(slow.position().x(), fast.position().x(), 0.00001);
        assertEquals(slow.position().z(), fast.position().z(), 0.00001);
        assertEquals(slow.yaw(), fast.yaw(), 0.00001);
    }

    private Pose runAt(int rate) {
        var session = new CinematicSession(SUBJECT, ENTRY, 0, new CinematicConfig(), OPEN, new Random(42));
        session.tick(0);
        for (int i = 0; i <= rate * 8; i++) session.frame(Math.round(i * 1e9 / rate));
        assertFalse(session.ended());
        return session.current();
    }

    @Test void blockedPathsCannotBeAcceptedJustBecauseEndpointsAreClear() {
        Geometry wall = new Geometry() {
            public boolean clear(Vec from, Vec to) { return !(Math.min(from.x(), to.x()) < 0 && Math.max(from.x(), to.x()) >= 0); }
            public boolean visible(Vec from, Vec target) { return true; }
        };
        var shot = new Shot(Shot.Type.DRIFT, SUBJECT, new Vec(-2, 2, -4), new Vec(0, 2, -4), new Vec(2, 2, -4), 20);
        assertFalse(wall.valid(shot, shot.start()));
    }

    @Test void changedObstacleStopsAnAlreadyValidatedShot() {
        boolean[] blocked = {false};
        Geometry changing = new Geometry() {
            public boolean clear(Vec from, Vec to) { return !blocked[0]; }
            public boolean visible(Vec from, Vec target) { return true; }
        };
        var session = new CinematicSession(SUBJECT, ENTRY, 0, new CinematicConfig(), changing, new Random(1));
        session.tick(0);
        assertNotNull(session.frame(100_000_000));
        blocked[0] = true;
        assertNull(session.frame(110_000_000));
        assertTrue(session.ended());
    }

    @Test void noSpaceAndLongFrameGapsStopWithoutAnInvalidPose() {
        Geometry closed = new Geometry() {
            public boolean clear(Vec from, Vec to) { return false; }
            public boolean visible(Vec from, Vec target) { return false; }
        };
        var session = new CinematicSession(SUBJECT, ENTRY, 0, new CinematicConfig(), closed, new Random(1));
        for (int i = 0; i < 6; i++) session.tick(i * 50_000_000L);
        assertTrue(session.ended());
        assertFalse(session.showing());
        var open = new CinematicSession(SUBJECT, ENTRY, 0, new CinematicConfig(), OPEN, new Random(1));
        open.tick(0);
        assertNull(open.frame(2_000_000_000L));
        assertTrue(open.ended());
    }

    @Test void stillMotionDoesNotMoveAfterEntry() {
        var config = new CinematicConfig();
        config.motion = CinematicConfig.Motion.STILL;
        var search = new ShotSearch(SUBJECT, ENTRY, 0, 4, config.motion, null, true, new Random(4));
        Shot shot = search.next(OPEN);
        assertNotNull(shot);
        assertEquals(shot.sample(0), shot.sample(shot.duration()));
    }
}
