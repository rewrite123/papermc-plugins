package io.github.ihormel.inventorysorter;

import java.util.Set;
import org.bukkit.Material;

public final class ItemGroups {
    private static final Set<String> MOB_DROPS = Set.of(
            "ARMADILLO_SCUTE", "BLAZE_POWDER", "BLAZE_ROD", "BONE", "BONE_MEAL",
            "BREEZE_ROD", "ENDER_PEARL", "FEATHER", "FERMENTED_SPIDER_EYE", "GHAST_TEAR",
            "GUNPOWDER", "INK_SAC", "GLOW_INK_SAC", "LEATHER", "MAGMA_CREAM",
            "NAUTILUS_SHELL", "PHANTOM_MEMBRANE", "RABBIT_FOOT", "RABBIT_HIDE",
            "ROTTEN_FLESH", "SHULKER_SHELL", "SLIME_BALL", "SPIDER_EYE", "STRING",
            "TURTLE_SCUTE", "WITHER_SKELETON_SKULL");
    private static final Set<String> MINERALS = Set.of(
            "AMETHYST_SHARD", "COAL", "CHARCOAL", "DIAMOND", "EMERALD", "FLINT",
            "LAPIS_LAZULI", "NETHERITE_SCRAP", "QUARTZ", "RESIN_CLUMP");
    private static final Set<String> TOOLS = Set.of(
            "BRUSH", "CLOCK", "COMPASS", "RECOVERY_COMPASS", "FISHING_ROD",
            "FLINT_AND_STEEL", "SHEARS", "SPYGLASS", "CARROT_ON_A_STICK",
            "WARPED_FUNGUS_ON_A_STICK", "LEAD", "NAME_TAG");
    private static final Set<String> REDSTONE = Set.of(
            "REDSTONE", "REDSTONE_BLOCK", "REDSTONE_TORCH", "REPEATER", "COMPARATOR",
            "PISTON", "STICKY_PISTON", "OBSERVER", "DISPENSER", "DROPPER", "HOPPER",
            "TARGET", "LEVER", "DAYLIGHT_DETECTOR", "TRIPWIRE_HOOK", "TNT",
            "SCULK_SENSOR", "CALIBRATED_SCULK_SENSOR", "CRAFTER");
    private static final Set<String> PLANTS = Set.of(
            "WHEAT", "SUGAR_CANE", "BAMBOO", "CACTUS", "KELP", "SEAGRASS",
            "LILY_PAD", "VINE", "GLOW_LICHEN", "MOSS_CARPET", "PALE_MOSS_CARPET",
            "DANDELION", "POPPY", "BLUE_ORCHID", "ALLIUM", "AZURE_BLUET",
            "OXEYE_DAISY", "CORNFLOWER", "LILY_OF_THE_VALLEY", "WITHER_ROSE",
            "TORCHFLOWER", "PITCHER_PLANT", "SUNFLOWER", "LILAC", "ROSE_BUSH",
            "PEONY", "PINK_PETALS", "WILDFLOWERS", "LEAF_LITTER", "DEAD_BUSH",
            "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN", "HANGING_ROOTS",
            "BROWN_MUSHROOM", "RED_MUSHROOM", "CRIMSON_FUNGUS", "WARPED_FUNGUS",
            "NETHER_WART", "CHORUS_FLOWER", "SPORE_BLOSSOM");

    private ItemGroups() {}

    public static String group(Material material) {
        String name = material.name();
        if (name.endsWith("_SPAWN_EGG")) return "Spawn Eggs";
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.equals("ELYTRA") || name.equals("WOLF_ARMOR")) return "Armor";
        if (name.endsWith("_SWORD") || name.endsWith("_SPEAR") || Set.of("BOW", "CROSSBOW", "TRIDENT", "MACE",
                "ARROW", "SPECTRAL_ARROW", "TIPPED_ARROW", "SHIELD").contains(name)) return "Weapons";
        if (TOOLS.contains(name) || name.endsWith("_PICKAXE") || name.endsWith("_AXE")
                || name.endsWith("_SHOVEL") || name.endsWith("_HOE")) return "Tools";
        if (name.contains("POTION") || Set.of("BREWING_STAND", "CAULDRON",
                "GLASS_BOTTLE", "DRAGON_BREATH", "GLISTERING_MELON_SLICE").contains(name)) return "Brewing";
        if (name.equals("ENCHANTED_BOOK") || name.equals("ENCHANTING_TABLE")
                || name.equals("EXPERIENCE_BOTTLE") || name.endsWith("ANVIL")) return "Enchanting";
        if (material.isEdible()) return "Food";
        if (MOB_DROPS.contains(name)) return "Mob Drops";
        if (MINERALS.contains(name) || name.endsWith("_ORE") || name.endsWith("_INGOT")
                || name.endsWith("_NUGGET") || name.startsWith("RAW_")) return "Minerals";
        if (REDSTONE.contains(name) || name.endsWith("_BUTTON")
                || name.endsWith("_PRESSURE_PLATE") || name.endsWith("RAIL")) return "Redstone";
        if (name.endsWith("_BOAT") || name.endsWith("_RAFT") || name.endsWith("MINECART")
                || name.equals("SADDLE") || name.endsWith("_HORSE_ARMOR")) return "Transport";
        if (name.endsWith("_BUCKET") || name.equals("BUCKET") || name.equals("BUNDLE")
                || name.endsWith("_BUNDLE") || name.endsWith("SHULKER_BOX")
                || Set.of("CHEST", "TRAPPED_CHEST", "ENDER_CHEST", "BARREL").contains(name)) return "Storage";
        if (PLANTS.contains(name) || name.endsWith("_SEEDS") || name.endsWith("_SAPLING")
                || name.endsWith("_LEAVES") || name.endsWith("_TULIP")
                || name.endsWith("_ROOTS") || name.endsWith("_VINES")) return "Plants";
        if (name.endsWith("_DYE") || name.endsWith("_BANNER") || name.endsWith("_BANNER_PATTERN")
                || name.startsWith("MUSIC_DISC_") || name.endsWith("_POTTERY_SHERD")
                || name.endsWith("_SMITHING_TEMPLATE") || name.equals("PAINTING")
                || name.equals("ITEM_FRAME") || name.equals("GLOW_ITEM_FRAME")) return "Decoration";
        if (material.isBlock()) return "Building Blocks";
        return "Miscellaneous";
    }
}
