package dev.ohneb.afkcinematics.core;

import java.util.HashSet;
import java.util.Set;

public final class InputState {
    private final Set<Long> held = new HashSet<>();
    private final Set<Long> suppressed = new HashSet<>();
    private final Set<Long> consumed = new HashSet<>();

    public boolean press(long input, boolean suppress) {
        held.add(input);
        if (suppress) suppressed.add(input);
        return suppressed.contains(input);
    }

    public boolean release(long input) {
        held.remove(input);
        consumed.remove(input);
        return suppressed.remove(input);
    }

    public void consume(long input) {
        held.remove(input);
        suppressed.add(input);
        consumed.add(input);
    }

    public void consumeHeld() {
        suppressed.addAll(held);
        consumed.addAll(held);
        held.clear();
    }

    public boolean consumed(long input) { return consumed.contains(input); }

    public boolean held() { return !held.isEmpty(); }
    public boolean heldExcept(long permitted) { return held.stream().anyMatch(input -> input != permitted); }
    public void suppress(long input) { suppressed.add(input); }
    public boolean suppressed(long input) { return suppressed.contains(input); }
    public void resetHeld() { held.retainAll(suppressed); }
}
