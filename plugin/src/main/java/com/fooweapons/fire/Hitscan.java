package com.fooweapons.fire;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class Hitscan {
    public record Hit(LivingEntity target, double distance, boolean headshot) {}

    public static Hit fire(Player shooter, Vector direction, double range) {
        Location eye = shooter.getEyeLocation();
        RayTraceResult result = shooter.getWorld().rayTrace(
            eye, direction.clone().normalize(), range,
            org.bukkit.FluidCollisionMode.NEVER, true, 0.0,
            e -> e instanceof LivingEntity && !e.equals(shooter)
        );
        if (result == null || result.getHitEntity() == null) return null;
        Entity hit = result.getHitEntity();
        if (!(hit instanceof LivingEntity target)) return null;
        double distance = eye.toVector().distance(result.getHitPosition());
        boolean headshot = isHeadshot(target, result.getHitPosition().getY());
        return new Hit(target, distance, headshot);
    }

    private static boolean isHeadshot(LivingEntity target, double hitY) {
        double headY = target.getEyeLocation().getY();
        return hitY >= headY - 0.15;
    }
}
