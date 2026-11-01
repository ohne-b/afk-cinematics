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
        }
        assertTrue(session.settled());
        assertEquals(2, accepted.size(), "Repeated presses queued more than one next shot");
    }

    @Test void searchingForANextShotKeepsMovingAndRevalidatesTheCurrentView() {
        var accepted = new ArrayList<Shot>();
        int[] rejected = {0};
        Geometry open = geometry(accepted, rejected);
        var session = session(open);
        for (int tick = 0; tick <= 140; tick++) {
            long now = tick * 50_000_000L;
            session.tick(now);
            session.frame(now);
        }
        Pose before = session.current();
        rejected[0] = 4;
        session.nextShot();
        for (int tick = 141; tick <= 142; tick++) {
            long now = tick * 50_000_000L;
            session.tick(now);
            Pose pose = session.frame(now);
            assertEquals(0, before.position().distance(pose.position()), 1e-10);
            double turn = Math.abs(Math.IEEEremainder(pose.yaw() - before.yaw(), 360));
            assertTrue(turn > 1e-6 && turn < .5, "Preparing the next take froze or jumped the panorama");
            assertTrue(Math.abs(pose.pitch() - before.pitch()) < .1, "Preparing the next take jumped its pitch");
            session.nextShot();
        }
        assertEquals(1, accepted.size());
        session.tick(7_150_000_000L);
        session.frame(7_150_000_000L);
        assertEquals(2, accepted.size());
        assertEquals(2, session.take());

        boolean[] occupied = {false};
        var blocked = new CinematicSession(SUBJECT, ENTRY, 0, config(), new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return !occupied[0]; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
        }, new Random(2));
        for (int tick = 0; tick <= 140; tick++) {
            blocked.tick(tick * 50_000_000L);
            blocked.frame(tick * 50_000_000L);
        }
        blocked.nextShot();
        occupied[0] = true;
        blocked.tick(7_050_000_000L);
        assertNull(blocked.frame(7_050_000_000L), "A continuing view must still react to new obstacles");
        assertTrue(blocked.ended());
    }

    @Test void entryCutsToTheValidatedPoseWithoutAnAimSweep() {
        var config = config();
        config.shots = EnumSet.of(Shot.Type.FLYOVER);
        var accepted = new ArrayList<Shot>();
        var session = new CinematicSession(SUBJECT, new Pose(ENTRY.position(), 0, -90), 0, config,
            geometry(accepted, new int[]{0}), new Random(2));
        session.tick(0);
        assertEquals(0, session.take());
        assertEquals(accepted.getFirst().sample(0), session.frame(0));
        assertTrue(session.settled());
    }

    @Test void repeatedNextRequestsPrepareOnlyOneReplacement() {
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
        int take = 0;
        int cuts = 0;
        for (int tick = 0; tick < 200; tick++) {
            long now = tick * 50_000_000L;
            session.tick(now);
            Pose pose = session.frame(now);
            if (pose == null) continue;
            if (session.take() != take) {
                cuts++;
                if (cuts == 1) { session.nextShot(); session.nextShot(); }
            }
            if (pose.position().distance(previous.position()) > 2) {
                assertEquals(take + 1, session.take());
            }
            previous = pose;
            take = session.take();
        }
        assertEquals(2, cuts);
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
