package com.fooweapons.fire.auto;

import com.fooweapons.fire.FireRateCooldown;
import com.fooweapons.fire.FireService;
import com.fooweapons.fire.mode.FireMode;
import com.fooweapons.item.ItemState;
import com.fooweapons.item.WeaponItemFactory;
import com.fooweapons.reload.ReloadListener;
import com.fooweapons.weapon.Weapon;
import com.fooweapons.weapon.WeaponRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class AutoFireTracker {
    private static final long SWING_TIMEOUT_MS = 250L;

    private final Plugin plugin;
    private final WeaponRegistry registry;
    private final WeaponItemFactory factory;
    private final ItemState state;
    private final FireService fireService;
    private final ReloadListener reloadListener;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private BukkitTask task;

    public AutoFireTracker(Plugin plugin, WeaponRegistry registry, WeaponItemFactory factory,
                           ItemState state, FireService fireService, ReloadListener reloadListener) {
        this.plugin = plugin;
        this.registry = registry;
        this.factory = factory;
        this.state = state;
        this.fireService = fireService;
        this.reloadListener = reloadListener;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        entries.clear();
    }

    /** Called by ArmSwingListener whenever the player swings while holding an AUTO-mode weapon. */
    public void markSwing(UUID playerId, String weaponId, int slot) {
        entries.put(playerId, new Entry(weaponId, slot, System.currentTimeMillis()));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Entry> mapEntry = it.next();
            Entry entry = mapEntry.getValue();
            Player player = Bukkit.getPlayer(mapEntry.getKey());
            if (player == null) { it.remove(); continue; }
            if (now - entry.lastSwingMs > SWING_TIMEOUT_MS) { it.remove(); continue; }

            ItemStack stack = player.getInventory().getItem(entry.slot);
            if (!factory.isWeapon(stack) || !entry.weaponId.equals(factory.getWeaponId(stack))) {
                it.remove();
                continue;
            }
            if (player.getInventory().getHeldItemSlot() != entry.slot) {
                it.remove();
                continue;
            }
            Weapon weapon = registry.get(entry.weaponId).orElse(null);
            if (weapon == null) { it.remove(); continue; }
            if (state.getFireMode(stack, weapon.defaultMode()) != FireMode.AUTO) {
                it.remove();
                continue;
            }
            if (reloadListener.isReloading(player.getUniqueId())) continue;
            if (!FireRateCooldown.canFire(state.getLastFiredMs(stack), now, weapon.fireCooldownMillis())) continue;

            fireService.tryFire(player, stack, weapon);
        }
    }

    private record Entry(String weaponId, int slot, long lastSwingMs) {}
}
