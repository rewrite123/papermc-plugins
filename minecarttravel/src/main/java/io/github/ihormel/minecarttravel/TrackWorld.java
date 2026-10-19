package io.github.ihormel.minecarttravel;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
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
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

final class TrackWorld implements Listener {
    private final RailGraph graph;
    private final int budget;
    private final Set<TrackPos> edits = new LinkedHashSet<>();
    private final ArrayDeque<TrackPos.ChunkKey> scans = new ArrayDeque<>();
    private final Set<TrackPos.ChunkKey> queued = new HashSet<>();
    private final List<org.bukkit.block.data.BlockData> trackedStates;
    private Scan current;

    private static final class Scan {
        final TrackPos.ChunkKey key;
        final int minY;
        final int height;
        int cursor;

        Scan(Chunk chunk) {
            key = new TrackPos.ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
            minY = chunk.getWorld().getMinHeight();
            height = chunk.getWorld().getMaxHeight() - minY;
        }
    }

    TrackWorld(RailGraph graph, int budget) {
        this.graph = graph;
        this.budget = budget;
        trackedStates = trackedStates();
    }

    private static List<org.bukkit.block.data.BlockData> trackedStates() {
        var states = new ArrayList<org.bukkit.block.data.BlockData>();
        states.add(Material.LODESTONE.createBlockData());
        for (var material : List.of(Material.RAIL, Material.POWERED_RAIL,
                Material.DETECTOR_RAIL, Material.ACTIVATOR_RAIL)) {
            var rail = (Rail) material.createBlockData();
            for (var shape : rail.getShapes()) {
                for (boolean water : List.of(false, true)) {
                    for (boolean powered : List.of(false, true)) {
                        var state = (Rail) rail.clone();
                        state.setShape(shape);
                        if (state instanceof org.bukkit.block.data.Waterlogged data) data.setWaterlogged(water);
                        if (state instanceof org.bukkit.block.data.Powerable data) data.setPowered(powered);
                        if (!states.contains(state)) states.add(state);
                    }
                }
            }
        }
        return List.copyOf(states);
    }

    void discover(Chunk chunk) {
        var key = new TrackPos.ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        if (graph.dirty.contains(key)) {
            enqueue(chunk);
        } else if (graph.knownChunks.contains(key)) {
            var observations = graph.observationsInChunk(key);
            if (!observations.isEmpty()) {
                graph.dirty.add(key);
                edits.addAll(observations);
            }
        } else if (trackedStates.stream().anyMatch(chunk::contains)) {
            enqueue(chunk);
        } else {
            graph.knownChunks.add(key);
        }
    }

    void enqueue(Chunk chunk) {
        var key = new TrackPos.ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        graph.dirty.add(key);
        graph.invalidate();
        if (queued.add(key)) scans.add(key);
    }

    void changed(Block block) {
        if (!relevant(block)) return;
        var pos = TrackPos.at(block.getLocation());
        graph.invalidate();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    var nearby = pos.add(dx, dy, dz);
                    if (!block.getWorld().isChunkLoaded(nearby.x() >> 4, nearby.z() >> 4)) continue;
                    edits.add(nearby);
                    graph.dirty.add(nearby.chunk());
                }
            }
        }
    }

    private boolean relevant(Block block) {
        var pos = TrackPos.at(block.getLocation());
        if (block.getBlockData() instanceof Rail || block.getType() == Material.LODESTONE
                || graph.rail(pos) != null || graph.stopper(pos)) return true;
        for (int dy = 1; dy <= 2; dy++) {
            if (graph.rail(pos.add(0, dy, 0)) != null) return true;
        }
        return false;
    }

    void tick() {
        int remaining = budget;
        var iterator = edits.iterator();
        var touched = new HashSet<TrackPos.ChunkKey>();
        while (iterator.hasNext() && remaining-- > 0) {
            var pos = iterator.next();
            iterator.remove();
            var world = Bukkit.getWorld(pos.world());
            if (world == null || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
                // Retain the dirty marker until a later chunk-load reconciliation.
                continue;
            }
            if (pos.y() >= world.getMinHeight() && pos.y() < world.getMaxHeight()) {
                observe(world.getBlockAt(pos.x(), pos.y(), pos.z()));
            }
            touched.add(pos.chunk());
        }
        var stillPending = new HashSet<TrackPos.ChunkKey>();
        for (var pos : edits) stillPending.add(pos.chunk());
        for (var key : touched) {
            if (!stillPending.contains(key) && !queued.contains(key)) graph.dirty.remove(key);
        }
        while (remaining-- > 0) {
            if (current == null) {
                var key = scans.poll();
                if (key == null) return;
                var world = Bukkit.getWorld(key.world());
                if (world == null || !world.isChunkLoaded(key.x(), key.z())) {
                    queued.remove(key);
                    continue;
                }
                current = new Scan(world.getChunkAt(key.x(), key.z()));
                graph.clearChunk(key);
            }
            var world = Bukkit.getWorld(current.key.world());
            if (world == null || !world.isChunkLoaded(current.key.x(), current.key.z())) {
                queued.remove(current.key);
                current = null;
                continue;
            }
            int index = current.cursor++;
            int x = current.key.x() * 16 + (index & 15);
            int z = current.key.z() * 16 + ((index >> 4) & 15);
            int y = current.minY + (index >> 8);
            var block = world.getBlockAt(x, y, z);
            if (block.getType() == Material.LODESTONE || block.getBlockData() instanceof Rail) observe(block);
            if (current.cursor == current.height * 256) {
                queued.remove(current.key);
                graph.knownChunks.add(current.key);
                if (!stillPending.contains(current.key)) graph.dirty.remove(current.key);
                graph.invalidate();
                current = null;
            }
        }
    }

    private void observe(Block block) {
        graph.observe(TrackPos.at(block.getLocation()),
                block.getBlockData() instanceof Rail rail ? RailShape.of(rail) : null,
                block.getType() == Material.LODESTONE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void load(ChunkLoadEvent event) { discover(event.getChunk()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void unload(ChunkUnloadEvent event) {
        var chunk = event.getChunk();
        var key = new TrackPos.ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        if (edits.stream().anyMatch(pos -> pos.chunk().equals(key))) graph.dirty.add(key);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void physics(BlockPhysicsEvent event) { changed(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void redstone(BlockRedstoneEvent event) { changed(event.getBlock()); }
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
