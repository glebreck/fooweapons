package com.fooweapons.item;

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
