package io.github.ihormel.mobbanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.help.HelpTopic;
import org.bukkit.plugin.Plugin;

/**
 * In-game guide shown by {@code /help <plugin>}, loaded from {@code help.txt} in the plugin jar.
 *
 * <p>Lines between {@code [admin:<permission>]} and {@code [/admin]} are only shown to senders with that permission.
 * Registering in onEnable replaces Paper's default per-plugin command index, so help.txt lists commands itself.
 */
@SuppressWarnings("deprecation") // HelpTopic is a legacy-string API.
final class HelpGuide extends HelpTopic {
    static final String REPOSITORY = "https://github.com/rewrite123/papermc-plugins";
    private static final Pattern ADMIN_BLOCK = Pattern.compile("(?s)\\[admin:([^\\]]+)]\\R?(.*?)\\[/admin]\\R?");

    private final Plugin plugin;
    private final String guideUrl;
    private final String text;

    private HelpGuide(Plugin plugin, String folder, String text) {
        this.plugin = plugin;
        this.guideUrl = REPOSITORY + "/tree/main/" + folder;
        this.text = text;
        this.name = plugin.getName();
        this.shortText = Objects.requireNonNullElse(plugin.getPluginMeta().getDescription(), "");
        this.fullText = text;
    }

    static HelpGuide register(Plugin plugin, String folder) {
        var guide = new HelpGuide(plugin, folder, load(plugin));
        plugin.getServer().getHelpMap().addTopic(guide);
        return guide;
    }

    private static String load(Plugin plugin) {
        try (InputStream stream = plugin.getResource("help.txt")) {
            if (stream == null) {
                throw new IllegalStateException("Missing help.txt in plugin jar");
            }
            var raw = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
            return ChatColor.translateAlternateColorCodes('&', raw);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read help.txt", exception);
        }
    }

    String guideUrl() {
        return guideUrl;
    }

    @Override
    public boolean canSee(CommandSender sender) {
        return amendedPermission == null || sender.hasPermission(amendedPermission);
    }

    @Override
    public String getFullText(CommandSender sender) {
        var visible = textFor(sender);
        if (sender instanceof Player player) {
            if (plugin.isEnabled()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(links());
                    }
                });
            }
            return visible;
        }
        return visible + "\n" + ChatColor.GOLD + "Full guide: " + ChatColor.WHITE + guideUrl
                + "\n" + ChatColor.GOLD + "Report an issue: " + ChatColor.WHITE + REPOSITORY + "/issues";
    }

    String textFor(CommandSender sender) {
        Matcher matcher = ADMIN_BLOCK.matcher(text);
        var result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, sender.hasPermission(matcher.group(1).strip())
                    ? Matcher.quoteReplacement(matcher.group(2)) : "");
        }
        matcher.appendTail(result);
        return result.toString().strip();
    }

    Component links() {
        return Component.text(plugin.getName() + ": ", NamedTextColor.GOLD)
                .append(link("[Full guide on GitHub]", guideUrl, NamedTextColor.AQUA))
                .append(Component.text(" "))
                .append(link("[Report an issue]", REPOSITORY + "/issues", NamedTextColor.YELLOW));
    }

    private static Component link(String label, String url, NamedTextColor color) {
        return Component.text(label, color)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(Component.text(url));
    }
}
