package io.github.ihormel.mobbanner;

import java.util.List;
import java.util.Locale;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

final class BannerCommand implements TabExecutor {
    private final Server server;
    private final BannerItems items;

    BannerCommand(Server server, BannerItems items) {
        this.server = server;
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mobbanner.give")) {
            sender.sendMessage("You do not have permission to give mob banners.");
            return true;
        }
        if (args.length != 3 || !args[0].equalsIgnoreCase("give")) return false;
        Player target = server.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("That player is not online.");
            return true;
        }
        EntityType type = MobTypes.SUPPORTED.stream()
                .filter(mob -> mob.name().equalsIgnoreCase(args[2])).findFirst().orElse(null);
        if (type == null) {
            sender.sendMessage("Unsupported mob type. Use tab completion; multiword types use underscores.");
            return true;
        }
        items.give(target, type);
        sender.sendMessage("Gave a " + MobTypes.name(type) + " Ward Banner to " + target.getName() + ".");
        target.sendMessage("An administrator gave you a " + MobTypes.name(type) + " Ward Banner.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("mobbanner.give") || args.length == 0) return List.of();
        List<String> options = switch (args.length) {
            case 1 -> List.of("give");
            case 2 -> args[0].equalsIgnoreCase("give") ? server.getOnlinePlayers().stream()
                    .filter(player -> !(sender instanceof Player viewer) || viewer.canSee(player))
                    .map(Player::getName).sorted().toList() : List.of();
            case 3 -> args[0].equalsIgnoreCase("give") ? MobTypes.SUPPORTED.stream()
                    .map(type -> type.name().toLowerCase(Locale.ROOT)).sorted().toList() : List.of();
            default -> List.of();
        };
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
