package io.github.ihormel.mobbanner;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Banner;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

final class BannerItems {
    private final NamespacedKey mobKey;

    BannerItems(NamespacedKey mobKey) {
        this.mobKey = mobKey;
    }

    void give(Player player, EntityType type) {
        player.getInventory().addItem(create(type)).values().forEach(
                leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    ItemStack create(EntityType type) {
        if (!MobTypes.SUPPORTED.contains(type)) throw new IllegalArgumentException("Unsupported mob: " + type);
        DyeColor base = color(type);
        ItemStack item = new ItemStack(Material.valueOf(base.name() + "_BANNER"));
        BannerMeta meta = (BannerMeta) item.getItemMeta();
        DyeColor face = base == DyeColor.BLACK || base == DyeColor.BLUE
                || base == DyeColor.BROWN || base == DyeColor.PURPLE ? DyeColor.WHITE : DyeColor.BLACK;
        PatternType shape = switch (type) {
            case CREEPER, ZOMBIE, ZOMBIE_VILLAGER, HUSK, DROWNED -> PatternType.CREEPER;
            default -> PatternType.SKULL;
        };
        meta.setPatterns(List.of(new Pattern(face, shape), new Pattern(face, PatternType.BORDER)));
        meta.displayName(Component.text(MobTypes.name(type) + " Ward Banner"));
        meta.lore(List.of(Component.text("Suppresses natural " + MobTypes.name(type) + " spawns"),
                Component.text("in the chunk where this banner is placed."),
                Component.text("On a 5/3/1 valuable-block pyramid: attracts instead.")));
        meta.setEnchantmentGlintOverride(true);
        write(meta.getPersistentDataContainer(), type);
        item.setItemMeta(meta);
        return item;
    }

    EntityType read(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof BannerMeta meta)) return null;
        return read(meta.getPersistentDataContainer());
    }

    EntityType read(Banner banner) {
        return read(banner.getPersistentDataContainer());
    }

    private EntityType read(PersistentDataContainer data) {
        String value = data.get(mobKey, PersistentDataType.STRING);
        if (value == null) return null;
        return MobTypes.SUPPORTED.stream()
                .filter(type -> type.name().equals(value)).findFirst().orElse(null);
    }

    void write(PersistentDataContainer data, EntityType type) {
        data.set(mobKey, PersistentDataType.STRING, type.name());
    }

    private DyeColor color(EntityType type) {
        return switch (type) {
            case BAT -> DyeColor.BROWN;
            case BLAZE, MAGMA_CUBE -> DyeColor.ORANGE;
            case BOGGED, CREEPER, SLIME, ZOMBIE, ZOMBIE_VILLAGER -> DyeColor.LIME;
            case BREEZE, PHANTOM, STRAY -> DyeColor.LIGHT_BLUE;
            case CAVE_SPIDER, DROWNED, WARDEN -> DyeColor.CYAN;
            case CREAKING, HOGLIN, HUSK -> DyeColor.BROWN;
            case ENDER_DRAGON, ENDERMAN, WITCH -> DyeColor.PURPLE;
            case GUARDIAN, ELDER_GUARDIAN -> DyeColor.CYAN;
            case PIGLIN_BRUTE, ZOGLIN -> DyeColor.PINK;
            case SPIDER, WITHER, WITHER_SKELETON -> DyeColor.BLACK;
            case EVOKER, PILLAGER, RAVAGER, VINDICATOR -> DyeColor.GRAY;
            case SHULKER, ENDERMITE -> DyeColor.MAGENTA;
            case PARCHED -> DyeColor.YELLOW;
            default -> DyeColor.WHITE;
        };
    }
}
