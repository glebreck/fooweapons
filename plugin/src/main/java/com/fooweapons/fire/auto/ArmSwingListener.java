package com.fooweapons.fire.auto;

import com.fooweapons.fire.mode.FireMode;
import com.fooweapons.item.ItemState;
import com.fooweapons.item.WeaponItemFactory;
import com.fooweapons.weapon.Weapon;
import com.fooweapons.weapon.WeaponRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.inventory.ItemStack;
import java.util.Optional;

public final class ArmSwingListener implements Listener {
    private final WeaponRegistry registry;
    private final WeaponItemFactory factory;
    private final ItemState state;
    private final AutoFireTracker tracker;

    public ArmSwingListener(WeaponRegistry registry, WeaponItemFactory factory,
                            ItemState state, AutoFireTracker tracker) {
        this.registry = registry;
        this.factory = factory;
        this.state = state;
        this.tracker = tracker;
    }

    @EventHandler
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!factory.isWeapon(stack)) return;
        Optional<Weapon> w = registry.get(factory.getWeaponId(stack));
        if (w.isEmpty()) return;
        if (state.getFireMode(stack, w.get().defaultMode()) != FireMode.AUTO) return;
        tracker.markSwing(
            player.getUniqueId(),
            w.get().id(),
            player.getInventory().getHeldItemSlot()
        );
    }
}
