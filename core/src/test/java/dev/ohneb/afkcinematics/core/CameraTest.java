package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.Random;
import java.util.EnumSet;
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
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.ARC);
        var session = new CinematicSession(SUBJECT, ENTRY, 0, config, OPEN, new Random(42));
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
        var shot = new Shot(Shot.Type.DRIFT, new Vec(-2, 2, -4), new Vec(0, 2, -4), new Vec(2, 2, -4), SUBJECT, SUBJECT, 20);
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

    @Test void lookingDownDoesNotRejectAWideEntryAboveFlatGround() {
        Geometry ground = new Geometry() {
            public boolean clear(Vec from, Vec to) { return from.y() > 0.25 && to.y() > 0.25; }
            public boolean visible(Vec from, Vec target) { return from.y() > 0 && target.y() > 0; }
        };
        var config = new CinematicConfig();
        config.distance = CinematicConfig.Distance.WIDE;
        config.shots = EnumSet.of(Shot.Type.STILL);
        var session = new CinematicSession(SUBJECT, new Pose(ENTRY.position(), 0, 90), 0, config, ground, new Random(2));
        session.tick(0);
        for (int tick = 0; tick < 100; tick++) {
            assertNotNull(session.frame(tick * 50_000_000L), "A downward entry heading must not project the subject below the floor");
        }
        assertFalse(session.ended());
    }

    @Test void noSpaceAndLongFrameGapsStopWithoutAnInvalidPose() {
        Geometry closed = new Geometry() {
            public boolean clear(Vec from, Vec to) { return false; }
            public boolean visible(Vec from, Vec target) { return false; }
        };
        var session = new CinematicSession(SUBJECT, ENTRY, 0, new CinematicConfig(), closed, new Random(1));
        for (int i = 0; i < 70; i++) session.tick(i * 50_000_000L);
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
        var search = new ShotSearch(new Scene(SUBJECT, ENTRY.position(), false, 0), ENTRY, 0, config, null, true, false, new Random(4));
        Shot shot = search.next(OPEN);
        assertNotNull(shot);
        assertEquals(shot.sample(0), shot.sample(shot.duration()));
    }

    @Test void successiveShotsStayWithinTheLocalCameraRange() {
        var config = new CinematicConfig();
        config.distance = CinematicConfig.Distance.WIDE;
        for (int seed = 0; seed < 3; seed++) {
            var session = new CinematicSession(SUBJECT, ENTRY, 0, config, OPEN, new Random(seed));
            for (int tick = 0; tick < 12000; tick++) {
                long now = tick * 50_000_000L;
                session.tick(now);
                Pose pose = session.frame(now);
                assertNotNull(pose);
                Vec offset = pose.position().subtract(SUBJECT);
                assertTrue(Math.hypot(offset.x(), offset.z()) <= 6.000001, "Successive shots drifted beyond six blocks");
                assertTrue(offset.y() >= -0.300001 && offset.y() <= 5.500001, "Successive shots drifted vertically");
            }
        }
    }
}
