package com.fooweapons.hud;

import com.fooweapons.item.ItemState;
import com.fooweapons.item.WeaponItemFactory;
import com.fooweapons.weapon.Weapon;
import com.fooweapons.weapon.WeaponRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class HudService {
    private final Plugin plugin;
    private final WeaponRegistry registry;
    private final WeaponItemFactory factory;
    private final ItemState state;
    private BukkitTask task;

    public HudService(Plugin plugin, WeaponRegistry registry,
                      WeaponItemFactory factory, ItemState state) {
        this.plugin = plugin;
        this.registry = registry;
        this.factory = factory;
        this.state = state;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack stack = player.getInventory().getItemInMainHand();
            if (!factory.isWeapon(stack)) continue;
            Weapon w = registry.get(factory.getWeaponId(stack)).orElse(null);
            if (w == null) continue;
            int ammo = state.getAmmo(stack);
            player.sendActionBar(Component.text(w.displayName() + "  ", NamedTextColor.GRAY)
                .append(Component.text(ammo + "/" + w.magSize(),
                    ammo == 0 ? NamedTextColor.RED : NamedTextColor.WHITE)));
        }
    }
}
