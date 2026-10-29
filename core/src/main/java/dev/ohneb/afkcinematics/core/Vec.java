package dev.ohneb.afkcinematics.core;

public record Vec(double x, double y, double z) {
    public Vec {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Non-finite position");
    }

    public Vec add(Vec other) { return new Vec(x + other.x, y + other.y, z + other.z); }
    public Vec subtract(Vec other) { return new Vec(x - other.x, y - other.y, z - other.z); }
    public Vec scale(double factor) { return new Vec(x * factor, y * factor, z * factor); }
    public double length() { return Math.sqrt(x * x + y * y + z * z); }
    public double distance(Vec other) { return subtract(other).length(); }
    public Vec lerp(Vec other, double progress) { return add(other.subtract(this).scale(progress)); }
}
