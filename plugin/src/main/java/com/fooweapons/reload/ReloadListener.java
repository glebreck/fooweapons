package com.fooweapons.reload;

import com.fooweapons.feedback.SoundService;
import com.fooweapons.item.ItemState;
import com.fooweapons.item.WeaponItemFactory;
import com.fooweapons.weapon.Weapon;
import com.fooweapons.weapon.WeaponRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ReloadListener implements Listener {
    private final Plugin plugin;
    private final WeaponRegistry registry;
    private final WeaponItemFactory factory;
    private final ItemState state;
    private final SoundService sounds;
    private final Set<UUID> reloading = new HashSet<>();

    public ReloadListener(Plugin plugin, WeaponRegistry registry,
                          WeaponItemFactory factory, ItemState state, SoundService sounds) {
        this.plugin = plugin;
        this.registry = registry;
        this.factory = factory;
        this.state = state;
        this.sounds = sounds;
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        if (!factory.isWeapon(main)) return;
        event.setCancelled(true);

        if (reloading.contains(player.getUniqueId())) return;
        String id = factory.getWeaponId(main);
        Weapon weapon = registry.get(id).orElse(null);
        if (weapon == null) return;

        int current = state.getAmmo(main);
        if (current >= weapon.magSize()) return;

        reloading.add(player.getUniqueId());
        sounds.playReload(player, weapon.reloadSoundId());
        player.sendActionBar(Component.text("Reloading..."));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            reloading.remove(player.getUniqueId());
            ItemStack currentHand = player.getInventory().getItemInMainHand();
            if (factory.isWeapon(currentHand) && id.equals(factory.getWeaponId(currentHand))) {
                state.setAmmo(currentHand, weapon.magSize());
            }
        }, weapon.reloadTimeTicks());
    }

    public boolean isReloading(UUID playerId) {
        return reloading.contains(playerId);
    }
}
