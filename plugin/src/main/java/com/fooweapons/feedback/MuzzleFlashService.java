package com.fooweapons.feedback;

import com.fooweapons.weapon.MuzzleFlashConfig;
import com.fooweapons.weapon.Weapon;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class MuzzleFlashService {
    public void spawn(Player player, Weapon weapon) {
        MuzzleFlashConfig cfg = weapon.muzzleFlash();
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().multiply(cfg.forwardOffset());
        Location origin = eye.clone().add(forward);
        World world = player.getWorld();
        if (cfg.flameCount() > 0) {
            world.spawnParticle(Particle.FLAME, origin, cfg.flameCount(),
                0.0, 0.0, 0.0, 0.0);
        }
        if (cfg.smokeCount() > 0) {
            world.spawnParticle(Particle.SMOKE, origin, cfg.smokeCount(),
                0.05, 0.05, 0.05, 0.01);
        }
    }
}
