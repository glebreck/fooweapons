package com.fooweapons.fire;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class SpreadCalculatorTest {
    @Test
    void zeroSpreadReturnsExactDirection() {
        Vector base = new Vector(1, 0, 0);
        Vector result = SpreadCalculator.applySpread(base, 0.0, new Random(42));
        assertEquals(1.0, result.getX(), 0.0001);
        assertEquals(0.0, result.getY(), 0.0001);
        assertEquals(0.0, result.getZ(), 0.0001);
    }

    @Test
    void spreadStaysWithinExpectedConeRoughly() {
        Vector base = new Vector(1, 0, 0);
        Random r = new Random(42);
        for (int i = 0; i < 100; i++) {
            Vector result = SpreadCalculator.applySpread(base, 5.0, r);
            double dot = base.dot(result.clone().normalize());
            double angleDeg = Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));
            assertTrue(angleDeg <= 5.5, "angle " + angleDeg + " exceeded 5°");
        }
    }
}
