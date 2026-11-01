package dev.ohneb.afkcinematics.core;

/** The old and new viewpoints each need an opaque rendered frame before revealing a cut. */
final class ShotFade {
    static final long DURATION = 220_000_000L;
    private enum Phase { CLEAR, OUT, DESTINATION, IN }
    private Phase phase = Phase.CLEAR;
    private long started, sampled;
    private double from, opacity;
    private boolean covered;

    void sample(long now) {
        sampled = now;
        if (phase != Phase.OUT && phase != Phase.IN) return;
        double t = Math.clamp((double)(now - started) / DURATION, 0, 1);
        double eased = t * t * (3 - 2 * t);
        opacity = phase == Phase.OUT ? from + (1 - from) * eased : from * (1 - eased);
        if (phase == Phase.IN && t == 1) phase = Phase.CLEAR;
    }

    boolean prepare(long now) {
        if (phase != Phase.OUT) {
            phase = Phase.OUT;
            started = now;
            from = opacity;
            covered = false;
        }
        return covered;
    }

    void swapped(long now) {
        phase = Phase.DESTINATION;
        sampled = now;
        opacity = 1;
        covered = false;
    }

    void coveredFrame() {
        if (opacity != 1) return;
        if (phase == Phase.OUT) covered = true;
        else if (phase == Phase.DESTINATION) reveal(sampled);
    }

    void cancel(long now) { if (phase == Phase.OUT) reveal(now); }

    private void reveal(long now) {
        phase = Phase.IN;
        started = now;
        from = opacity;
        covered = false;
    }

    double opacity() { return opacity; }
    void pause(long elapsed) { started += elapsed; sampled += elapsed; }
}
