package com.fooweapons;

import org.bukkit.plugin.java.JavaPlugin;

public final class FooWeaponsPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("fooWeapons enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("fooWeapons disabled.");
    }
}
