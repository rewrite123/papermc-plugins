package io.github.ihormel.flipdisc;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Material;

final class DiscPairs {
    static final Map<Material, Material> OPPOSITES;

    static {
        EnumMap<Material, Material> pairs = new EnumMap<>(Material.class);
        pair(pairs, Material.MUSIC_DISC_CAT, Material.MUSIC_DISC_TEARS);
        pair(pairs, Material.MUSIC_DISC_BLOCKS, Material.MUSIC_DISC_LAVA_CHICKEN);
        pair(pairs, Material.MUSIC_DISC_CHIRP, Material.MUSIC_DISC_BOUNCE);
        pair(pairs, Material.MUSIC_DISC_13, Material.MUSIC_DISC_MELLOHI);
        pair(pairs, Material.MUSIC_DISC_FAR, Material.MUSIC_DISC_STRAD);
        pair(pairs, Material.MUSIC_DISC_MALL, Material.MUSIC_DISC_STAL);
        pair(pairs, Material.MUSIC_DISC_WAIT, Material.MUSIC_DISC_WARD);
        OPPOSITES = Collections.unmodifiableMap(pairs);
    }

    private DiscPairs() {}

    private static void pair(Map<Material, Material> pairs, Material first, Material second) {
        if (first == second || pairs.containsKey(first) || pairs.containsKey(second)) {
            throw new IllegalArgumentException("Music disc must belong to exactly one two-song pair");
        }
        pairs.put(first, second);
        pairs.put(second, first);
    }
}
