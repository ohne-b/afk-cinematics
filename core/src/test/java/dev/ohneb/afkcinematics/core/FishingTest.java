package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class FishingTest {
    private static final Vec PLAYER = new Vec(0, 1, 0), BOBBER = new Vec(0, .1, 8);
    private static final Geometry OPEN = new Geometry() {
        public boolean clear(Vec a, Vec b, double margin) { return a.y() >= margin && b.y() >= margin; }
        public boolean visible(Vec a, Vec b, double margin) { return true; }
    };

    @Test void fishingStylesMoveGentlyAndStillRemainsFixed() {
        var config = new CinematicConfig();
        for (Shot.Type type : new Shot.Type[]{Shot.Type.ANGLER, Shot.Type.CAST_LINE, Shot.Type.FLOAT}) {
            config.shots = EnumSet.of(type);
            var plan = find(new FishingSearch(PLAYER, BOBBER, config, new ShotHistory(), new Random(1)), OPEN);
            assertNotNull(plan, type.name());
            assertTrue(plan.cut());
            assertEquals(type, plan.shot().type());
            assertTrue(plan.shot().start().distance(plan.shot().end()) > .5);
        }
        config.shots = EnumSet.of(Shot.Type.STILL);
        var still = find(new FishingSearch(PLAYER, BOBBER, config, new ShotHistory(), new Random(1)), OPEN).shot();
        assertEquals(still.start(), still.end());
    }

    @Test void aClearCompositionCannotHideAnObstructedBobber() {
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.ANGLER);
        Geometry obscured = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) { return b.distance(BOBBER) > 1; }
        };
        var search = new FishingSearch(PLAYER, BOBBER, config, new ShotHistory(), new Random(1));
        while (!search.exhausted()) assertNull(search.next(obscured));
    }

    @Test void liveFocusIsValidatedWhileCaptureKeepsItsAppliedComposition() {
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.ANGLER);
        var blocked = new java.util.concurrent.atomic.AtomicReference<Vec>();
        Geometry geometry = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) {
                return blocked.get() == null || b.distance(blocked.get()) > .01;
            }
        };
        var session = new CinematicSession(PLAYER, new Pose(PLAYER, 0, 0), 0, config,
            geometry, new Random(1), Scene.Kind.FISHING, BOBBER);
        for (long now = 0; now <= 3_000_000_000L; now += 50_000_000L) {
            session.tick(now);
            session.frame(now);
            session.coveredFrame();
        }
        assertTrue(session.settled());
        blocked.set(session.focus());
        session.fishing(BOBBER.add(new Vec(2, 0, 0)));
        assertNotNull(session.frame(3_050_000_000L), "The obsolete focus must not veto the current composition");
        assertTrue(session.focus().distance(blocked.get()) > .5);
        Vec captured = session.focus();
        session.beginCapture(3_100_000_000L);
        session.fishing(BOBBER.add(new Vec(3, 0, 0)));
        assertNotNull(session.frame(3_200_000_000L));
        assertEquals(captured, session.focus(), "Capture must keep the composition it actually applied");
        session.endCapture(3_300_000_000L);
        session.nextShot();
        int previous = session.take();
        for (long now = 3_350_000_000L; now <= 5_000_000_000L; now += 50_000_000L) {
            session.tick(now);
            session.fishing(BOBBER.add(new Vec(4, 0, 0)));
            assertNotNull(session.frame(now));
            session.coveredFrame();
        }
        assertTrue(session.take() > previous);
        assertFalse(session.ended());
    }
    private static ShotPlanner.Plan find(FishingSearch search, Geometry geometry) {
        ShotPlanner.Plan plan = null;
        while (!search.exhausted() && plan == null) plan = search.next(geometry);
        return plan;
    }

}
