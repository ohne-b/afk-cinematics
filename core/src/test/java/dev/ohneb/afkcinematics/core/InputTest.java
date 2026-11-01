package dev.ohneb.afkcinematics.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InputTest {
    @Test void screenInputsStaySuppressedWithoutBecomingHeldGameplay() {
        var input = new InputState();
        input.press(17, false);
        input.consumeHeld();
        input.consume(18);
        assertFalse(input.held());
        input.resetHeld();
        assertTrue(input.consumed(17));
        assertTrue(input.suppressed(18));
        assertTrue(input.release(17));
        assertFalse(input.consumed(17));
        assertTrue(input.release(18));
        assertFalse(input.suppressed(18));
    }

    @Test void lifecycleResetAllowsIdleWithoutReleasingSuppressedInputs() {
        var input = new InputState();
        var idle = new IdleTimer();
        idle.reset(0);
        input.press(17, false);
        input.press(18, false);
        // A release while unfocused quarantines the key until a trusted release arrives.
        input.suppress(17);
        input.resetHeld();
        assertTrue(idle.ready(10_000_000_000L, 10, !input.held()), "Stale held input permanently blocked AFK");
        assertFalse(input.heldExcept(18));
        assertTrue(input.suppressed(17));
        assertTrue(input.press(17, false));
        assertTrue(input.held(), "A new physical event still counts as activity");
        assertTrue(input.release(17));
        assertFalse(input.held());
        assertFalse(input.suppressed(17));
        assertFalse(input.press(17, false), "A fresh press after release must work normally");
    }

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
