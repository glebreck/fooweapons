package com.fooweapons;

import com.fooweapons.command.GiveCommand;
import com.fooweapons.feedback.MuzzleFlashService;
import com.fooweapons.feedback.SoundService;
import com.fooweapons.fire.FireListener;
import com.fooweapons.fire.FireService;
import com.fooweapons.hud.HudService;
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
    private HudService hudService;
    private com.fooweapons.fire.auto.AutoFireTracker autoFireTracker;

    @Override
    public void onEnable() {
        this.pdcKeys = new PdcKeys(this);
        this.itemFactory = new WeaponItemFactory(pdcKeys);
        this.itemState = new ItemState(pdcKeys);
        this.weapons = new WeaponRegistry();
        try {
            weapons.loadFromClasspath(getClassLoader(),
                "weapons/pistol_01.yml",
                "weapons/rifle_01.yml",
                "weapons/smg_01.yml");
        } catch (Exception e) {
            getLogger().severe("Failed to load weapons: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        SoundService soundService = new SoundService();
        MuzzleFlashService muzzleFlashService = new MuzzleFlashService();

        this.reloadListener = new ReloadListener(this, weapons, itemFactory, itemState, soundService);
        getServer().getPluginManager().registerEvents(reloadListener, this);

        FireService fireService = new FireService(itemState, soundService, muzzleFlashService);
        getServer().getPluginManager().registerEvents(
            new FireListener(weapons, itemFactory, fireService, reloadListener, itemState), this);

        getServer().getPluginManager().registerEvents(
            new com.fooweapons.fire.mode.FireModeListener(weapons, itemFactory, itemState, soundService), this);

        this.autoFireTracker = new com.fooweapons.fire.auto.AutoFireTracker(
            this, weapons, itemFactory, itemState, fireService, reloadListener);
        autoFireTracker.start();
        getServer().getPluginManager().registerEvents(
            new com.fooweapons.fire.auto.ArmSwingListener(weapons, itemFactory, itemState, autoFireTracker), this);

        this.hudService = new HudService(this, weapons, itemFactory, itemState);
        hudService.start();

        GiveCommand giveCommand = new GiveCommand(weapons, itemFactory);
        getCommand("fooweapons").setExecutor(giveCommand);
        getCommand("fooweapons").setTabCompleter(giveCommand);

        getLogger().info("fooWeapons enabled with " + weapons.size() + " weapon(s).");
    }

    @Override
    public void onDisable() {
        if (hudService != null) hudService.stop();
        if (autoFireTracker != null) autoFireTracker.stop();
    }

    public WeaponRegistry weapons() { return weapons; }
    public WeaponItemFactory itemFactory() { return itemFactory; }
    public ItemState itemState() { return itemState; }
    public ReloadListener reloadListener() { return reloadListener; }
}
