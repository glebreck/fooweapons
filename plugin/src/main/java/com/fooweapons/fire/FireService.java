package com.fooweapons.fire;

import com.fooweapons.feedback.MuzzleFlashService;
import com.fooweapons.feedback.SoundService;
import com.fooweapons.item.ItemState;
import com.fooweapons.weapon.Weapon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import java.util.Map;
import java.util.Random;

public final class FireService {
    private final ItemState state;
    private final SoundService sounds;
    private final MuzzleFlashService muzzleFlash;
    private final Random random = new Random();

    public FireService(ItemState state, SoundService sounds, MuzzleFlashService muzzleFlash) {
        this.state = state;
        this.sounds = sounds;
        this.muzzleFlash = muzzleFlash;
    }

    public Result tryFire(Player player, ItemStack stack, Weapon weapon) {
        long now = System.currentTimeMillis();
        if (!FireRateCooldown.canFire(state.getLastFiredMs(stack), now, weapon.fireCooldownMillis())) {
            return Result.COOLDOWN;
        }
        int ammo = state.getAmmo(stack);
        if (ammo <= 0) {
            sounds.playDryFire(player, weapon.dryFireSoundId());
            return Result.DRY;
        }

        state.setAmmo(stack, ammo - 1);
        state.setLastFiredMs(stack, now);
        sounds.playFire(player, weapon.fireSoundId());
        muzzleFlash.spawn(player, weapon);

        double spread = weapon.baseSpreadDegrees();
        if (player.getVelocity().lengthSquared() > 0.01) {
            spread += weapon.movingSpreadPenaltyDegrees();
        }
        Vector aim = player.getEyeLocation().getDirection();

        PelletDamageAggregator<LivingEntity> aggregator = new PelletDamageAggregator<>();
        for (int i = 0; i < weapon.pelletsPerShot(); i++) {
            Vector dir = SpreadCalculator.applySpread(aim, spread, random);
            Hitscan.Hit hit = Hitscan.fire(player, dir, weapon.range());
            if (hit == null) continue;
            double dmg = DamageCalculator.compute(
                weapon.damage(),
                hit.distance(),
                weapon.falloffStart(),
                weapon.falloffEnd(),
                Math.max(1.0, weapon.damage() * 0.25),
                weapon.headshotMultiplier(),
                hit.headshot()
            );
            aggregator.add(hit.target(), dmg);
        }
        for (Map.Entry<LivingEntity, Double> e : aggregator.totals().entrySet()) {
            e.getKey().damage(e.getValue(), player);
        }
        return Result.FIRED;
    }

    public enum Result { FIRED, COOLDOWN, DRY }
}
