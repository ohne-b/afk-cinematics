package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InputTest {
    @Test void blockedInputStaysBlockedThroughRepeatsUntilRelease() {
        var input = new InputState();
        assertTrue(input.press(17, true));
        assertTrue(input.press(17, false));
        assertTrue(input.held());
        assertTrue(input.release(17));
        assertFalse(input.held());
        assertFalse(input.press(17, false));
    }

    @Test void eligibilityAndClockChangesRestartTheFullIdleDelay() {
        var idle = new IdleTimer();
        assertFalse(idle.ready(0, 30, true));
        assertTrue(idle.ready(30_000_000_000L, 30, true));
        assertFalse(idle.ready(40_000_000_000L, 30, false));
        assertFalse(idle.ready(50_000_000_000L, 30, true));
        assertTrue(idle.ready(70_000_000_000L, 30, true));
        assertFalse(idle.ready(1, 30, true));
    }
}
