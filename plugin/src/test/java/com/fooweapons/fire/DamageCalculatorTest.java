package com.fooweapons.fire;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DamageCalculatorTest {
    @Test
    void fullDamageWithinFalloffStart() {
        double d = DamageCalculator.compute(10.0, 5.0, 25.0, 50.0, 10.0, 2.0, false);
        assertEquals(10.0, d, 0.0001);
    }

    @Test
    void halfDamageAtMidpointOfFalloff() {
        double d = DamageCalculator.compute(10.0, 30.0, 20.0, 40.0, 0.0, 2.0, false);
        assertEquals(5.0, d, 0.0001);
    }

    @Test
    void minDamageBeyondFalloffEnd() {
        double d = DamageCalculator.compute(10.0, 60.0, 20.0, 40.0, 1.0, 2.0, false);
        assertEquals(1.0, d, 0.0001);
    }

    @Test
    void headshotMultiplierApplies() {
        double d = DamageCalculator.compute(10.0, 5.0, 25.0, 50.0, 10.0, 2.5, true);
        assertEquals(25.0, d, 0.0001);
    }
}
