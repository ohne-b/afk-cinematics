package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class SleepTest {
    private static final Vec BED = new Vec(0, .8, 0);
    private static final Geometry OPEN = new Geometry() {
        public boolean clear(Vec a, Vec b, double margin) { return a.y() > margin && b.y() > margin; }
        public boolean visible(Vec a, Vec b, double margin) { return true; }
    };

    @Test void openingFitsBriefSleepRegardlessOfAfkSubjectAndStillDoesNotMove() {
        var config = new CinematicConfig();
        config.subject = CinematicConfig.Subject.SCENERY;
        for (var motion : CinematicConfig.Motion.values()) {
            config.motion = motion;
            var session = new CinematicSession(BED, new Pose(BED, 0, 0), 90, config, OPEN, new Random(1), Scene.Kind.SLEEP);
            session.tick(0);
            Pose start = session.frame(0);
            assertNotNull(start);
            assertFalse(session.scenery());
            for (long now = 50_000_000; now <= 500_000_000; now += 50_000_000) session.frame(now);
            double distance = start.position().distance(session.current().position());
            if (motion == CinematicConfig.Motion.STILL) assertEquals(0, distance);
            else assertTrue(distance > 0 && distance < .1);
        }
    }

    @Test void blockedMovementFallsBackToEnabledStillWithinABoundedSearch() {
        Geometry cramped = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return a.equals(b) && margin == 0; }
            public boolean visible(Vec a, Vec b, double margin) { return true; }
        };
        var config = new CinematicConfig();
        var plan = find(new SleepSearch(BED, 0, config, new ShotHistory(), new Random(1)), cramped);
        assertNotNull(plan);
        assertEquals(Shot.Type.STILL, plan.shot().type());
        config.shots = EnumSet.of(Shot.Type.DOLLY);
        assertNull(find(new SleepSearch(BED, 0, config, new ShotHistory(), new Random(1)), cramped));
        config.shots = EnumSet.of(Shot.Type.TRAIL);
        assertNull(find(new SleepSearch(BED, 0, config, new ShotHistory(), new Random(1)), OPEN));
    }

    @Test void longerPaceDoesNotSpeedUpTheCamera() {
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.DOLLY);
        var history = new ShotHistory();
        var first = find(new SleepSearch(BED, 0, config, history, new Random(1)), OPEN);
        history.add(first.scene(), first.shot());
        config.pace = CinematicConfig.Pace.QUICK;
        var quick = find(new SleepSearch(BED, 0, config, history, new Random(1)), OPEN).shot();
        config.pace = CinematicConfig.Pace.LONG;
        var slow = find(new SleepSearch(BED, 0, config, history, new Random(1)), OPEN).shot();
        assertTrue(quick.duration() < slow.duration());
        assertTrue(quick.sample(3).position().distance(slow.sample(3).position()) < 1e-9);
    }

    @Test void captureStillValidatesTheWholeSleepingBody() {
        boolean[] blocked = {false};
        Geometry changing = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) { return !blocked[0] || b.z() < .6; }
        };
        var session = new CinematicSession(BED, new Pose(BED, 0, 0), 0, new CinematicConfig(), changing, new Random(1), Scene.Kind.SLEEP);
        session.tick(0);
        assertNotNull(session.frame(0));
        session.beginCapture(1);
        blocked[0] = true;
        assertNull(session.frame(2));
        assertTrue(session.ended());
    }

    private static ShotPlanner.Plan find(SleepSearch search, Geometry geometry) {
        int attempts = 0;
        while (!search.exhausted()) {
            assertTrue(++attempts <= 18);
            var plan = search.next(geometry);
            if (plan != null) return plan;
        }
        return null;
    }
}
