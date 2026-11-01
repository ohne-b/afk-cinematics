package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class LensTest {
    private static final Geometry OPEN = new Geometry() {
        public boolean clear(Vec from, Vec to, double margin) { return true; }
        public boolean visible(Vec from, Vec to, double margin) { return true; }
        public Scene scenery(Vec anchor, Pose original, double bearing, int probe) {
            return probe == 0 ? new Scene(new Vec(8, 2, 0), new Vec(8, 4, -8), Scene.Kind.LANDSCAPE, 5) : null;
        }
    };

    @Test void theLensCutsWithTheCameraAndStaysFixedDuringATake() {
        var config = new CinematicConfig();
        assertEquals(100, config.fieldOfView(100));
        config.useGameFov = false;
        config.customFov = 140;
        config.subject = CinematicConfig.Subject.PLAYER;
        config.shots = EnumSet.of(Shot.Type.STILL);
        var session = session(config);
        session.tick(0);
        session.frame(0);
        assertEquals(1, session.lensStrength());
        for (int tick = 1; tick <= 180; tick++) {
            session.tick(tick * 50_000_000L);
            session.frame(tick * 50_000_000L);
            assertEquals(1, session.lensStrength());
            assertEquals(140, config.fieldOfView(100));
        }
        for (double aspect : new double[]{.5, 16.0 / 9, 32.0 / 9}) {
            double halfHeight = .05 * Math.tan(Math.toRadians(140) / 2);
            var corner = new Vec(halfHeight * aspect, halfHeight, .05);
            assertTrue(config.cameraRadius(aspect) > corner.length(), "The wider near plane escaped camera clearance");
        }
        config.useGameFov = true;
        assertEquals(100, config.fieldOfView(100));
        assertEquals(75, config.fieldOfView(75));
    }

    @Test void aSceneryLensChangesOnlyWithAnAppliedTake() {
        var config = new CinematicConfig();
        config.subject = CinematicConfig.Subject.SCENERY;
        var session = session(config);
        double previous = 0;
        for (int tick = 0; tick < 120; tick++) {
            long now = tick * 50_000_000L;
            session.tick(now);
            session.frame(now);
            if (session.lensStrength() != previous) assertEquals(1, session.take());
            previous = session.lensStrength();
        }
        assertTrue(session.settled());
        assertEquals(1, session.lensStrength());
    }

    private static CinematicSession session(CinematicConfig config) {
        return new CinematicSession(new Vec(0, 1, 0), new Pose(new Vec(0, 1.6, 0), 0, 0),
            0, config, OPEN, new Random(4));
    }
}
