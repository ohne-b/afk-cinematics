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

    @Test void fishingStylesMoveGentlyAndABitePrefersTheFloat() {
        var config = new CinematicConfig();
        for (Shot.Type type : new Shot.Type[]{Shot.Type.ANGLER, Shot.Type.CAST_LINE, Shot.Type.FLOAT}) {
            config.shots = EnumSet.of(type);
            var plan = new FishingSearch(PLAYER, BOBBER, false, config, new ShotHistory(), new Random(1)).next(OPEN);
            assertNotNull(plan, type.name());
            assertTrue(plan.cut());
            assertEquals(type, plan.shot().type());
            assertTrue(plan.shot().start().distance(plan.shot().end()) > .5);
        }
        config.shots = EnumSet.of(Shot.Type.ANGLER, Shot.Type.CAST_LINE, Shot.Type.FLOAT);
        assertEquals(Shot.Type.FLOAT, new FishingSearch(PLAYER, BOBBER, true, config, new ShotHistory(), new Random(1)).next(OPEN).shot().type());
    }

    @Test void aClearCompositionCannotHideAnObstructedBobber() {
        var config = new CinematicConfig();
        config.shots = EnumSet.of(Shot.Type.ANGLER);
        Geometry obscured = new Geometry() {
            public boolean clear(Vec a, Vec b, double margin) { return true; }
            public boolean visible(Vec a, Vec b, double margin) { return b.distance(BOBBER) > 1; }
        };
        var search = new FishingSearch(PLAYER, BOBBER, false, config, new ShotHistory(), new Random(1));
        while (!search.exhausted()) assertNull(search.next(obscured));
    }
}
