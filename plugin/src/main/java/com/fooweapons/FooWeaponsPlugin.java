package com.fooweapons;

import com.fooweapons.weapon.WeaponRegistry;
import org.bukkit.plugin.java.JavaPlugin;

public final class FooWeaponsPlugin extends JavaPlugin {
    private final WeaponRegistry weapons = new WeaponRegistry();

    @Override
    public void onEnable() {
        try {
            weapons.loadFromClasspath(getClassLoader(), "weapons/pistol_01.yml");
            getLogger().info("Loaded " + weapons.size() + " weapon(s).");
        } catch (Exception e) {
            getLogger().severe("Failed to load weapons: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("fooWeapons enabled.");
    }

    public WeaponRegistry weapons() {
        return weapons;
    }
}
