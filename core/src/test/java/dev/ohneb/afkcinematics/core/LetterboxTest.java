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
            assertEquals(0, bars.sample(2_000_000_000L, false, false));
            assertEquals(0, bars.sample(2_175_000_000L, false, false));
            assertEquals(0, bars.sample(2_350_000_000L, false, false));
        }
    }

    @Test void capturesFreezeEntryButExitIsImmediateAndRestartsBeginFresh() {
        var bars = new Letterbox();
        bars.sample(0, true, false);
        double halfway = bars.sample(425_000_000L, true, false);
        assertEquals(.5, halfway, 1e-12);
        assertEquals(halfway, bars.sample(2_425_000_000L, true, true));
        assertEquals(0, bars.sample(2_425_000_000L, false, true));
        assertEquals(0, bars.sample(2_500_000_000L, true, false));
        assertEquals(1, bars.sample(3_350_000_000L, true, false));
        bars.reset();
        assertEquals(0, bars.sample(3_500_000_000L, false, false));
    }
}
