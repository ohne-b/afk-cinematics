package dev.ohneb.afkcinematics.core;

/** Ease bars in once per session; returning control removes them immediately. */
public final class Letterbox {
    private boolean visible;
    private long started, previous;

    public double sample(long now, boolean show, boolean held) {
        if (!show) { reset(); return 0; }
        if (!visible) { visible = true; started = now; }
        else if (held) started += Math.max(0, now - previous);
        previous = now;
        return Pose.ease((now - started) / 850_000_000.0);
    }

    public void reset() { visible = false; started = previous = 0; }
}
