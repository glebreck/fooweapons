package com.fooweapons.fire;

public final class FireRateCooldown {
    private FireRateCooldown() {}

    public static boolean canFire(long lastFiredMs, long nowMs, long cooldownMs) {
        return (nowMs - lastFiredMs) >= cooldownMs;
    }
}
