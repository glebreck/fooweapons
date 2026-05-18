package com.fooweapons.weapon;

public record MuzzleFlashConfig(
    int flameCount,
    int smokeCount,
    double forwardOffset
) {
    public static MuzzleFlashConfig defaults() {
        return new MuzzleFlashConfig(5, 2, 1.5);
    }
}
