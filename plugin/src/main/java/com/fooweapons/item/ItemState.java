package com.fooweapons.item;

import com.fooweapons.fire.mode.FireMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

public final class ItemState {
    private final PdcKeys keys;

    public ItemState(PdcKeys keys) {
        this.keys = keys;
    }

    public int getAmmo(ItemStack stack) {
        return read(stack, pdc -> pdc.getOrDefault(keys.ammo, keys.intType(), 0));
    }

    public void setAmmo(ItemStack stack, int ammo) {
        write(stack, pdc -> pdc.set(keys.ammo, keys.intType(), ammo));
    }

    public long getLastFiredMs(ItemStack stack) {
        return read(stack, pdc -> pdc.getOrDefault(keys.lastFiredMs, keys.longType(), 0L));
    }

    public void setLastFiredMs(ItemStack stack, long ms) {
        write(stack, pdc -> pdc.set(keys.lastFiredMs, keys.longType(), ms));
    }

    /**
     * Reads the fire mode from PDC. If the tag is missing or its value is not a known
     * FireMode, returns {@code fallback} and writes that value back to the PDC (lazy migration).
     */
    public FireMode getFireMode(ItemStack stack, FireMode fallback) {
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String raw = pdc.get(keys.fireMode, keys.stringType());
        if (raw == null) {
            pdc.set(keys.fireMode, keys.stringType(), fallback.yamlName());
            stack.setItemMeta(meta);
            return fallback;
        }
        try {
            return FireMode.fromYamlName(raw);
        } catch (IllegalArgumentException unknown) {
            pdc.set(keys.fireMode, keys.stringType(), fallback.yamlName());
            stack.setItemMeta(meta);
            return fallback;
        }
    }

    public void setFireMode(ItemStack stack, FireMode mode) {
        write(stack, pdc -> pdc.set(keys.fireMode, keys.stringType(), mode.yamlName()));
    }

    private <T> T read(ItemStack stack, java.util.function.Function<PersistentDataContainer, T> fn) {
        ItemMeta meta = stack.getItemMeta();
        return fn.apply(meta.getPersistentDataContainer());
    }

    private void write(ItemStack stack, java.util.function.Consumer<PersistentDataContainer> fn) {
        ItemMeta meta = stack.getItemMeta();
        fn.accept(meta.getPersistentDataContainer());
        stack.setItemMeta(meta);
    }
}
