package io.github.ihormel.tpcost;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

final class TeleportCommands implements TabExecutor, Listener {
    private final Server server;
    private final TeleportRequests requests;
    private final Supplier<TeleportPricing> pricing;
    private final BooleanSupplier quotesEnabled;
    private final RequestPreferences preferences;

    TeleportCommands(Server server, TeleportRequests requests) {
        this(server, requests, () -> TeleportPricing.DEFAULT);
    }

    TeleportCommands(Server server, TeleportRequests requests, Supplier<TeleportPricing> pricing) {
        this(server, requests, pricing, () -> true, null);
    }

    TeleportCommands(Server server, TeleportRequests requests, Supplier<TeleportPricing> pricing,
            BooleanSupplier quotesEnabled, RequestPreferences preferences) {
        this.server = server;
        this.requests = requests;
        this.pricing = pricing;
        this.quotesEnabled = quotesEnabled;
        this.preferences = preferences;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use teleport commands.");
            return true;
        }
        if (!player.hasPermission("tpcost.use")) {
            tell(player, "You do not have permission to use teleport commands.");
            return true;
        }
        expireRequests();
        switch (command.getName()) {
            case "tpclose", "tpopen" -> {
                if (args.length != 0) return false;
                boolean close = command.getName().equals("tpclose");
                try {
                    preferences.setClosed(player.getUniqueId(), close);
                } catch (IOException exception) {
                    server.getLogger().log(Level.SEVERE, "Could not save teleport request preference.", exception);
                    tell(player, "Could not save your preference. Nothing changed; contact an admin.");
                    return true;
                }
                if (close) {
                    requests.incoming(player.getUniqueId()).forEach(request -> {
                        requests.remove(request.sender());
                        notify(request.sender(), player.getName() + " closed incoming teleport requests.");
                    });
                }
                tell(player, "Incoming teleport requests are now " + (close ? "closed" : "open")
                        + ". This choice is saved across restarts. Use /" + (close ? "tpopen" : "tpclose") + " to change it.");
            }
            case "tpa", "tpcost" -> {
                if (args.length != 1) {
                    return false;
                }
                requestOrQuote(player, args[0], command.getName().equals("tpcost"));
            }
            case "tpaccept", "tpdeny" -> {
                if (args.length > 1) {
                    return false;
                }
                respond(player, args, command.getName().equals("tpaccept"));
            }
            case "tpcancel" -> {
                if (args.length != 0) {
                    return false;
                }
                var removed = requests.remove(player.getUniqueId());
                if (removed.isEmpty()) {
                    tell(player, "You have no outgoing teleport request.");
                } else {
                    tell(player, "Teleport request cancelled.");
                    notify(removed.get().target(), player.getName() + " cancelled their teleport request.");
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void requestOrQuote(Player sender, String targetName, boolean quoteOnly) {
        if (quoteOnly && !quotesEnabled.getAsBoolean()) {
            tell(sender, "Teleport cost lookup is disabled by the server.");
            return;
        }
        Player target = server.getPlayerExact(targetName);
        if (target == null || !sender.canSee(target)) {
            tell(sender, "That player is not online or visible to you.");
            return;
        }
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            tell(sender, "You cannot teleport to yourself.");
            return;
        }
        if (preferences != null && preferences.isClosed(target.getUniqueId())) {
            tell(sender, "That player is not accepting teleport requests.");
            return;
        }
        int cost = TeleportCost.between(sender.getLocation(), target.getLocation(), pricing.get());
        if (quoteOnly) {
            tell(sender, "Teleporting to " + target.getName() + " currently costs " + cost + " levels.");
            return;
        }
        if (!ready(sender, target)) {
            return;
        }
        if (quotesEnabled.getAsBoolean() && sender.getLevel() < cost) {
            tell(sender, "You need " + cost + " levels to teleport; you have " + sender.getLevel() + ".");
            return;
        }
        requests.add(sender.getUniqueId(), target.getUniqueId()).ifPresent(previous -> {
            notify(previous.target(), sender.getName() + " replaced their previous teleport request.");
        });
        tell(sender, "Request sent to " + target.getName() + ". "
                + (quotesEnabled.getAsBoolean() ? "Current cost: " + cost + " levels (recalculated on acceptance). "
                        : "You pay XP levels on acceptance. ")
                + "Expires in 60 seconds.");
        target.sendMessage(Component.text("[tpcost] " + sender.getName() + " wants to teleport to you. ")
                .append(Component.text("[Accept]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/tpcost:tpaccept " + sender.getName()))
                        .hoverEvent(Component.text("Accept " + sender.getName() + "'s teleport request")))
                .append(Component.text(" "))
                .append(Component.text("[Deny]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/tpcost:tpdeny " + sender.getName())))
                .append(Component.text(" Expires in 60 seconds. You can also use /tpaccept or /tpdeny "
                        + sender.getName() + ".")));
    }

    private void respond(Player target, String[] args, boolean accept) {
        if (accept && preferences != null && preferences.isClosed(target.getUniqueId())) {
            tell(target, "Your incoming teleport requests are closed. Use /tpopen first.");
            return;
        }
        var incoming = requests.incoming(target.getUniqueId());
        if (args.length == 0 && incoming.size() > 1) {
            tell(target, "You have multiple requests. Specify a player with /"
                    + (accept ? "tpaccept" : "tpdeny") + " <player>.");
            return;
        }
        var selected = incoming.stream().filter(request -> {
            Player sender = server.getPlayer(request.sender());
            return sender != null && (args.length == 0 || sender.getName().equalsIgnoreCase(args[0]));
        }).findFirst();
        if (selected.isEmpty()) {
            tell(target, "No matching teleport request.");
            return;
        }
        var request = selected.get();
        requests.remove(request.sender());
        Player sender = server.getPlayer(request.sender());
        if (sender == null) {
            tell(target, "The requesting player is no longer online.");
            return;
        }
        if (!accept) {
            tell(target, "Teleport request denied.");
            tell(sender, target.getName() + " denied your teleport request.");
            return;
        }
        if (!sender.hasPermission("tpcost.use") || !sender.canSee(target) || !target.canSee(sender)) {
            tell(target, "Teleport cancelled: permission or player visibility changed.");
            tell(sender, "Teleport cancelled: permission or player visibility changed.");
            return;
        }
        if (!ready(sender, target)) {
            tell(target, "Teleport cancelled: one of the players is dead or riding a vehicle.");
            return;
        }
        var destination = target.getLocation();
        int cost = TeleportCost.between(sender.getLocation(), destination, pricing.get());
        if (sender.getLevel() < cost) {
            tell(sender, "Teleport cancelled: you now need " + cost + " levels; you have " + sender.getLevel() + ".");
            tell(target, "Teleport cancelled: " + sender.getName() + " does not have enough levels.");
            return;
        }
        // An online player's destination chunk is already loaded; keep payment and teleport on the server thread.
        if (!sender.teleport(destination, TeleportCause.PLUGIN)) {
            tell(sender, "Teleport failed or was cancelled. No levels were charged.");
            tell(target, "Teleport failed or was cancelled. No levels were charged.");
            return;
        }
        sender.giveExpLevels(-cost);
        tell(sender, "Teleported to " + target.getName() + " for " + cost + " levels.");
        tell(target, sender.getName() + " teleported to you.");
    }

    private boolean ready(Player sender, Player target) {
        if (sender.isDead() || target.isDead() || sender.isInsideVehicle() || target.isInsideVehicle()) {
            tell(sender, "Both players must be alive and outside vehicles.");
            return false;
        }
        return true;
    }

    void expireRequests() {
        requests.expire().forEach(request -> {
            notify(request.sender(), "Your teleport request expired.");
            notify(request.target(), "An incoming teleport request expired.");
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID quitter = event.getPlayer().getUniqueId();
        requests.removeFor(quitter).forEach(request -> {
            UUID other = request.sender().equals(quitter) ? request.target() : request.sender();
            notify(other, "Teleport request cancelled because " + event.getPlayer().getName() + " disconnected.");
        });
    }

    private void notify(UUID id, String message) {
        Player player = server.getPlayer(id);
        if (player != null) {
            tell(player, message);
        }
    }

    private static void tell(Player player, String message) {
        player.sendMessage("[tpcost] " + message);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("tpcost.use") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        if (command.getName().equals("tpcost") && !quotesEnabled.getAsBoolean()) return List.of();
        return switch (command.getName()) {
            case "tpa", "tpcost" -> server.getOnlinePlayers().stream()
                    .filter(other -> !other.getUniqueId().equals(player.getUniqueId()) && player.canSee(other))
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted().toList();
            case "tpaccept", "tpdeny" -> requests.incoming(player.getUniqueId()).stream()
                    .map(request -> server.getPlayer(request.sender()))
                    .filter(other -> other != null && player.canSee(other))
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted().toList();
            default -> List.of();
        };
    }
}
