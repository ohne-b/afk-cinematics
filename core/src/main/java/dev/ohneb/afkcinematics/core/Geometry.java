package dev.ohneb.afkcinematics.core;

public interface Geometry {
    // Margins conservatively include motion between planning samples; runtime queries use zero.
    boolean clear(Vec from, Vec to, double margin);
    boolean visible(Vec camera, Vec subject, double margin);

    default boolean clear(Vec from, Vec to) { return clear(from, to, 0); }
    default boolean visible(Vec camera, Vec subject) { return visible(camera, subject, 0); }

    default Scene scenery(Vec anchor, Pose original, double bearing, int probe) { return null; }

    default boolean valid(Shot shot, Vec entry) {
        if (entry.distance(shot.start()) > 16) return false;
        if (!entry.equals(shot.start()) && !validCurve(entry, entry.lerp(shot.start(), 0.5), shot.start(),
            shot.focusStart(), shot.focusStart(), 5)) return false;
        return validCurve(shot.start(), shot.control(), shot.end(), shot.focusStart(), shot.focusEnd(), 5);
    }

    private boolean validCurve(Vec start, Vec control, Vec end, Vec focusStart, Vec focusEnd, int splits) {
        Vec left = start.lerp(control, 0.5), right = control.lerp(end, 0.5);
        Vec middle = left.lerp(right, 0.5), focus = focusStart.lerp(focusEnd, 0.5);
        if (splits > 0 && (!start.equals(control) || !start.equals(end) || !focusStart.equals(focusEnd))) {
            return validCurve(start, left, middle, focusStart, focus, splits - 1)
                && validCurve(middle, right, end, focus, focusEnd, splits - 1);
        }
        // The Bezier hull bounds every intermediate sightline, including movement of its focus.
        double margin = Math.max(Math.max(middle.distance(start), middle.distance(control)),
            Math.max(middle.distance(end), Math.max(focus.distance(focusStart), focus.distance(focusEnd))));
        double bend = control.distance(start.lerp(end, 0.5)) * 0.5;
        return clear(start, end, bend) && visible(middle, focus, margin);
    }
}
