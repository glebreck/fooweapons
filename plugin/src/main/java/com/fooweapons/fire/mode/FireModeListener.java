package com.fooweapons.fire.mode;

import com.fooweapons.feedback.SoundService;
import com.fooweapons.item.ItemState;
import com.fooweapons.item.WeaponItemFactory;
import com.fooweapons.weapon.Weapon;
import com.fooweapons.weapon.WeaponRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import java.util.Optional;

public final class FireModeListener implements Listener {
    private final WeaponRegistry registry;
    private final WeaponItemFactory factory;
    private final ItemState state;
    private final SoundService sounds;

    public FireModeListener(WeaponRegistry registry, WeaponItemFactory factory,
                            ItemState state, SoundService sounds) {
        this.registry = registry;
        this.factory = factory;
        this.state = state;
        this.sounds = sounds;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        ItemStack stack = event.getItemDrop().getItemStack();
        if (!factory.isWeapon(stack)) return;
        Optional<Weapon> w = registry.get(factory.getWeaponId(stack));
        if (w.isEmpty()) return;
        event.setCancelled(true);

        Weapon weapon = w.get();
        if (weapon.modes().size() <= 1) {
            sounds.playDeniedClick(player);
            return;
        }
        FireMode current = state.getFireMode(stack, weapon.defaultMode());
        FireMode next = FireModeCycle.next(current, weapon.modes());
        state.setFireMode(stack, next);
        sounds.playClick(player);
    }
}
