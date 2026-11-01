package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LetterboxTest {
    @Test void barsAnimateIndependentlyOfFrameRateAndDoNotRestartAtCuts() {
        for (int fps : new int[]{20, 60, 144}) {
            var bars = new Letterbox();
            assertEquals(0, bars.sample(0, true, false));
            double previous = 0;
            for (int frame = 1; frame <= fps; frame++) {
                double next = bars.sample(Math.round(frame * 1e9 / fps), true, false);
                assertTrue(next >= previous && next <= 1);
                previous = next;
            }
            assertEquals(1, previous);
            assertEquals(1, bars.sample(2_000_000_000L, true, false));
            assertEquals(1, bars.sample(2_000_000_000L, false, false));
            assertEquals(.5, bars.sample(2_175_000_000L, false, false), 1e-12);
            assertEquals(0, bars.sample(2_350_000_000L, false, false));
        }
    }

    @Test void capturesFreezeBarsAndRapidRestartsReverseWithoutJumping() {
        var bars = new Letterbox();
        bars.sample(0, true, false);
        double halfway = bars.sample(275_000_000L, true, false);
        assertEquals(.5, halfway, 1e-12);
        assertEquals(halfway, bars.sample(2_275_000_000L, true, true));
        assertEquals(halfway, bars.sample(2_275_000_000L, false, false));
        double leaving = bars.sample(2_400_000_000L, false, false);
        assertTrue(leaving > 0 && leaving < halfway);
        assertEquals(leaving, bars.sample(2_400_000_000L, true, false));
        assertEquals(1, bars.sample(2_950_000_000L, true, false));
        bars.reset();
        assertEquals(0, bars.sample(3_000_000_000L, false, false));
    }
}
