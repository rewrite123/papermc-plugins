package io.github.ihormel.soulgem;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

final class GemItems {
    enum Kind {
        AMETHYST(Material.AMETHYST_SHARD, "Soul Gem"),
        ECHO(Material.ECHO_SHARD, "Echo Soul Gem");

        final Material material;
        final String title;

        Kind(Material material, String title) {
            this.material = material;
            this.title = title;
        }
    }

    private final NamespacedKey kindKey;
    private final NamespacedKey chargedKey;
    private final NamespacedKey progressKey;
    private final NamespacedKey requiredKey;

    GemItems(JavaPlugin plugin) {
        kindKey = new NamespacedKey(plugin, "kind");
        chargedKey = new NamespacedKey(plugin, "charged");
        progressKey = new NamespacedKey(plugin, "progress");
        requiredKey = new NamespacedKey(plugin, "required");
    }

    ItemStack create(Kind kind, boolean charged) {
        ItemStack item = new ItemStack(kind.material);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind.name());
        meta.getPersistentDataContainer().set(chargedKey, PersistentDataType.BYTE, (byte) (charged ? 1 : 0));
        meta.setMaxStackSize(charged ? 64 : 1);
        item.setItemMeta(meta);
        describe(item, kind, charged, 0, 0);
        return item;
    }

    boolean marked(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(kindKey);
    }

    Kind kind(ItemStack item) {
        if (!marked(item)) return null;
        var data = item.getItemMeta().getPersistentDataContainer();
        String value = data.get(kindKey, PersistentDataType.STRING);
        for (Kind kind : Kind.values()) {
            if (kind.name().equals(value) && item.getType() == kind.material) return kind;
        }
        return null;
    }

    boolean charged(ItemStack item) {
        return kind(item) != null && Byte.valueOf((byte) 1).equals(
                item.getItemMeta().getPersistentDataContainer().get(chargedKey, PersistentDataType.BYTE));
    }

    int progress(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(progressKey, PersistentDataType.INTEGER, 0);
    }

    int required(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(requiredKey, PersistentDataType.INTEGER, 0);
    }

    ItemStack withProgress(ItemStack item, int progress, int required) {
        Kind kind = kind(item);
        if (kind == null || charged(item) || required <= 0 || progress < 0 || progress >= required) {
            throw new IllegalArgumentException("Invalid soul gem charging state");
        }
        ItemStack result = item.clone();
        var meta = result.getItemMeta();
        meta.getPersistentDataContainer().set(progressKey, PersistentDataType.INTEGER, progress);
        meta.getPersistentDataContainer().set(requiredKey, PersistentDataType.INTEGER, required);
        result.setItemMeta(meta);
        describe(result, kind, false, progress, required);
        return result;
    }

    private void describe(ItemStack item, Kind kind, boolean charged, int progress, int required) {
        var meta = item.getItemMeta();
        meta.displayName(Component.text((charged ? "Charged " : "") + kind.title,
                kind == Kind.ECHO ? NamedTextColor.AQUA : NamedTextColor.LIGHT_PURPLE));
        List<Component> lore = new ArrayList<>();
        if (charged) {
            lore.add(Component.text("Off-hand: enchanting-table fuel.", NamedTextColor.GRAY));
            lore.add(Component.text(kind == Kind.ECHO
                    ? "Rolled enchantments gain +1 level." : "Normal enchanting-table power.", NamedTextColor.GRAY));
        } else {
            lore.add(Component.text("Hold in your off-hand to absorb earned XP.", NamedTextColor.GRAY));
            lore.add(Component.text(required == 0 ? "Charge cost: one full level of new XP."
                    : "Charge: " + progress + " / " + required + " XP", NamedTextColor.GRAY));
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(charged);
        item.setItemMeta(meta);
    }
}
