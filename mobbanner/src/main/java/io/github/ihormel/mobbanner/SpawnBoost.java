package io.github.ihormel.mobbanner;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.util.BoundingBox;

class SpawnBoost {
    private boolean spawning;
    private final IntSupplier offset;

    SpawnBoost() {
        this(() -> ThreadLocalRandom.current().nextInt(-3, 4));
    }

    SpawnBoost(IntSupplier offset) {
        this.offset = offset;
    }

    boolean isSpawning() {
        return spawning;
    }

    void attempt(LivingEntity source, Location origin) {
        int dx = offset.getAsInt();
        int dz = offset.getAsInt();
        if (dx == 0 && dz == 0) return;
        Location candidate = origin.clone().add(dx, 0, dz);
        if (!safe(source, origin, candidate)) return;
        spawning = true;
        try {
            // CUSTOM avoids recursive boosting and lets protection plugins veto the extra spawn.
            origin.getWorld().spawnEntity(candidate, source.getType(), SpawnReason.CUSTOM);
        } finally {
            spawning = false;
        }
    }

    boolean safe(LivingEntity source, Location origin, Location candidate) {
        var world = origin.getWorld();
        if (!world.equals(candidate.getWorld()) || !world.getWorldBorder().isInside(candidate)
                || origin.getBlockX() >> 4 != candidate.getBlockX() >> 4
                || origin.getBlockZ() >> 4 != candidate.getBlockZ() >> 4) return false;
        var shift = candidate.toVector().subtract(source.getLocation().toVector());
        BoundingBox box = source.getBoundingBox().clone().shift(shift);
        if (box.getMinY() < world.getMinHeight() || box.getMaxY() >= world.getMaxHeight()) return false;
        boolean aquatic = source.getType() == EntityType.GUARDIAN
                || source.getType() == EntityType.ELDER_GUARDIAN || source.getType() == EntityType.DROWNED
                && origin.getBlock().getType() == Material.WATER;
        for (int x = (int) Math.floor(box.getMinX()); x < Math.ceil(box.getMaxX()); x++) {
            for (int z = (int) Math.floor(box.getMinZ()); z < Math.ceil(box.getMaxZ()); z++) {
                if (x >> 4 != origin.getBlockX() >> 4 || z >> 4 != origin.getBlockZ() >> 4) return false;
                for (int y = (int) Math.floor(box.getMinY()); y < Math.ceil(box.getMaxY()); y++) {
                    var block = world.getBlockAt(x, y, z);
                    if (aquatic ? block.getType() != Material.WATER : !block.getType().isAir()) return false;
                }
            }
        }
        boolean flying = switch (source.getType()) {
            case BAT, BLAZE, BREEZE, GHAST, PHANTOM, VEX, WITHER, ENDER_DRAGON -> true;
            default -> false;
        };
        if (!aquatic && !flying && !candidate.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) return false;
        if (candidate.getBlock().getLightLevel() > origin.getBlock().getLightLevel()) return false;
        if (world.getPlayers().stream().anyMatch(player -> player.getGameMode() != GameMode.SPECTATOR
                && player.getLocation().distanceSquared(candidate) < 24 * 24)) return false;
        return world.getNearbyEntities(box, entity -> entity instanceof LivingEntity).isEmpty();
    }
}
