package org.bukkit;

import java.util.Objects;

public class Location implements Cloneable {
    private World world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    public Location(World world, double x, double y, double z) {
        this(world, x, y, z, 0.0F, 0.0F);
    }

    public Location(World world, double x, double y, double z, float yaw, float pitch) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public World getWorld() { return world; }
    public void setWorld(World world) { this.world = world; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }
    public float getYaw() { return yaw; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public float getPitch() { return pitch; }
    public void setPitch(float pitch) { this.pitch = pitch; }

    @Override
    public Location clone() {
        return new Location(world, x, y, z, yaw, pitch);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Location location)) return false;
        return Double.compare(x, location.x) == 0
            && Double.compare(y, location.y) == 0
            && Double.compare(z, location.z) == 0
            && Float.compare(yaw, location.yaw) == 0
            && Float.compare(pitch, location.pitch) == 0
            && Objects.equals(world, location.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, y, z, yaw, pitch);
    }
}
