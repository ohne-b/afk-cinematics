package dev.ohneb.afkcinematics.core;

/** A damped tracking point shared by the camera and its aim. */
final class FollowMotion {
    private record Axis(double position, double velocity) { }
    private Vec position, velocity = new Vec(0, 0, 0), previous;
    private double groundY, airborne;

    FollowMotion(Vec start) { reset(start); }

    void reset(Vec start) {
        position = previous = start;
        velocity = new Vec(0, 0, 0);
        groundY = start.y();
        airborne = 0;
    }

    void hold(Vec next) {
        previous = next;
        velocity = new Vec(0, 0, 0);
        groundY = next.y();
        airborne = 0;
    }

    Vec follow(Vec next, double seconds, boolean walking, boolean grounded) {
        if (seconds <= 0) return position;
        if (!walking || grounded) { groundY = next.y(); airborne = 0; }
        else {
            airborne += seconds;
            // Let a short hop move within the frame; follow sustained falls and new landings.
            if (airborne > .65 || next.y() < groundY - .7) groundY = next.y();
        }
        Vec destination = new Vec(next.x(), groundY, next.z());
        Axis x = advance(position.x(), velocity.x(), previous.x(), destination.x(), 18, seconds);
        Axis y = advance(position.y(), velocity.y(), previous.y(), destination.y(), walking ? 7 : 10, seconds);
        Axis z = advance(position.z(), velocity.z(), previous.z(), destination.z(), 18, seconds);
        position = new Vec(x.position(), y.position(), z.position());
        velocity = new Vec(x.velocity(), y.velocity(), z.velocity());
        previous = destination;
        return position;
    }

    private static Axis advance(double position, double velocity, double from, double to, double spring, double seconds) {
        // Exact critical damping for a linearly moving target, independent of frame rate.
        double speed = (to - from) / seconds;
        double offset = position - from + 2 * speed / spring;
        double change = (velocity - speed + spring * offset) * seconds;
        double decay = Math.exp(-spring * seconds);
        return new Axis(to - 2 * speed / spring + (offset + change) * decay,
            speed + (velocity - speed - spring * change) * decay);
    }
}
