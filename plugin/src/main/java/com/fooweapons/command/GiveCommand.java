package com.fooweapons.command;

import com.fooweapons.item.WeaponItemFactory;
import com.fooweapons.weapon.Weapon;
import com.fooweapons.weapon.WeaponRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Optional;

public final class GiveCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("give");

    private final WeaponRegistry registry;
    private final WeaponItemFactory factory;

    public GiveCommand(WeaponRegistry registry, WeaponItemFactory factory) {
        this.registry = registry;
        this.factory = factory;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text("Usage: /fooweapons give <weapon_id>", NamedTextColor.YELLOW));
            return true;
        }
        Optional<Weapon> weapon = registry.get(args[1]);
        if (weapon.isEmpty()) {
            sender.sendMessage(Component.text("Unknown weapon: " + args[1], NamedTextColor.RED));
            return true;
        }
        ItemStack stack = factory.create(weapon.get());
        player.getInventory().addItem(stack);
        sender.sendMessage(Component.text("Gave " + weapon.get().displayName(), NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                     @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return prefixFilter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return prefixFilter(registry.ids().stream().sorted().toList(), args[1]);
        }
        return List.of();
    }

    private static List<String> prefixFilter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
    }
}
