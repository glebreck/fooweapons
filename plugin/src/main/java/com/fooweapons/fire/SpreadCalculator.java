package com.fooweapons.fire;

import org.bukkit.util.Vector;
import java.util.Random;

public final class SpreadCalculator {
    private SpreadCalculator() {}

    public static Vector applySpread(Vector direction, double maxDegrees, Random random) {
        if (maxDegrees <= 0.0) return direction.clone();
        Vector dir = direction.clone().normalize();
        double phi = random.nextDouble() * 2.0 * Math.PI;
        double theta = Math.toRadians(random.nextDouble() * maxDegrees);

        Vector perp1;
        if (Math.abs(dir.getY()) < 0.9) {
            perp1 = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        } else {
            perp1 = dir.clone().crossProduct(new Vector(1, 0, 0)).normalize();
        }
        Vector perp2 = dir.clone().crossProduct(perp1).normalize();

        double sinTheta = Math.sin(theta);
        double cosTheta = Math.cos(theta);
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);

        return dir.multiply(cosTheta)
            .add(perp1.multiply(sinTheta * cosPhi))
            .add(perp2.multiply(sinTheta * sinPhi));
    }
}
