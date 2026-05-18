package com.fooweapons.item;

import com.fooweapons.weapon.Weapon;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

public final class WeaponItemFactory {
    private final PdcKeys keys;

    public WeaponItemFactory(PdcKeys keys) {
        this.keys = keys;
    }

    public ItemStack create(Weapon weapon) {
        ItemStack stack = new ItemStack(Material.CROSSBOW);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(weapon.displayName()));
        meta.setCustomModelData(weapon.customModelData());
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.weaponId, keys.stringType(), weapon.id());
        pdc.set(keys.ammo, keys.intType(), weapon.magSize());
        pdc.set(keys.lastFiredMs, keys.longType(), 0L);
        pdc.set(keys.fireMode, keys.stringType(), weapon.defaultMode().yamlName());
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isWeapon(ItemStack stack) {
        if (stack == null || stack.getType() != Material.CROSSBOW) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(keys.weaponId, keys.stringType());
    }

    public String getWeaponId(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(keys.weaponId, keys.stringType());
    }
}
