package com.fooweapons.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.persistence.PersistentDataType;

public final class PdcKeys {
    public final NamespacedKey weaponId;
    public final NamespacedKey ammo;
    public final NamespacedKey lastFiredMs;

    public PdcKeys(Plugin plugin) {
        this.weaponId = new NamespacedKey(plugin, "weapon_id");
        this.ammo = new NamespacedKey(plugin, "ammo");
        this.lastFiredMs = new NamespacedKey(plugin, "last_fired_ms");
    }

    public PersistentDataType<String, String> stringType() {
        return PersistentDataType.STRING;
    }

    public PersistentDataType<Integer, Integer> intType() {
        return PersistentDataType.INTEGER;
    }

    public PersistentDataType<Long, Long> longType() {
        return PersistentDataType.LONG;
    }
}
