package com.fooweapons.fire;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FireRateCooldownTest {
    @Test
    void canFireWhenNeverFired() {
        assertTrue(FireRateCooldown.canFire(0L, 1000L, 250L));
    }

    @Test
    void cannotFireDuringCooldown() {
        assertFalse(FireRateCooldown.canFire(1000L, 1100L, 250L));
    }

    @Test
    void canFireAfterCooldown() {
        assertTrue(FireRateCooldown.canFire(1000L, 1300L, 250L));
    }

    @Test
    void canFireAtExactCooldownBoundary() {
        assertTrue(FireRateCooldown.canFire(1000L, 1250L, 250L));
    }
}
