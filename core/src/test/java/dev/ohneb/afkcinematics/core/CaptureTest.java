package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class CaptureTest {
    private boolean blocked;
    private final Geometry geometry = new Geometry() {
        public boolean clear(Vec from, Vec to, double margin) { return !blocked; }
        public boolean visible(Vec from, Vec target, double margin) { return !blocked; }
    };

    private CinematicSession moving() {
        var config = new CinematicConfig();
        config.subject = CinematicConfig.Subject.PLAYER;
        config.shots = EnumSet.of(Shot.Type.DRIFT);
        var session = new CinematicSession(new Vec(0, 1, 0), new Pose(new Vec(0, 1.6, 0), 0, 0), 0, config, geometry, new Random(5));
        session.tick(0);
        for (int i = 0; i <= 160; i++) session.frame(i * 50_000_000L);
        assertTrue(session.settled());
        return session;
    }

    @Test void slowNestedCaptureHoldsTheTakeAndResumesWithoutAJump() {
        var session = moving();
        Pose captured = session.current();
        session.beginCapture(8_000_000_000L);
        session.beginCapture(9_000_000_000L);
        session.tick(30_000_000_000L);
        assertEquals(captured, session.frame(30_000_000_000L));
        session.endCapture(30_000_000_000L);
        assertEquals(captured, session.frame(31_000_000_000L));
        session.endCapture(31_000_000_000L);
        assertEquals(captured, session.frame(31_000_000_000L));
        Pose resumed = session.frame(31_050_000_000L);
        assertNotNull(resumed);
        assertTrue(captured.position().distance(resumed.position()) < 0.1);
        assertNotEquals(captured.position(), resumed.position());
    }

    @Test void aCaptureDoesNotProtectAnObstructedView() {
        var session = moving();
        session.beginCapture(8_000_000_000L);
        blocked = true;
        assertNull(session.frame(12_000_000_000L));
        session.endCapture(13_000_000_000L);
        assertTrue(session.ended());
    }
}
