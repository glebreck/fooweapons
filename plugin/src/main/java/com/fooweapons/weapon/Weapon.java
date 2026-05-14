package com.fooweapons.weapon;

public record Weapon(
    String id,
    String displayName,
    int customModelData,
    double damage,
    double headshotMultiplier,
    double range,
    double falloffStart,
    double falloffEnd,
    double baseSpreadDegrees,
    double movingSpreadPenaltyDegrees,
    int fireRatePerSecond,
    int magSize,
    int reloadTimeTicks,
    String fireSoundId,
    String reloadSoundId,
    String dryFireSoundId
) {
    public long fireCooldownMillis() {
        return 1000L / Math.max(1, fireRatePerSecond);
    }
}
