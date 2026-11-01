package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class RoadsideTest {
    private static final Vec ZERO = new Vec(0, 0, 0);
    private static final Geometry OPEN = new Geometry() {
        public boolean clear(Vec a, Vec b, double margin) { return true; }
        public boolean visible(Vec a, Vec b, double margin) { return a.distance(b) <= 32; }
    };

    @Test void plantedCameraPansHoldsCapturesAndUsesItsPreparedSuccessor() {
        for (var transition : CinematicConfig.Transition.values()) plantedHandoff(transition);
    }

    private void plantedHandoff(CinematicConfig.Transition transition) {
        var moving = new MovingGeometry(OPEN, ZERO, 0);
        moving.course(0);
        moving.framing(Scene.Kind.FOLLOW, false);
        moving.worldLens(70);
        moving.viewport(70, 16.0 / 9, true, .7, 1.3);
        for (int frame = 1; frame <= 40; frame++)
            moving.follow(new Vec(0, 0, frame * .15), frame * 50_000_000L, false, true);
        var config = new CinematicConfig();
        config.transition = transition;
        config.shots = EnumSet.of(Shot.Type.PASS_BY);
        config.frontTravel = false;
        var session = new CinematicSession(ZERO, new Pose(new Vec(0, 2, -5), 0, 0), 0,
            config, moving, new Random(4), Scene.Kind.FOLLOW);
        Pose first = null, last = null;
        long now = 2_000_000_000L;
        for (int frame = 0; frame < 25; frame++) {
            now += 50_000_000L;
            moving.follow(new Vec(0, 0, 6 + frame * .15), now, false, true);
            session.tick(now);
            Pose local = session.frame(now);
            moving.style(session.type());
            Pose view = moving.apply(local, session.focus(), session.take(), false);
            assertNotNull(view);
            if (first == null) first = view;
            assertEquals(first.position(), view.position(), "Roadside camera translated with the player");
            last = view;
        }
        assertTrue(Math.abs(first.yaw() - last.yaw()) > 5);
        session.beginCapture(now);
        moving.follow(new Vec(0, 0, 10), now + 100_000_000L, true, true);
        assertEquals(last, moving.apply(session.frame(now), session.focus(), session.take(), true));
        session.endCapture(now + 100_000_000L);
        now += 100_000_000L;
        int take = session.take();
        // No tick/search follows this request: only the already-prepared successor can satisfy it.
        session.handoffSoon(now);
        for (int frame = 1; frame <= 12 && session.take() == take; frame++) {
            session.frame(now + frame * 50_000_000L);
            if (session.fadeOpacity() == 1) session.coveredFrame();
        }
        assertEquals(take + 1, session.take());
        assertFalse(session.ended());
    }

    @Test void framingIncludesTheBodyBarsAndNarrowAspectAndRejectsFastFlybys() {
        Pose camera = Pose.lookingAt(new Vec(0, 0, -10), ZERO);
        assertTrue(RoadsideView.fits(camera, ZERO, .7, 1.3, 70, 16.0 / 9, .82, .8));
        assertFalse(RoadsideView.fits(camera, new Vec(3, 0, 0), .7, 1.3, 30, .6, .82, .8));
        assertFalse(RoadsideView.fits(camera, new Vec(0, 2, 0), .7, 1.3, 30, 16.0 / 9, .82, .8));
        var moving = new MovingGeometry(OPEN, ZERO, 0);
        moving.course(0);
        moving.framing(Scene.Kind.FLIGHT, true);
        moving.worldLens(70);
        moving.viewport(70, 16.0 / 9, true, .7, 1.3);
        for (int frame = 1; frame <= 40; frame++) moving.follow(new Vec(0, 0, frame), frame * 50_000_000L, false, false);
        assertFalse(moving.fixedWindow(new Vec(12, 3, 5)), "Fast flight cannot sustain a planted camera's range");
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.PASS_BY, Shot.Type.OVERLOOK);
        var fishing = new TravelSearch(ZERO, Scene.Kind.FISHING, config, new ShotHistory(), new Random(1));
        ShotPlanner.Plan fallback = fishing.next(OPEN);
        assertNotNull(fallback);
        assertFalse(fallback.shot().type().fixedTravel(), "Fishing fallback selected a travel-only planted camera");
    }
}
