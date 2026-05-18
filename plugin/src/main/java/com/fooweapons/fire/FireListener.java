package com.fooweapons.fire;

import com.fooweapons.fire.mode.FireMode;
import com.fooweapons.item.ItemState;
import com.fooweapons.item.WeaponItemFactory;
import com.fooweapons.reload.ReloadListener;
import com.fooweapons.weapon.Weapon;
import com.fooweapons.weapon.WeaponRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import java.util.Optional;

public final class FireListener implements Listener {
    private final WeaponRegistry registry;
    private final WeaponItemFactory factory;
    private final FireService fireService;
    private final ReloadListener reloadListener;
    private final ItemState state;

    public FireListener(WeaponRegistry registry, WeaponItemFactory factory,
                        FireService fireService, ReloadListener reloadListener,
                        ItemState state) {
        this.registry = registry;
        this.factory = factory;
        this.fireService = fireService;
        this.reloadListener = reloadListener;
        this.state = state;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != null && event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (reloadListener.isReloading(player.getUniqueId())) return;
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!factory.isWeapon(stack)) return;
        event.setCancelled(true);
        String id = factory.getWeaponId(stack);
        Optional<Weapon> weapon = registry.get(id);
        if (weapon.isEmpty()) return;
        // AUTO-mode weapons fire from the arm-swing tracker, not from click events.
        if (state.getFireMode(stack, weapon.get().defaultMode()) == FireMode.AUTO) return;
        fireService.tryFire(player, stack, weapon.get());
    }
}
