package com.fooweapons.feedback;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

public final class SoundService {
    public void playFire(Player player, String soundId) {
        play(player, soundId, 1.0f, 1.0f);
    }

    public void playReload(Player player, String soundId) {
        play(player, soundId, 0.8f, 1.0f);
    }

    public void playDryFire(Player player, String soundId) {
        play(player, soundId, 0.5f, 1.2f);
    }

    private void play(Player player, String soundId, float volume, float pitch) {
        Location loc = player.getLocation();
        player.getWorld().playSound(loc, soundId, SoundCategory.PLAYERS, volume, pitch);
    }
}
