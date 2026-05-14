package com.fooweapons;

import com.fooweapons.fire.FireListener;
import com.fooweapons.fire.FireService;
import com.fooweapons.item.ItemState;
import com.fooweapons.item.PdcKeys;
import com.fooweapons.item.WeaponItemFactory;
import com.fooweapons.reload.ReloadListener;
import com.fooweapons.weapon.WeaponRegistry;
import org.bukkit.plugin.java.JavaPlugin;

public final class FooWeaponsPlugin extends JavaPlugin {
    private WeaponRegistry weapons;
    private PdcKeys pdcKeys;
    private WeaponItemFactory itemFactory;
    private ItemState itemState;
    private ReloadListener reloadListener;

    @Override
    public void onEnable() {
        this.pdcKeys = new PdcKeys(this);
        this.itemFactory = new WeaponItemFactory(pdcKeys);
        this.itemState = new ItemState(pdcKeys);
        this.weapons = new WeaponRegistry();
        try {
            weapons.loadFromClasspath(getClassLoader(), "weapons/pistol_01.yml");
        } catch (Exception e) {
            getLogger().severe("Failed to load weapons: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.reloadListener = new ReloadListener(this, weapons, itemFactory, itemState);
        getServer().getPluginManager().registerEvents(reloadListener, this);

        FireService fireService = new FireService(itemState);
        getServer().getPluginManager().registerEvents(
            new FireListener(weapons, itemFactory, fireService, reloadListener), this);
        getLogger().info("fooWeapons enabled with " + weapons.size() + " weapon(s).");
    }

    public WeaponRegistry weapons() { return weapons; }
    public WeaponItemFactory itemFactory() { return itemFactory; }
    public ItemState itemState() { return itemState; }
    public ReloadListener reloadListener() { return reloadListener; }
}
