package io.github.ihormel.reduceendermanblockdmg;

import java.util.ArrayList;
import java.util.HashMap;
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
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Enderman;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

final class BlockReturns {
    static final NamespacedKey ORIGIN = new NamespacedKey("reduceendermanblockdmg", "origin");
    private record Pending(Enderman entity, ReturnOrigin origin, BlockData data) {}
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
        return entity.getPersistentDataContainer().has(ORIGIN, PersistentDataType.STRING);
    }

    boolean ownPlacement(EntityChangeBlockEvent event) {
        return placements.get(event.getEntity().getUniqueId()) == event;
    }

    void load(Enderman entity) {
        String value = entity.getPersistentDataContainer().get(ORIGIN, PersistentDataType.STRING);
        if (value == null) return;
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
    }

    void tick() {
        for (Pending entry : new ArrayList<>(pending.values())) {
            Enderman entity = entry.entity();
            if (entity.isDead()) {
                forget(entity);
                continue;
            }
            if (!entity.isValid()) {
                unload(entity);
                continue;
            }
            BlockData carried = entity.getCarriedBlock();
            // Vanilla carries the default state, not necessarily the state it picked up.
            if (carried == null || carried.getMaterial() != entry.data().getMaterial()) {
                forget(entity);
                continue;
            }
            if (clock.getAsLong() < entry.origin().due() || entity.getTarget() != null
                    || !entity.hasAI()) continue;
            World world = worlds.apply(entry.origin().world());
            if (world == null || world != entity.getWorld()
                    || !griefing.test(world)
                    || !world.isChunkLoaded(entry.origin().x() >> 4, entry.origin().z() >> 4)) continue;
            if (entry.origin().y() < world.getMinHeight() || entry.origin().y() >= world.getMaxHeight()) {
                warning.accept("Enderman return location is outside world height: " + entity.getUniqueId());
                forget(entity);
                continue;
            }
            Block original = world.getBlockAt(entry.origin().x(), entry.origin().y(), entry.origin().z());
            if (!original.isEmpty()) continue;
            Location destination = original.getLocation().add(0.5, 0, 0.5);
            if (entity.getLocation().distanceSquared(destination) > 6.25) {
                entity.getPathfinder().moveTo(destination, 1.0);
                continue;
            }
            entity.getPathfinder().stopPathfinding();
            BoundingBox space = BoundingBox.of(original).expand(-0.3, 0, -0.3);
            if (entity.getBoundingBox().overlaps(space)) continue;
            // Aim at the block's center: a ray ending on the floor of the gap clips the block beneath it.
            if (!entity.hasLineOfSight(original.getLocation().add(0.5, 0.5, 0.5))) continue;
            EntityChangeBlockEvent placement = new EntityChangeBlockEvent(entity, original, entry.data());
            placements.put(entity.getUniqueId(), placement);
            try {
                events.accept(placement);
            } finally {
                placements.remove(entity.getUniqueId());
            }
            // Protection handlers may change the world or entity while handling the event.
            if (placement.isCancelled() || !original.isEmpty() || entity.isDead() || !entity.isValid()
                    || entity.getWorld() != world
                    || entity.getLocation().distanceSquared(destination) > 6.25
                    || entity.getCarriedBlock() == null
                    || entity.getCarriedBlock().getMaterial() != entry.data().getMaterial()
                    || !griefing.test(world)) continue;
            original.setBlockData(entry.data(), true);
            entity.setCarriedBlock(null);
            forget(entity);
        }
    }

    void clear() {
        pending.clear();
        placements.clear();
    }
}
