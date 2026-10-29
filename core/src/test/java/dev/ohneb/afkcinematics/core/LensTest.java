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

    @Test void theLensEntersSmoothlyAndStaysFixedDuringATake() {
        var config = new CinematicConfig();
        config.subject = CinematicConfig.Subject.PLAYER;
        config.shots = EnumSet.of(Shot.Type.STILL);
        var session = session(config);
        session.tick(0);
        session.frame(0);
        assertEquals(100, config.lens.blend(100, 16.0 / 9, session.lensStrength()));
        double previous = 100;
        for (int tick = 1; tick <= 180; tick++) {
            session.tick(tick * 50_000_000L);
            session.frame(tick * 50_000_000L);
            double fov = config.lens.blend(100, 16.0 / 9, session.lensStrength());
            assertTrue(fov <= previous && fov >= 62);
            if (tick >= 120) assertEquals(62, fov, 0.00001);
            previous = fov;
        }
        assertEquals(100, CinematicConfig.Lens.GAME.blend(100, 0.5, 1));
        assertEquals(78, CinematicConfig.Lens.WIDE.blend(100, 16.0 / 9, 1), 0.00001);
        double tall = CinematicConfig.Lens.CINEMATIC.fieldOfView(100, 0.5);
        assertEquals(Math.tan(Math.toRadians(62) / 2), Math.tan(Math.toRadians(tall) / 2) * 0.5, 0.00001);
    }

    @Test void aSceneryLensChangesOnlyUnderCover() {
        var config = new CinematicConfig();
        config.subject = CinematicConfig.Subject.SCENERY;
        var session = session(config);
        double previous = 0;
        for (int tick = 0; tick < 120; tick++) {
            long now = tick * 50_000_000L;
            session.tick(now);
            session.frame(now);
            if (session.lensStrength() != previous) assertEquals(1, session.fade());
            previous = session.lensStrength();
            session.coveredFrame();
        }
        assertTrue(session.settled());
        assertEquals(1, session.lensStrength());
    }

    private static CinematicSession session(CinematicConfig config) {
        return new CinematicSession(new Vec(0, 1, 0), new Pose(new Vec(0, 1.6, 0), 0, 0),
            0, config, OPEN, new Random(4));
    }
}
