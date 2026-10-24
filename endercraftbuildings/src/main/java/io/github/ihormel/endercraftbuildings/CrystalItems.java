package io.github.ihormel.endercraftbuildings;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

final class CrystalItems {
    static final String MEMORY = "memory_crystal";
    static final String EMPTY = "empty_storage_crystal";
    static final String STORAGE = "storage_crystal";
    static final String TAPER = "ender_biconical_taper";

    private final NamespacedKey kind;
    private final NamespacedKey blueprint;
    private final NamespacedKey name;
    private final NamespacedKey blocks;
    private final NamespacedKey token;

    CrystalItems(Plugin plugin) {
        kind = new NamespacedKey(plugin, "item");
        blueprint = new NamespacedKey(plugin, "blueprint");
        name = new NamespacedKey(plugin, "building_name");
        blocks = new NamespacedKey(plugin, "block_count");
        token = new NamespacedKey(plugin, "scan_token");
    }

    static double holdSeconds(int blockCount) { return Math.min(30.0, 2.0 + 0.05 * blockCount); }

    ItemStack memoryCrystal() {
        var item = base(Material.AMETHYST_SHARD, MEMORY, "Memory Storage Crystal", List.of(
                "Right-click two corners to scan a building",
                "(up to 32 x 32 x 32). Sneak + right-click cancels.",
                "Use on an Ender Biconical Taper to imprint",
                "storage crystals. Never consumed."));
        item.editMeta(meta -> meta.setItemModel(Material.SOUL_LANTERN.getKey()));
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        return item;
    }

    ItemStack emptyStorageCrystal(int amount) {
        var item = base(Material.AMETHYST_SHARD, EMPTY, "Empty Storage Crystal", List.of(
                "Carry this to an Ender Biconical Taper with",
                "a memory or storage crystal and the materials."));
        item.setAmount(amount);
        return item;
    }

    ItemStack taper() {
        return base(Material.VAULT, TAPER, "Ender Biconical Taper", List.of(
                "Right-click with a memory or storage crystal",
                "to imprint an empty storage crystal."));
    }

    ItemStack storageCrystal(byte[] data, String building, int count) {
        var item = base(Material.AMETHYST_SHARD, STORAGE, "Storage Crystal: " + building, List.of(
                count + " blocks waiting in the End",
                "Hold right-click on a block to recall it.",
                "The building's front-left corner goes there",
                "and it faces the way you look. Breaks on use."));
        item.editMeta(meta -> {
            var pdc = meta.getPersistentDataContainer();
            pdc.set(blueprint, PersistentDataType.BYTE_ARRAY, data);
            pdc.set(name, PersistentDataType.STRING, building);
            pdc.set(blocks, PersistentDataType.INTEGER, count);
            meta.setEnchantmentGlintOverride(true);
        });
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .consumeSeconds((float) holdSeconds(count))
                .animation(ItemUseAnimation.BOW)
                .sound(Key.key("minecraft:block.amethyst_block.chime"))
                .hasConsumeParticles(false)
                .effects(List.of())
                .build());
        return item;
    }

    void imprintMemory(ItemStack crystal, Blueprint building, String buildingName) {
        int count = building.blockCount();
        crystal.editMeta(meta -> {
            var pdc = meta.getPersistentDataContainer();
            pdc.set(blueprint, PersistentDataType.BYTE_ARRAY, building.encode());
            pdc.set(name, PersistentDataType.STRING, buildingName);
            pdc.set(blocks, PersistentDataType.INTEGER, count);
            pdc.remove(token);
            meta.setEnchantmentGlintOverride(true);
            var lore = new ArrayList<Component>();
            lore.add(line("Holds: " + buildingName + " (" + count + " blocks)", NamedTextColor.AQUA));
            for (String text : List.of("Use on an Ender Biconical Taper to imprint",
                    "storage crystals. Scanning again replaces it.")) lore.add(line(text, NamedTextColor.GRAY));
            meta.lore(lore);
        });
    }

    ItemStack copyFrom(ItemStack source) {
        return storageCrystal(rawBlueprint(source), buildingName(source), blockCount(source));
    }

    String kind(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(kind, PersistentDataType.STRING);
    }

    boolean custom(ItemStack item) { return kind(item) != null; }

    byte[] rawBlueprint(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(blueprint, PersistentDataType.BYTE_ARRAY);
    }

    Blueprint blueprint(ItemStack item) {
        byte[] data = rawBlueprint(item);
        if (data == null) return null;
        try {
            return Blueprint.decode(data);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    String buildingName(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(name, PersistentDataType.STRING, "Building");
    }

    int blockCount(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(blocks, PersistentDataType.INTEGER, 0);
    }

    void setToken(ItemStack item, String value) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(token, PersistentDataType.STRING, value));
    }

    boolean hasToken(ItemStack item, String value) {
        return item != null && item.hasItemMeta()
                && value.equals(item.getItemMeta().getPersistentDataContainer().get(token, PersistentDataType.STRING));
    }

    private ItemStack base(Material material, String id, String title, List<String> lore) {
        var item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.itemName(Component.text(title, NamedTextColor.LIGHT_PURPLE));
            meta.lore(lore.stream().map(text -> line(text, NamedTextColor.GRAY)).toList());
            meta.getPersistentDataContainer().set(kind, PersistentDataType.STRING, id);
        });
        return item;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
