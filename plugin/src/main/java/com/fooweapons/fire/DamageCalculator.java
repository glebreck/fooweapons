package com.fooweapons.fire;

public final class DamageCalculator {
    private DamageCalculator() {}

    public static double compute(
        double baseDamage,
        double distance,
        double falloffStart,
        double falloffEnd,
        double minDamage,
        double headshotMultiplier,
        boolean headshot
    ) {
        double dmg;
        if (distance <= falloffStart) {
            dmg = baseDamage;
        } else if (distance >= falloffEnd) {
            dmg = minDamage;
        } else {
            double t = (distance - falloffStart) / (falloffEnd - falloffStart);
            dmg = baseDamage + (minDamage - baseDamage) * t;
        }
        return headshot ? dmg * headshotMultiplier : dmg;
    }
}
