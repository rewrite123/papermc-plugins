package io.github.ihormel.reduceendermanworlddmg;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Enderman;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

final class BlockReturns {
    static final NamespacedKey ORIGIN = new NamespacedKey("reduceendermanworlddmg", "origin");
    /** Key written by 0.1.x, when the plugin was named reduceendermanblockdmg. */
    static final NamespacedKey LEGACY_ORIGIN = new NamespacedKey("reduceendermanblockdmg", "origin");
    /** How long an Enderman may try to put the block back before it may drop it nearby instead. */
    static final long RETURN_WINDOW_MS = 10_000;
    /** Maximum distance from the Enderman's eyes to the center of the block it places. */
    static final double REACH = 3.5;
    private static final double[][] SIGHT_POINTS = {
            {0.5, 0.5, 0.5}, {0.5, 0.9, 0.5}, {0.5, 0.1, 0.5},
            {0.1, 0.5, 0.5}, {0.9, 0.5, 0.5}, {0.5, 0.5, 0.1}, {0.5, 0.5, 0.9}};

    private static final class Pending {
        final Enderman entity;
        final ReturnOrigin origin;
        final BlockData data;
        long windowEnds;

        Pending(Enderman entity, ReturnOrigin origin, BlockData data) {
            this.entity = entity;
            this.origin = origin;
            this.data = data;
        }
    }

    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, EntityChangeBlockEvent> placements = new HashMap<>();
    private final LongSupplier clock;
    private final LongSupplier delay;
    private final Consumer<EntityChangeBlockEvent> events;
    private final Function<UUID, World> worlds;
    private final Function<String, BlockData> blockData;
    private final Consumer<String> warning;
    private final Predicate<World> griefing;

    BlockReturns(LongSupplier clock, LongSupplier delay, Consumer<EntityChangeBlockEvent> events,
            Function<UUID, World> worlds, Function<String, BlockData> blockData, Consumer<String> warning,
            Predicate<World> griefing) {
        this.clock = clock;
        this.delay = delay;
        this.events = events;
        this.worlds = worlds;
        this.blockData = blockData;
        this.warning = warning;
        this.griefing = griefing;
    }

    void pickedUp(Enderman entity, Block block, BlockData original) {
        ReturnOrigin origin = new ReturnOrigin(block.getWorld().getUID(), block.getX(), block.getY(),
                block.getZ(), original.getAsString(), clock.getAsLong() + delay.getAsLong());
        entity.getPersistentDataContainer().set(ORIGIN, PersistentDataType.STRING, origin.encode());
        pending.put(entity.getUniqueId(), new Pending(entity, origin, original.clone()));
    }

    boolean tracked(Enderman entity) {
        var container = entity.getPersistentDataContainer();
        return container.has(ORIGIN, PersistentDataType.STRING)
                || container.has(LEGACY_ORIGIN, PersistentDataType.STRING);
    }

    boolean ownPlacement(EntityChangeBlockEvent event) {
        return placements.get(event.getEntity().getUniqueId()) == event;
    }

    void load(Enderman entity) {
        var container = entity.getPersistentDataContainer();
        String value = container.get(ORIGIN, PersistentDataType.STRING);
        if (value == null) {
            value = container.get(LEGACY_ORIGIN, PersistentDataType.STRING);
            if (value == null) return;
            container.set(ORIGIN, PersistentDataType.STRING, value);
            container.remove(LEGACY_ORIGIN);
        }
        try {
            ReturnOrigin origin = ReturnOrigin.decode(value);
            pending.put(entity.getUniqueId(), new Pending(entity, origin, blockData.apply(origin.blockData())));
        } catch (IllegalArgumentException exception) {
            warning.accept("Invalid return record for Enderman " + entity.getUniqueId()
                    + ": " + exception.getMessage() + ". Removing record; carried block is unchanged.");
            forget(entity);
        }
    }

    void unload(Enderman entity) {
        pending.remove(entity.getUniqueId());
    }

    void forget(Enderman entity) {
        pending.remove(entity.getUniqueId());
        entity.getPersistentDataContainer().remove(ORIGIN);
        entity.getPersistentDataContainer().remove(LEGACY_ORIGIN);
    }

    void tick() {
        for (Pending entry : new ArrayList<>(pending.values())) {
            Enderman entity = entry.entity;
            if (entity.isDead()) {
                forget(entity);
                continue;
            }
            if (!entity.isValid()) {
                unload(entity);
                continue;
            }
            if (!carrying(entry)) {
                forget(entity);
                continue;
            }
            long now = clock.getAsLong();
            if (now < entry.origin.due() || entity.getTarget() != null || !entity.hasAI()) continue;
            World world = worlds.apply(entry.origin.world());
            if (world == null || world != entity.getWorld() || !griefing.test(world)) continue;
            boolean returning = world.isChunkLoaded(entry.origin.x() >> 4, entry.origin.z() >> 4);
            if (returning && (entry.origin.y() < world.getMinHeight() || entry.origin.y() >= world.getMaxHeight())) {
                warning.accept("Enderman return location is outside world height: " + entity.getUniqueId());
                forget(entity);
                continue;
            }
            if (entry.windowEnds == 0) entry.windowEnds = now + RETURN_WINDOW_MS;
            boolean expired = now >= entry.windowEnds;
            if (returning) {
                Block original = world.getBlockAt(entry.origin.x(), entry.origin.y(), entry.origin.z());
                if (returnTo(entry, world, original, !expired)) continue;
            }
            if (expired) placeNearby(entry, world);
        }
    }

    /** Walks toward and places the block at its origin; returns true once it is placed. */
    private boolean returnTo(Pending entry, World world, Block original, boolean walk) {
        Enderman entity = entry.entity;
        if (!original.isEmpty()) return false;
        Location center = original.getLocation().add(0.5, 0.5, 0.5);
        if (!inReach(entity, center)) {
            if (walk) entity.getPathfinder().moveTo(center.clone().subtract(0, 0.5, 0), 1.0);
            return false;
        }
        if (overlaps(entity, original)) {
            if (walk) stepAway(entity, center);
            return false;
        }
        if (!visible(entity, original)) {
            if (walk) entity.getPathfinder().moveTo(center.clone().subtract(0, 0.5, 0), 1.0);
            return false;
        }
        entity.getPathfinder().stopPathfinding();
        return place(entry, world, original);
    }

    /** Fallback after the return window: place the block on the nearby spot closest to its origin. */
    private void placeNearby(Pending entry, World world) {
        Enderman entity = entry.entity;
        Location feet = entity.getLocation();
        Vector origin = new Vector(entry.origin.x() + 0.5, entry.origin.y() + 0.5, entry.origin.z() + 0.5);
        List<Block> candidates = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int y = feet.getBlockY() + dy;
                    if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
                    Block block = world.getBlockAt(feet.getBlockX() + dx, y, feet.getBlockZ() + dz);
                    if (block != null) candidates.add(block);
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(
                block -> block.getLocation().toVector().add(new Vector(0.5, 0.5, 0.5)).distanceSquared(origin)));
        for (Block block : candidates) {
            if (!placeable(entry, block)) continue;
            entity.getPathfinder().stopPathfinding();
            if (place(entry, world, block)) return;
        }
    }

    private boolean placeable(Pending entry, Block block) {
        Enderman entity = entry.entity;
        if (!block.isEmpty() || !block.getRelative(BlockFace.DOWN).isSolid()) return false;
        if (!block.canPlace(entry.data)) return false;
        if (!inReach(entity, block.getLocation().add(0.5, 0.5, 0.5)) || overlaps(entity, block)) return false;
        if (!block.getWorld().getNearbyEntities(BoundingBox.of(block)).isEmpty()) return false;
        return visible(entity, block);
    }

    private boolean place(Pending entry, World world, Block target) {
        Enderman entity = entry.entity;
        EntityChangeBlockEvent placement = new EntityChangeBlockEvent(entity, target, entry.data);
        placements.put(entity.getUniqueId(), placement);
        try {
            events.accept(placement);
        } finally {
            placements.remove(entity.getUniqueId());
        }
        // Protection handlers may change the world or entity while handling the event.
        if (placement.isCancelled() || !target.isEmpty() || entity.isDead() || !entity.isValid()
                || entity.getWorld() != world || !inReach(entity, target.getLocation().add(0.5, 0.5, 0.5))
                || !carrying(entry) || !griefing.test(world)) return false;
        target.setBlockData(entry.data, true);
        entity.setCarriedBlock(null);
        forget(entity);
        return true;
    }

    private static boolean carrying(Pending entry) {
        BlockData carried = entry.entity.getCarriedBlock();
        // Vanilla carries the default state, not necessarily the state it picked up.
        return carried != null && carried.getMaterial() == entry.data.getMaterial();
    }

    private static boolean inReach(Enderman entity, Location center) {
        return entity.getEyeLocation().distanceSquared(center) <= REACH * REACH;
    }

    private static boolean overlaps(Enderman entity, Block block) {
        return entity.getBoundingBox().overlaps(BoundingBox.of(block).expand(-0.3, 0, -0.3));
    }

    /**
     * Endermen look down from about 2.5 blocks up, so a ray to the center of a gap in a wall
     * often clips the block above it. Any visible point inside the gap counts.
     */
    private static boolean visible(Enderman entity, Block block) {
        Location corner = block.getLocation();
        for (double[] point : SIGHT_POINTS) {
            if (entity.hasLineOfSight(corner.clone().add(point[0], point[1], point[2]))) return true;
        }
        return false;
    }

    private static void stepAway(Enderman entity, Location center) {
        Vector away = entity.getLocation().toVector().subtract(center.toVector()).setY(0);
        if (away.lengthSquared() < 1.0E-4) away = new Vector(1, 0, 0);
        Location target = center.clone().add(away.normalize().multiply(2)).subtract(0, 0.5, 0);
        entity.getPathfinder().moveTo(target, 1.0);
    }

    void clear() {
        pending.clear();
        placements.clear();
    }
}
