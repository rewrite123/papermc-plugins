package io.github.ihormel.minecarttravel;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Rail;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Re-checks registered route blocks after nearby block changes and whenever a
 * route chunk loads. Changes are only queued here; checks run a tick later, after
 * the change has been applied, under a global per-tick budget.
 */
final class RouteWatcher implements Listener {
    private final RouteRegistry registry;
    private final int budget;
    private final Function<UUID, World> worlds;
    private final Consumer<String> log;
    private final Set<TrackPos> queue = new LinkedHashSet<>();
    private final Map<TrackPos.ChunkKey, Integer> pending = new HashMap<>();

    RouteWatcher(RouteRegistry registry, int budget, Function<UUID, World> worlds, Consumer<String> log) {
        this.registry = registry;
        this.budget = budget;
        this.worlds = worlds;
        this.log = log;
    }

    void changed(Block block) {
        var world = block.getWorld().getUID();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        // Most block updates happen far from any route; reject those with chunk lookups only.
        if (!registry.touches(new TrackPos.ChunkKey(world, (x - 1) >> 4, (z - 1) >> 4))
                && !registry.touches(new TrackPos.ChunkKey(world, (x + 1) >> 4, (z - 1) >> 4))
                && !registry.touches(new TrackPos.ChunkKey(world, (x - 1) >> 4, (z + 1) >> 4))
                && !registry.touches(new TrackPos.ChunkKey(world, (x + 1) >> 4, (z + 1) >> 4))) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    var pos = new TrackPos(world, x + dx, y + dy, z + dz);
                    if (registry.watched(pos)) enqueue(pos);
                }
            }
        }
    }

    void loaded(Chunk chunk) {
        var key = new TrackPos.ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        if (!registry.touches(key)) return;
        for (var check : registry.checksIn(key)) enqueue(check.pos());
    }

    int pending() {
        return queue.size();
    }

    private void enqueue(TrackPos pos) {
        if (!queue.add(pos)) return;
        pending.merge(pos.chunk(), 1, Integer::sum);
        registry.unverified.add(pos.chunk());
    }

    void tick() {
        var iterator = queue.iterator();
        for (int remaining = budget; remaining > 0 && iterator.hasNext(); remaining--) {
            var pos = iterator.next();
            iterator.remove();
            var chunk = pos.chunk();
            boolean done = pending.merge(chunk, -1, Integer::sum) == 0;
            if (done) pending.remove(chunk);
            var world = worlds.apply(pos.world());
            if (world == null || !world.isChunkLoaded(chunk.x(), chunk.z())) {
                // Leave the chunk unverified; loading it again queues a full check.
                continue;
            }
            verify(world, pos);
            if (done) registry.unverified.remove(chunk);
        }
    }

    private void verify(World world, TrackPos pos) {
        if (pos.y() < world.getMinHeight() || pos.y() >= world.getMaxHeight()) return;
        var block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        var hit = registry.at(pos);
        if (hit != null && !(block.getBlockData() instanceof Rail rail
                && RailShape.of(rail) == hit.segment().lane().shape())) {
            unregister(hit.route(), "rail at " + pos.x() + " " + pos.y() + " " + pos.z() + " changed");
        }
        if (block.getType() != Material.LODESTONE) {
            for (var route : registry.lodestoneRoutes(pos)) {
                unregister(route, "lodestone at " + pos.x() + " " + pos.y() + " " + pos.z() + " was removed");
            }
        }
    }

    private void unregister(RouteRegistry.Route route, String reason) {
        if (!registry.contains(route)) return;
        registry.remove(route);
        var a = route.first().lodestone();
        var b = route.second().lodestone();
        log.accept("Unregistered fast-travel route between lodestones at " + a.x() + " " + a.y() + " " + a.z()
                + " and " + b.x() + " " + b.y() + " " + b.z() + ": " + reason + ".");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void load(ChunkLoadEvent event) { loaded(event.getChunk()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void physics(BlockPhysicsEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void flow(BlockFromToEvent event) { changed(event.getToBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void burn(BlockBurnEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void fade(BlockFadeEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void destroy(com.destroystokyo.paper.event.block.BlockDestroyEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void entityChange(EntityChangeBlockEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void explosion(EntityExplodeEvent event) { event.blockList().forEach(this::changed); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void explosion(BlockExplodeEvent event) { event.blockList().forEach(this::changed); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent event) {
        event.getBlocks().forEach(block -> {
            changed(block);
            changed(block.getRelative(event.getDirection()));
        });
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void piston(BlockPistonRetractEvent event) {
        event.getBlocks().forEach(block -> {
            changed(block);
            changed(block.getRelative(event.getDirection()));
        });
    }
}
