package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ShotControlTest {
    private static final Vec SUBJECT = new Vec(0, 1, 0);
    private static final Pose ENTRY = new Pose(new Vec(0, 1.6, 0), 0, 0);

    @Test void advancingDuringEntryWaitsAndRepeatedRequestsCoalesce() {
        var accepted = new ArrayList<Shot>();
        Geometry open = geometry(accepted, new int[]{0});
        var session = session(open);
        session.tick(0);
        session.nextShot();
        boolean entered = false;
        for (int tick = 0; tick < 200; tick++) {
            long now = tick * 50_000_000L;
            if (session.settled()) entered = true;
            if (!entered) {
                session.nextShot();
                assertEquals(1, accepted.size());
            }
            session.tick(now);
            assertNotNull(session.frame(now));
            session.coveredFrame();
        }
        assertTrue(session.settled());
        assertEquals(2, accepted.size(), "Repeated presses queued more than one next shot");
    }

    @Test void searchingForANextShotHoldsAndRevalidatesTheCurrentView() {
        var accepted = new ArrayList<Shot>();
        int[] rejected = {0};
        Geometry open = geometry(accepted, rejected);
        var session = session(open);
        for (int tick = 0; tick <= 100; tick++) {
            long now = tick * 50_000_000L;
            session.tick(now);
            session.frame(now);
        }
        Pose before = session.current();
        rejected[0] = 4;
        session.nextShot();
        for (int tick = 101; tick <= 103; tick++) {
            long now = tick * 50_000_000L;
            session.tick(now);
            Pose pose = session.frame(now);
            assertEquals(before.position(), pose.position());
            assertEquals(0, Math.IEEEremainder(pose.yaw() - before.yaw(), 360), 1e-9);
            assertEquals(before.pitch(), pose.pitch(), 1e-9);
            session.nextShot();
        }
        assertEquals(2, accepted.size());

        boolean[] occupied = {false};
        var blocked = new CinematicSession(SUBJECT, ENTRY, 0, config(), new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return !occupied[0]; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
        }, new Random(2));
        for (int tick = 0; tick <= 100; tick++) {
            blocked.tick(tick * 50_000_000L);
            blocked.frame(tick * 50_000_000L);
        }
        blocked.nextShot();
        occupied[0] = true;
        blocked.tick(5_050_000_000L);
        assertNull(blocked.frame(5_050_000_000L), "A held view must still react to new obstacles");
        assertTrue(blocked.ended());
    }

    @Test void aLargePitchChangeGetsTheSameGentleEntryAsALargeYawChange() {
        var config = config();
        config.shots = EnumSet.of(Shot.Type.OVERHEAD);
        var session = new CinematicSession(SUBJECT, new Pose(ENTRY.position(), 0, -90), 0, config,
            geometry(new ArrayList<>(), new int[]{0}), new Random(2));
        session.tick(0);
        for (int tick = 0; tick <= 60; tick++) assertNotNull(session.frame(tick * 50_000_000L));
        assertFalse(session.settled(), "A near-vertical turn completed too quickly");
        for (int tick = 61; tick <= 80; tick++) assertNotNull(session.frame(tick * 50_000_000L));
        assertTrue(session.settled());
    }

    @Test void advancingDuringACoveredCutKeepsBothCoveredFrames() {
        int[] probes = {0};
        var config = config();
        config.subject = CinematicConfig.Subject.SCENERY;
        config.shots = EnumSet.of(Shot.Type.STILL);
        var session = new CinematicSession(SUBJECT, ENTRY, 0, config, new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
            public Scene scenery(Vec anchor, Pose original, double bearing, int probe) {
                probes[0]++;
                return probe == 0 ? new Scene(new Vec(8, 1, 0), new Vec(8, 3, -8), Scene.Kind.LANDSCAPE, 1) : null;
            }
        }, new Random(2));
        Pose previous = ENTRY;
        double opacity = 0;
        int fades = 0;
        for (int tick = 0; tick < 200; tick++) {
            long now = tick * 50_000_000L;
            session.tick(now);
            Pose pose = session.frame(now);
            if (pose == null) continue;
            if (session.fade() == 1 && opacity < 1) {
                fades++;
                if (fades == 1) { session.nextShot(); session.nextShot(); }
            }
            if (pose.position().distance(previous.position()) > 2) {
                assertEquals(1, opacity);
                assertEquals(1, session.fade());
            }
            previous = pose;
            opacity = session.fade();
            session.coveredFrame();
        }
        assertEquals(2, fades);
        assertEquals(Scene.PROBES * 2, probes[0]);
        assertTrue(session.settled());
    }

    private static Geometry geometry(ArrayList<Shot> accepted, int[] rejected) {
        return new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
            public boolean valid(Shot shot, Vec entry) {
                if (rejected[0] > 0) { rejected[0]--; return false; }
                if (!accepted.contains(shot)) accepted.add(shot);
                return Geometry.super.valid(shot, entry);
            }
        };
    }

    private static CinematicConfig config() {
        var config = new CinematicConfig();
        config.subject = CinematicConfig.Subject.PLAYER;
        config.shots = EnumSet.of(Shot.Type.PAN);
        return config;
    }

    private static CinematicSession session(Geometry geometry) {
        return new CinematicSession(SUBJECT, ENTRY, 0, config(), geometry, new Random(2));
    }
}
