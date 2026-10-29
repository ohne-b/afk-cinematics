package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ShotOptionsTest {
    private static final Vec SUBJECT = new Vec(0, 1, 0);
    private static final Pose ENTRY = new Pose(new Vec(0, 1.6, 0), 0, 0);
    private static final Geometry OPEN = new Geometry() {
        public boolean clear(Vec from, Vec to, double margin) { return true; }
        public boolean visible(Vec from, Vec target, double margin) { return true; }
    };

    @Test void eachSelectedStyleHasItsOwnCameraMotion() {
        for (Shot.Type type : Shot.Type.values()) {
            if (type.sceneryOnly()) continue;
            var config = new CinematicConfig();
            config.shots = EnumSet.of(type);
            Shot shot = new ShotSearch(new Scene(SUBJECT, ENTRY.position(), Scene.Kind.PLAYER, 0), ENTRY, 0, config, null, true, false, new Random(2)).next(OPEN);
            assertNotNull(shot, type.name());
            assertEquals(type, shot.type());
            Pose first = shot.sample(0), last = shot.sample(shot.duration());
            if (type == Shot.Type.PAN) {
                assertEquals(first.position(), last.position());
                assertTrue(Math.abs(Math.IEEEremainder(last.yaw() - first.yaw(), 360)) > 2);
            } else if (type == Shot.Type.STILL) assertEquals(first, last);
            else assertTrue(first.position().distance(last.position()) > 0.1, type.name());
            if (type == Shot.Type.CRANE) assertTrue(Math.abs(first.position().y() - last.position().y()) > 1);
        }
    }

    @Test void changingFromAPanPreservesTheExactStartingView() {
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.PAN);
        Shot pan = new ShotSearch(new Scene(SUBJECT, ENTRY.position(), Scene.Kind.PLAYER, 0), ENTRY, 0, config, null, true, false, new Random(2)).next(OPEN);
        Pose previous = pan.sample(pan.duration());
        for (Shot.Type type : Shot.Type.values()) {
            if (type.sceneryOnly()) continue;
            config.shots = EnumSet.of(type);
            Shot next = new ShotSearch(new Scene(SUBJECT, ENTRY.position(), Scene.Kind.PLAYER, 0), previous, 0, config, pan.type(), false, false, new Random(3)).next(OPEN);
            assertNotNull(next);
            Pose start = next.sample(0);
            assertEquals(previous.position(), start.position());
            assertEquals(0, Math.IEEEremainder(start.yaw() - previous.yaw(), 360), 1e-9);
            assertEquals(previous.pitch(), start.pitch(), 1e-9);
            if (type == Shot.Type.PAN) assertTrue(Math.abs(Math.IEEEremainder(
                next.sample(next.duration()).yaw() - start.yaw(), 360)) > 1, "A consecutive panorama must keep turning");
        }
    }

    @Test void aLowCeilingFallsBackOnlyToAnEnabledStyle() {
        Geometry low = new Geometry() {
            public boolean clear(Vec from, Vec to, double margin) { return Math.max(from.y(), to.y()) + margin <= 2.5; }
            public boolean visible(Vec from, Vec target, double margin) { return true; }
        };
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.CRANE, Shot.Type.DRIFT);
        Shot shot = find(new ShotSearch(new Scene(SUBJECT, ENTRY.position(), Scene.Kind.PLAYER, 0), ENTRY, 0, config, null, true, false, new Random(2)), low);
        assertNotNull(shot);
        assertEquals(Shot.Type.DRIFT, shot.type());
        config.shots = EnumSet.of(Shot.Type.CRANE);
        assertNull(find(new ShotSearch(new Scene(SUBJECT, ENTRY.position(), Scene.Kind.PLAYER, 0), ENTRY, 0, config, null, true, false, new Random(2)), low));
    }

    @Test void visibilityFollowsTheWholePan() {
        Geometry narrow = new Geometry() {
            public boolean clear(Vec from, Vec to, double margin) { return true; }
            public boolean visible(Vec from, Vec target, double margin) { return target.distance(SUBJECT) + margin < 0.5; }
        };
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.PAN);
        assertNull(find(new ShotSearch(new Scene(SUBJECT, ENTRY.position(), Scene.Kind.PLAYER, 0), ENTRY, 0, config, null, true, false, new Random(2)), narrow));
    }

    @Test void pacingControlsDurationAndAvailableStylesAvoidImmediateRepeats() {
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.ARC, Shot.Type.DOLLY);
        for (var pace : CinematicConfig.Pace.values()) {
            config.pace = pace;
            Shot shot = new ShotSearch(new Scene(SUBJECT, ENTRY.position(), Scene.Kind.PLAYER, 0), ENTRY, 0, config, Shot.Type.ARC, true, false, new Random(2)).next(OPEN);
            assertEquals(Shot.Type.DOLLY, shot.type());
            assertTrue(shot.duration() >= pace.minimumSeconds && shot.duration() < pace.maximumSeconds);
        }
    }

    @Test void playerCoverageUsesWideAngledViewsAroundBothSides() {
        var views = new java.util.HashSet<Integer>();
        var distances = new java.util.HashSet<Integer>();
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.STILL);
        var random = new Random(4);
        for (int seed = 0; seed < 64; seed++) {
            Shot shot = new ShotSearch(new Scene(SUBJECT, ENTRY.position(), Scene.Kind.PLAYER, 0), ENTRY, 0,
                config, null, true, true, random).next(OPEN);
            Vec offset = shot.start().subtract(SUBJECT);
            double distance = Math.hypot(offset.x(), offset.z());
            assertTrue(distance >= 6.999 && distance <= 14.001);
            distances.add((int)Math.round(distance));
            views.add((int)Math.round(Math.toDegrees(Math.atan2(offset.x(), -offset.z()))));
        }
        assertEquals(java.util.Set.of(25, -25, 65, -65, 115, -115, 155, -155), views);
        assertEquals(java.util.Set.of(7, 10, 14), distances);
    }

    @Test void limitedSpaceFallsBackToFiveAndAHalfBlocks() {
        Geometry limited = new Geometry() {
            public boolean clear(Vec from, Vec to, double margin) {
                return Math.max(from.distance(SUBJECT), to.distance(SUBJECT)) + margin < 6;
            }
            public boolean visible(Vec from, Vec to, double margin) { return true; }
        };
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.STILL);
        Shot shot = find(new ShotSearch(new Scene(SUBJECT, ENTRY.position(), Scene.Kind.PLAYER, 0), ENTRY, 0,
            config, null, true, false, new Random(4)), limited);
        assertNotNull(shot, "The previous wide distance must remain available when farther views are blocked");
        Vec offset = shot.start().subtract(SUBJECT);
        assertEquals(5.5, Math.hypot(offset.x(), offset.z()), 1e-9);
    }

    @Test void overheadIsAvailableForSceneryButNeverForThePlayer() {
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.OVERHEAD);
        assertTrue(new ShotSearch(new Scene(SUBJECT, ENTRY.position(), Scene.Kind.PLAYER, 0), ENTRY, 0,
            config, null, true, true, new Random(1)).exhausted());
        var scene = new Scene(SUBJECT, SUBJECT.add(new Vec(0, 4, -8)), Scene.Kind.LANDSCAPE, 1);
        assertNotNull(find(new ShotSearch(scene, ENTRY, 0, config, null, true, true, new Random(1)), OPEN));
    }

    private static Shot find(ShotSearch search, Geometry geometry) {
        for (int attempt = 0; attempt < 100 && !search.exhausted(); attempt++) {
            Shot shot = search.next(geometry);
            if (shot != null) return shot;
        }
        assertTrue(search.exhausted());
        return null;
    }
}
