package io.github.ihormel.enchantcap;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.java.JavaPlugin;

public class EnchantCapPlugin extends JavaPlugin {
    static final String SETTING = "max-additional-enchantment-levels";
    private int additionalLevels;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var configured = getConfig().get(SETTING);
        if (!(configured instanceof Integer additional) || additional < 0 || additional > 254) {
            throw new IllegalArgumentException("max-additional-enchantment-levels must be an integer from 0 to 254");
        }
        additionalLevels = additional;
        getServer().getPluginManager().registerEvents(new AnvilCostListener(() -> additionalLevels), this);
        Objects.requireNonNull(getCommand("enchantcap"), "Missing enchantcap command").setExecutor(this);
        HelpGuide.register(this, "enchantcap");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("enchantcap.admin")) {
            sender.sendMessage("You do not have permission to manage EnchantCap.");
            return true;
        }
        if (args.length < 1 || args.length > 2 || !args[0].equalsIgnoreCase(SETTING)) {
            sender.sendMessage("Usage: /enchantcap " + SETTING + " [0-254]");
            return true;
        }
        if (args.length == 1) {
            sender.sendMessage(SETTING + " = " + additionalLevels);
            return true;
        }
        int value;
        try {
            value = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            sender.sendMessage("The value must be a whole number from 0 to 254.");
            return true;
        }
        if (value < 0 || value > 254) {
            sender.sendMessage("The value must be a whole number from 0 to 254.");
            return true;
        }
        getConfig().set(SETTING, value);
        try {
            getConfig().save(getDataFolder().toPath().resolve("config.yml").toFile());
        } catch (IOException exception) {
            getConfig().set(SETTING, additionalLevels);
            getLogger().log(Level.SEVERE, "Could not save EnchantCap enchantment limit; change not applied.", exception);
            sender.sendMessage("Could not save the setting. Nothing changed; check the server log.");
            return true;
        }
        boolean changed = value != additionalLevels;
        additionalLevels = value;
        if (changed) {
            for (var player : getServer().getOnlinePlayers()) {
                if (player.getOpenInventory() instanceof AnvilView) {
                    player.closeInventory();
                    player.sendMessage("EnchantCap's enchantment limit changed. Reopen the anvil to continue.");
                }
            }
        }
        sender.sendMessage(SETTING + " set to " + value + " and saved. No restart required.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("enchantcap.admin")) return List.of();
        if (args.length == 1 && SETTING.startsWith(args[0].toLowerCase(Locale.ROOT))) return List.of(SETTING);
        if (args.length == 2 && args[0].equalsIgnoreCase(SETTING)) {
            return List.of("0", "1", "2").stream().filter(value -> value.startsWith(args[1])).toList();
        }
        return List.of();
    }
}
