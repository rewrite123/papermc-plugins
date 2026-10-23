package io.github.ihormel.votersrights;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class AdminCommands implements TabExecutor {
    private static final List<String> OPERATIONS = List.of("show", "set", "allow", "difficulties",
            "whitelist", "blacklist", "action", "reload");
    private final VotersRightsPlugin plugin;

    AdminCommands(VotersRightsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("votersrights.admin")) {
            VotersRightsPlugin.tell(sender, "You do not have permission to manage votes.");
            return true;
        }
        try {
            if (args.length == 0 || args[0].equalsIgnoreCase("show")) {
                var config = plugin.configurationCopy();
                VotersRightsPlugin.tell(sender, "Voting settings:\n" + config.saveToString());
                return true;
            }
            if (args[0].equalsIgnoreCase("reload")) {
                if (args.length != 1) throw new IllegalArgumentException("Use /votersrights reload.");
                plugin.reloadVotingConfiguration();
                VotersRightsPlugin.tell(sender, "Configuration reloaded. Any active vote was cancelled.");
                return true;
            }
            var candidate = plugin.configurationCopy();
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "set" -> {
                    if (args.length != 3) throw new IllegalArgumentException("Use /votersrights set <setting> <value>.");
                    String key = args[1].toLowerCase(Locale.ROOT);
                    if (VotingSettings.NUMERIC.contains(key)) {
                        try {
                            candidate.set(key, Integer.parseInt(args[2]));
                        } catch (NumberFormatException exception) {
                            throw new IllegalArgumentException("The value must be a whole number.");
                        }
                    } else if (key.equals("whitelist-enabled")) {
                        candidate.set(key, booleanValue(args[2]));
                    } else {
                        throw new IllegalArgumentException("Unknown setting. Use /votersrights show or tab completion.");
                    }
                }
                case "allow" -> {
                    if (args.length != 3) throw new IllegalArgumentException("Use /votersrights allow <action> <true|false>.");
                    String action = plugin.settings().actionName(args[1]);
                    String key = VotingSettings.ACTIONS.contains(action) ? "actions." + action
                            : "custom-actions." + action + ".enabled";
                    candidate.set(key, booleanValue(args[2]));
                }
                case "difficulties" -> {
                    if (args.length < 2) throw new IllegalArgumentException("Use /votersrights difficulties easy normal hard.");
                    candidate.set("allowed-difficulties", Arrays.stream(args).skip(1)
                            .map(value -> value.toUpperCase(Locale.ROOT)).toList());
                }
                case "whitelist", "blacklist" -> {
                    String key = args[0].toLowerCase(Locale.ROOT);
                    if (args.length == 1) {
                        VotersRightsPlugin.tell(sender, key + ": " + candidate.getStringList(key));
                        return true;
                    }
                    if (args.length != 3 || !(args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                        throw new IllegalArgumentException("Use /votersrights " + key + " <add|remove> <player|UUID>.");
                    }
                    UUID id = playerId(args[2]);
                    var entries = new ArrayList<>(candidate.getStringList(key));
                    entries.remove(id.toString());
                    if (args[1].equalsIgnoreCase("add")) entries.add(id.toString());
                    candidate.set(key, entries);
                }
                case "action" -> editAction(candidate, args);
                default -> throw new IllegalArgumentException("Use /votersrights " + String.join("|", OPERATIONS) + ".");
            }
            plugin.applyConfiguration(candidate, true);
            VotersRightsPlugin.tell(sender, "Saved and applied. Any active vote was cancelled; passed actions keep their warnings.");
        } catch (IllegalArgumentException exception) {
            VotersRightsPlugin.tell(sender, exception.getMessage());
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load or save voting settings; active settings unchanged.", exception);
            VotersRightsPlugin.tell(sender, "Could not load/save settings. Active settings are unchanged; check the server log.");
        }
        return true;
    }

    private void editAction(YamlConfiguration candidate, String[] args) {
        if (args.length < 3) throw new IllegalArgumentException(
                "Use /votersrights action add <name> <fixed console command...> or action remove <name>.");
        String name = args[2].toLowerCase(Locale.ROOT);
        VotingSettings.validateCustomName(name);
        String path = "custom-actions." + name;
        if (args[1].equalsIgnoreCase("remove") && args.length == 3) {
            if (!candidate.contains(path)) throw new IllegalArgumentException("No custom action named " + name + ".");
            candidate.set(path, null);
        } else if (args[1].equalsIgnoreCase("add") && args.length >= 4) {
            String consoleCommand = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            VotingSettings.validateCommand(consoleCommand);
            var commands = new ArrayList<>(candidate.getStringList(path + ".commands"));
            commands.add(consoleCommand);
            candidate.set(path + ".commands", commands);
            if (!candidate.contains(path + ".enabled")) candidate.set(path + ".enabled", true);
        } else {
            throw new IllegalArgumentException("Use action add <name> <command...> or action remove <name>.");
        }
    }

    private UUID playerId(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException exception) {
            var online = plugin.getServer().getPlayerExact(input);
            if (online != null) return online.getUniqueId();
            var cached = plugin.getServer().getOfflinePlayerIfCached(input);
            if (cached != null) return cached.getUniqueId();
            throw new IllegalArgumentException("Player not known locally. Use an online/cached name or their UUID.");
        }
    }

    private static boolean booleanValue(String value) {
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("The value must be true or false.");
        }
        return Boolean.parseBoolean(value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("votersrights.admin") || args.length == 0) return List.of();
        List<String> options = List.of();
        if (args.length == 1) options = OPERATIONS;
        else if (args.length == 2) {
            options = switch (args[0].toLowerCase(Locale.ROOT)) {
                case "set" -> {
                    var keys = new ArrayList<>(VotingSettings.NUMERIC);
                    keys.add("whitelist-enabled");
                    yield keys;
                }
                case "allow" -> plugin.settings().names();
                case "action", "whitelist", "blacklist" -> List.of("add", "remove");
                case "difficulties" -> List.of("peaceful", "easy", "normal", "hard");
                default -> List.of();
            };
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("allow")
                    || (args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase("whitelist-enabled"))) {
                options = List.of("true", "false");
            } else if (args[0].equalsIgnoreCase("action") && args[1].equalsIgnoreCase("remove")) {
                options = plugin.settings().customActions().keySet().stream().sorted().toList();
            } else if (args[0].equalsIgnoreCase("whitelist") || args[0].equalsIgnoreCase("blacklist")) {
                options = plugin.getServer().getOnlinePlayers().stream()
                        .filter(other -> !(sender instanceof org.bukkit.entity.Player player) || player.canSee(other))
                        .map(org.bukkit.entity.Player::getName).sorted().toList();
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
