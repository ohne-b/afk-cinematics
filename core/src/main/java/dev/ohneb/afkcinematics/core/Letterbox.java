package dev.ohneb.afkcinematics.core;

/** Reversible bars; shot cuts do not restart their animation. */
public final class Letterbox {
    private double value, from;
    private boolean visible;
    private long started, previous;

    public double sample(long now, boolean show, boolean held) {
        if (held && visible == show) started += Math.max(0, now - previous);
        previous = now;
        double progress = Pose.ease((now - started) / (visible ? 550_000_000.0 : 350_000_000.0));
        value = from + ((visible ? 1 : 0) - from) * progress;
        if (visible != show) {
            from = value;
            visible = show;
            started = now;
        }
        return value;
    }

    public void reset() { value = from = 0; visible = false; started = previous = 0; }
}
