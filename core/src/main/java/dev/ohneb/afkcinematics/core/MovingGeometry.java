package dev.ohneb.afkcinematics.core;

/** Plans in subject coordinates, then validates the actual world-space camera movement. */
public final class MovingGeometry implements Geometry {
    private final Geometry world;
    private final FollowMotion motion;
    private Scene.Kind activity = Scene.Kind.FLIGHT;
    private boolean frontViews = true;
    private Vec origin, target;
    private Pose previous;
    private Vec previousFocus;
    private int cuts;
    private int previousTake;
    private long updated;
    private double heading, desiredHeading, turnVelocity, turnScale = 1, scaleVelocity;
    private boolean followsCourse, reframeRequested, tightTurn;
    private double elapsed, extension = 1, clearSeconds, searchSeconds;
    private Vec previousTarget, previousOrigin;
    private Vec adjustmentVelocity = new Vec(0, 0, 0);
    private Vec avoidance;
    private Vec velocity = new Vec(0, 0, 0);
    private RoadsideView roadside;
    private Shot.Type style;
    private boolean roadsideFinished, roadsideLeaving;
    private double worldFov = 30, fov = 30, aspect = 16.0 / 9, crop = .82, bodyWidth = .8, bodyHeight = 1.3;

    public MovingGeometry(Geometry world, Vec origin, long now) {
        this.world = world;
        this.origin = this.target = origin;
        motion = new FollowMotion(origin);
        updated = now;
    }

    public void framing(Scene.Kind activity, boolean frontViews) {
        if (this.activity != activity) {
            adjustmentVelocity = new Vec(0, 0, 0);
            avoidance = null;
            roadside = null;
            roadsideFinished = true;
        }
        this.activity = activity;
        this.frontViews = frontViews;
    }

    public boolean follow(Vec next, long now, boolean capture, boolean grounded) {
        if (now < updated || next.distance(target) > 16 || next.distance(origin) > 24) return false;
        elapsed = capture ? 0 : Math.min(0.25, (now - updated) / 1e9);
        target = next;
        updated = now;
        if (!capture) {
            Vec before = origin;
            if (followsCourse && activity.travel())
                origin = motion.follow(next, elapsed, activity == Scene.Kind.FOLLOW || activity == Scene.Kind.RIDE, grounded);
            else {
                origin = origin.lerp(next, 1 - Math.exp(-elapsed / 0.08));
                motion.reset(origin);
            }
            turn(elapsed);
            if (elapsed > 0) {
                Vec measured = origin.subtract(before).scale(1 / elapsed);
                velocity = velocity.lerp(measured, 1 - Math.exp(-elapsed * 5));
            }
        } else { motion.hold(next); adjustmentVelocity = new Vec(0, 0, 0); }
        return true;
    }

    public void course(double degrees) {
        if (!Double.isFinite(degrees)) return;
        desiredHeading = degrees;
        if (!followsCourse) { heading = degrees; followsCourse = true; }
    }

    public void holdCourse() { desiredHeading = heading; turnVelocity = 0; }

    public Vec relative(Vec offset) { return rotate(offset, -heading); }

    public boolean needsReframe(Scene.Kind kind) {
        if (style != null && style.fixedTravel()) return roadsideFinished || roadsideLeaving;
        if (!kind.travel() || !followsCourse || reframeRequested
            || Math.abs(Math.IEEEremainder(desiredHeading - heading, 360)) < 155) return false;
        reframeRequested = true;
        return true;
    }

    public boolean turning() { return tightTurn || Math.abs(Math.IEEEremainder(desiredHeading - heading, 360)) >= 20; }

    private void turn(double seconds) {
        if (!followsCourse || seconds <= 0) return;
        double change = Math.IEEEremainder(desiredHeading - heading, 360);
        if (!activity.travel() || Math.abs(change) < 12) tightTurn = false;
        else if (Math.abs(change) > 55) tightTurn = true;
        // Keep the shorter boom through the corner, then ease back without an outward kick.
        double scaleTarget = tightTurn ? .6 : 1;
        double scaleSpring = tightTurn ? 3 : 1.5;
        double scaleError = turnScale - scaleTarget;
        double scaleChange = (scaleVelocity + scaleSpring * scaleError) * seconds;
        double scaleDecay = Math.exp(-scaleSpring * seconds);
        turnScale = Math.clamp(scaleTarget + (scaleError + scaleChange) * scaleDecay, .6, 1);
        scaleVelocity = (scaleVelocity - scaleSpring * scaleChange) * scaleDecay;
        double radius = previous == null ? 10 : Math.hypot(previous.position().x() - origin.x(), previous.position().z() - origin.z());
        double speed = Math.min(22 / (turnScale * turnScale), Math.max(22, Math.toDegrees(4 / Math.max(1, radius))));
        double offset = -change;
        double spring = 3.5;
        double temporary = (turnVelocity + spring * offset) * seconds;
        double decay = Math.exp(-spring * seconds);
        double step = Math.clamp(change + (offset + temporary) * decay, -speed * seconds, speed * seconds);
        heading += step;
        turnVelocity = Math.clamp((turnVelocity - spring * temporary) * decay, -speed, speed);
    }

    public Pose local(Pose pose) {
        return new Pose(rotate(pose.position().subtract(origin), -heading), pose.yaw() - heading, pose.pitch());
    }

    public boolean sees(Pose pose, Vec focus) { return world.visible(pose.position(), rotate(focus, heading).add(target)); }

    public Pose apply(Pose pose, Vec focus, int take, boolean capture) {
        if (pose == null) return null;
        boolean cut = take != previousTake && !capture;
        if (cut) { roadside = null; roadsideFinished = roadsideLeaving = false; }
        if (cut && followsCourse && !capture) {
            heading = desiredHeading;
            turnVelocity = 0;
            reframeRequested = false;
            turnScale = 1;
            scaleVelocity = 0;
            tightTurn = false;
        }
        Vec offset = pose.position();
        double radius = Math.hypot(offset.x(), offset.z());
        double scale = radius == 0 ? 1 : Math.max(turnScale, Math.min(1, 3 / radius));
        Vec point = rotate(new Vec(offset.x() * scale, offset.y(), offset.z() * scale), heading).add(origin);
        Vec worldFocus = focus == null ? null : rotate(focus, heading).add(activity.travel() ? origin : target);
        Pose result = worldFocus == null ? new Pose(point, pose.yaw() + heading, pose.pitch()) : Pose.lookingAt(point, worldFocus);
        if (capture && previous != null) { result = previous; worldFocus = previousFocus; }
        else if (followsCourse && focus != null) {
            if (activity.travel() && style != null && style.fixedTravel() && !roadsideFinished) {
                if (roadside == null && fixedWindow(pose.position())) roadside = new RoadsideView(point, origin);
                if (roadside != null) {
                    Pose planted = roadside.sample(origin, cut ? 0 : elapsed);
                    Vec future = target.add(velocity.scale(.8));
                    if (usable(planted.position(), 0) && fits(planted, target, .92)) {
                        roadsideLeaving |= planted.position().distance(future) > 27
                            || !fits(planted, future, .72) || !world.visible(planted.position(), future);
                        previous = planted;
                        previousFocus = planted.lookAhead(Math.max(1, planted.position().distance(origin)));
                        previousOrigin = origin;
                        previousTarget = target;
                        if (cut) cuts++;
                        previousTake = take;
                        return planted;
                    }
                }
                // A sudden turn/obstruction must not strand a planted camera. Recover through
                // the validated follow selector while its successor is prepared.
                roadsideFinished = true;
            }
            return travel(point, worldFocus, take, cut);
        }
        Vec from = previous == null || cut ? result.position() : previous.position();
        if (!Scene.inRange(result.position(), target) || !world.clear(from, result.position())
            || !world.visible(result.position(), target)
            || worldFocus != null && !worldFocus.equals(target) && !world.visible(result.position(), worldFocus)) return null;
        previous = result;
        previousFocus = worldFocus;
        if (cut) cuts++;
        previousTake = take;
        return result;
    }

    /** Travel framing may shorten, but every applied position still has a clear rider sightline. */
    private Pose travel(Vec desired, Vec focus, int take, boolean cut) {
        boolean walking = walking();
        if (activity.travel()) desired = travelView(desired, cut);
        Vec offset = desired.subtract(target);
        double distance = offset.length();
        Vec point = null;
        if (distance > .001) {
            Vec direction = offset.scale(1 / distance);
            // Only obstruction shortens the boom; jumping must not change its intended length.
            double maximum = distance * (cut || activity.travel() ? 1 : Math.min(1, extension + elapsed * 2 / Math.max(.8, distance)));
            point = nearby(direction, maximum, walking ? .15 : .3);
            // A rider beside a corner can be inside the padding while the real sightline is clear.
            if (point == null) point = nearby(direction, maximum, 0);
        }
        if (point == null) return null;

        Vec from = previous == null || cut ? point : previous.position();
        Vec nextVelocity = new Vec(0, 0, 0);
        if (!cut && previous != null) {
            Vec carried = previousOrigin == null ? from : from.add(origin.subtract(previousOrigin));
            double movement = walking ? carried.distance(from) : previousTarget == null ? 0 : target.distance(previousTarget);
            double limit = Math.max(.001, movement * (walking ? 1 : 1.5) + elapsed * (walking ? 6 : 12));
            double travel = from.distance(point);
            Vec step = travel > limit ? from.lerp(point, limit / travel) : point;
            if (activity.travel()) {
                // Ease composition changes relative to the filtered rig, not the player's jump arc.
                double spring = 6, decay = Math.exp(-spring * elapsed);
                Vec error = carried.subtract(point);
                Vec change = adjustmentVelocity.add(error.scale(spring)).scale(elapsed);
                Vec eased = point.add(error.add(change).scale(decay));
                Vec velocity = adjustmentVelocity.subtract(change.scale(spring)).scale(decay);
                if (eased.distance(point) < .0001) { eased = point; velocity = new Vec(0, 0, 0); }
                double widening = eased.distance(origin) - carried.distance(origin);
                if (widening > elapsed * 2) {
                    double amount = elapsed * 2 / widening;
                    eased = carried.lerp(eased, amount);
                    velocity = velocity.scale(amount);
                }
                double distanceFromPrevious = from.distance(eased);
                if (distanceFromPrevious > limit) {
                    double amount = limit / distanceFromPrevious;
                    eased = from.lerp(eased, amount);
                    velocity = velocity.scale(amount);
                }
                if (usable(eased, 0) && world.clear(from, eased)) { step = eased; nextVelocity = velocity; }
            }
            if (usable(step, 0) && world.clear(from, step)) point = step;
            else {
                // The continuous path is blocked. Cut directly to the validated clear destination.
                cut = true;
                nextVelocity = new Vec(0, 0, 0);
            }
        }
        adjustmentVelocity = nextVelocity;
        double ratio = Math.min(1, point.distance(target) / Math.max(.001, distance));
        // Shorten look-ahead only for a close recovery view. A jump or changing boom length
        // must not continually move the aim between the live player and the filtered rig.
        Vec aim = activity.travel() ? target.lerp(focus, Math.min(1, point.distance(target) / 3)) : target.lerp(focus, ratio);
        if (!activity.travel() && !world.visible(point, aim)) aim = target;
        Pose result = Pose.lookingAt(point, aim);
        extension = ratio;
        previous = result;
        previousFocus = aim;
        previousTarget = target;
        previousOrigin = origin;
        if (cut) cuts++;
        previousTake = take;
        return result;
    }

    private boolean walking() { return activity == Scene.Kind.FOLLOW || activity == Scene.Kind.RIDE; }

    private Vec travelView(Vec desired, boolean cut) {
        if (cut) { avoidance = null; clearSeconds = searchSeconds = 0; }
        Vec carried = previous == null || previousOrigin == null || cut ? desired : previous.position().add(origin.subtract(previousOrigin));
        Vec ahead = previousOrigin == null || elapsed <= 0 ? new Vec(0, 0, 0) : origin.subtract(previousOrigin).scale(.65 / elapsed);
        ahead = new Vec(ahead.x(), 0, ahead.z());
        if (ahead.length() > 4) ahead = ahead.scale(4 / ahead.length());
        // Reserve lower-body visibility on descents, without aiming that preference into a rising step.
        Vec body = new Vec(target.x(), Math.min(target.y(), origin.y()) - .65, target.z());
        if (!walking()) body = target;
        else if (!world.visible(target, body)) body = target.add(new Vec(0, -.65, 0));
        boolean preferred = usable(desired, .15) && world.visible(desired, body) && forecast(desired, ahead)
            && (previous == null || cut || world.clear(previous.position(), desired));
        clearSeconds = preferred ? clearSeconds + elapsed : 0;
        if (preferred && (avoidance == null || clearSeconds >= .65)) { avoidance = null; return desired; }

        Vec retained = avoidance == null ? carried : origin.add(rotate(avoidance, heading));
        searchSeconds += elapsed;
        if (avoidance != null && usable(retained, 0) && world.visible(retained, body)
            && (forecast(retained, ahead) || searchSeconds < .15)) return retained;
        searchSeconds = 0;

        Vec best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        // Keep an established roomy view when a wall or stair edge makes nearby choices alternate.
        if (carried.distance(target) >= Math.min(3.5, desired.distance(target) * .7)) {
            bestScore = travelScore(carried, desired, carried, ahead, body, cut, bestScore);
            if (Double.isFinite(bestScore)) best = carried;
        }
        Vec offset = desired.subtract(origin);
        double radius = Math.hypot(offset.x(), offset.z());
        Vec[] arms = {offset, rotate(offset, -20), rotate(offset, 20), rotate(offset, -40), rotate(offset, 40),
            rotate(new Vec(0, offset.y(), -radius), desiredHeading)};
        for (double lift : new double[]{0, .75, 1.5, 3, 4.5, 6}) {
            for (Vec arm : arms) {
                Vec candidate = origin.add(arm).add(new Vec(0, lift, 0));
                double score = travelScore(candidate, desired, carried, ahead, body, cut, bestScore);
                if (score < bestScore) { best = candidate; bestScore = score; }
            }
        }
        if (best != null) avoidance = rotate(best.subtract(origin), -heading);
        return best == null ? desired : best;
    }

    private boolean forecast(Vec point, Vec ahead) {
        if (!world.clear(point, point.add(ahead), .15)) return false;
        // Check the swept sightline too: a trunk can miss the camera yet cross the view.
        for (double fraction : new double[]{.5, 1}) {
            Vec step = ahead.scale(fraction);
            if (!world.visible(point.add(step), target.add(step), .15)) return false;
        }
        return true;
    }

    private double travelScore(Vec point, Vec desired, Vec carried, Vec ahead, Vec body, boolean cut, double bestScore) {
        double deviation = point.distance(desired), change = point.distance(carried);
        double score = deviation * deviation + change * change * .5;
        if (score >= bestScore || !usable(point, .15)) return Double.POSITIVE_INFINITY;
        if (!world.visible(point, body)) score += 64;
        if (score >= bestScore) return Double.POSITIVE_INFINITY;
        // Look a short distance ahead so the rig starts lifting or moving sideways before impact.
        if (!forecast(point, ahead)) score += 256;
        if (score >= bestScore) return Double.POSITIVE_INFINITY;
        if (previous != null && !cut && !world.clear(previous.position(), point)) score += 256;
        return score;
    }

    private Vec nearby(Vec direction, double maximum, double margin) {
        Vec point = along(direction, maximum, margin);
        if (point != null) return point;
        // A corner can block the old viewing direction completely; try the new course nearby.
        for (int side : new int[]{0, 180, 90, -90}) {
            Vec candidate = rotate(new Vec(0, .2, -1.25), desiredHeading + side).add(target);
            if (usable(candidate, margin)) return candidate;
        }
        return null;
    }

    private Vec along(Vec direction, double maximum, double margin) {
        double minimum = Math.min(.8, maximum);
        Vec far = target.add(direction.scale(maximum));
        if (usable(far, margin)) return far;
        Vec near = target.add(direction.scale(minimum));
        if (!usable(near, margin)) return null;
        // Fixed work per frame; the padded arm also leaves room before foliage reaches the camera.
        for (int i = 0; i < 10 && maximum - minimum > .01; i++) {
            double middle = (minimum + maximum) * .5;
            Vec candidate = target.add(direction.scale(middle));
            if (usable(candidate, margin)) { minimum = middle; near = candidate; }
            else maximum = middle;
        }
        return near;
    }

    private boolean usable(Vec point, double margin) {
        if (!frontViews && activity.travel()) {
            double radians = Math.toRadians(desiredHeading);
            Vec offset = point.subtract(target);
            if (-Math.sin(radians) * offset.x() + Math.cos(radians) * offset.z() > .05) return false;
        }
        return Scene.inRange(point, target) && world.clear(point, point, margin) && world.visible(point, target, walking() ? 0 : margin);
    }

    public int cuts() { return cuts; }

    public void style(Shot.Type type) { style = type; }

    public void worldLens(double value) {
        if (Double.isFinite(value) && value > 0 && value < 180) worldFov = value;
    }

    public void viewport(double conservativeFov, double aspect, boolean bars, double width, double height) {
        this.fov = Math.min(worldFov, conservativeFov);
        this.aspect = Math.max(.1, aspect);
        crop = bars ? .82 : 1;
        bodyWidth = Math.max(.6, width);
        bodyHeight = Math.max(1, height);
    }

    private boolean fits(Pose pose, Vec body, double fill) {
        return RoadsideView.fits(pose, body, bodyWidth, bodyHeight, fov, aspect, crop, fill);
    }

    /** Leave time to prepare a successor; a fresh rig has no motion history yet. */
    boolean fixedWindow(Vec local) {
        if (!activity.travel() || velocity.length() > 9) return false;
        Vec point = rotate(local, heading).add(origin);
        if (!usable(point, .15)) return false;
        for (double seconds : new double[]{0, 1, 2}) {
            Vec future = target.add(velocity.scale(seconds));
            double distance = point.distance(future);
            Pose aim = Pose.lookingAt(point, future);
            if (distance < 6 || distance > 26 || !fits(aim, future, .65) || !world.visible(point, future, .15)) return false;
        }
        return true;
    }

    public boolean adaptsTravel() { return followsCourse; }

    @Override public boolean clear(Vec from, Vec to, double margin) {
        return world.clear(rotate(from, heading).add(origin), rotate(to, heading).add(origin), margin);
    }
    @Override public boolean visible(Vec from, Vec to, double margin) {
        return world.visible(rotate(from, heading).add(origin), rotate(to, heading).add(target), margin);
    }

    @Override public double viewQuality(Pose view) {
        return world.viewQuality(new Pose(rotate(view.position(), heading).add(origin), view.yaw() + heading, view.pitch()));
    }

    private static Vec rotate(Vec point, double degrees) {
        double angle = Math.toRadians(degrees), cosine = Math.cos(angle), sine = Math.sin(angle);
        return new Vec(point.x() * cosine - point.z() * sine, point.y(), point.x() * sine + point.z() * cosine);
    }
}
