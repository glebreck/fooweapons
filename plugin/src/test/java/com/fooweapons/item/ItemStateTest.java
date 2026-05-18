package com.fooweapons.item;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import com.fooweapons.fire.mode.FireMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemStateTest {
    private PluginMock plugin;
    private PdcKeys keys;
    private ItemState state;
    private ItemStack stack;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        keys = new PdcKeys(plugin);
        state = new ItemState(keys);
        stack = new ItemStack(Material.CROSSBOW);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void roundTripsAmmoAndLastFired() {
        state.setAmmo(stack, 7);
        state.setLastFiredMs(stack, 12345L);
        assertEquals(7, state.getAmmo(stack));
        assertEquals(12345L, state.getLastFiredMs(stack));
    }

    @Test
    void roundTripsFireMode() {
        state.setFireMode(stack, FireMode.AUTO);
        assertEquals(FireMode.AUTO, state.getFireMode(stack, FireMode.SEMI));
    }

    @Test
    void missingTagReturnsFallbackAndPersistsIt() {
        // First call reads from a stack with no PDC tag — should return fallback.
        assertEquals(FireMode.SEMI, state.getFireMode(stack, FireMode.SEMI));
        // Second call with a DIFFERENT fallback should still see SEMI, proving the first
        // call wrote SEMI back to the PDC (lazy migration of legacy v0.1.0 items).
        assertEquals(FireMode.SEMI, state.getFireMode(stack, FireMode.AUTO));
    }

    @Test
    void invalidTagCoercesToFallbackAndPersistsIt() {
        // Plant a bogus value directly into the PDC.
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(keys.fireMode, keys.stringType(), "bogus_mode");
        stack.setItemMeta(meta);

        assertEquals(FireMode.SEMI, state.getFireMode(stack, FireMode.SEMI));
        // The bogus value should have been overwritten with SEMI.
        assertEquals(FireMode.SEMI, state.getFireMode(stack, FireMode.AUTO));
    }
}
