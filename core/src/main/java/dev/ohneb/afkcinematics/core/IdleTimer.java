package dev.ohneb.afkcinematics.core;

public final class IdleTimer {
    private long lastActivity = Long.MIN_VALUE;

    public void reset(long now) { lastActivity = now; }

    public boolean ready(long now, int delaySeconds, boolean eligible) {
        if (!eligible || lastActivity == Long.MIN_VALUE || now < lastActivity) reset(now);
        return eligible && now - lastActivity >= delaySeconds * 1_000_000_000L;
    }
}
