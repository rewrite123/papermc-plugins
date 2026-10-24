package io.github.ihormel.betterbarrels;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.plugin.java.JavaPlugin;

final class WorldScan {
    private record Region(UUID world, Path path, String key) {}
    private record Loaded(UUID world, int x, int z) {}
    private final JavaPlugin plugin;
    private final ScanState state;
    private final Conversion conversion;
    private final Queue<Region> regions = new ArrayDeque<>();
    private final Queue<Loaded> loadedChunks = new ArrayDeque<>();
    private final Set<UUID> worlds = new HashSet<>();
    private Region active;
    private boolean[] allocated;
    private int slot;
    private int sinceSave;
    private long chunks;
    private long converted;
    private String failure;

    WorldScan(JavaPlugin plugin, ScanState state, Conversion conversion) {
        this.plugin = plugin;
        this.state = state;
        this.conversion = conversion;
    }

    void start(boolean reset, boolean uninstall) throws IOException {
        if (reset) {
            for (World world : plugin.getServer().getWorlds()) world.save();
            state.reset(uninstall);
        }
        regions.clear();
        loadedChunks.clear();
        worlds.clear();
        active = null;
        failure = null;
        chunks = 0;
        converted = 0;
        for (World world : plugin.getServer().getWorlds()) addWorld(world);
        state.complete(false);
    }

    void addWorld(World world) throws IOException {
        if (!worlds.add(world.getUID())) return;
        state.complete(false);
        Path folder = world.getWorldFolder().toPath();
        for (Path regionFolder : new Path[]{folder.resolve("region"), folder.resolve("DIM-1/region"),
                folder.resolve("DIM1/region")}) {
            if (!Files.isDirectory(regionFolder)) continue;
            try (var files = Files.list(regionFolder)) {
                for (Path path : files.filter(RegionChunks::isRegion).sorted().toList()) {
                    String key = world.getUID() + "/" + folder.relativize(path);
                    if (state.cursor(key) < 1024) regions.add(new Region(world.getUID(), path, key));
                }
            }
        }
        for (Chunk chunk : world.getLoadedChunks()) {
            loadedChunks.add(new Loaded(world.getUID(), chunk.getX(), chunk.getZ()));
        }
    }

    void process(Chunk chunk) throws IOException {
        for (var tile : chunk.getTileEntities()) {
            if (tile instanceof Barrel barrel && conversion.prepare(barrel, state.uninstall())) converted++;
        }
    }

    void tick() {
        if (failure != null || state.complete()) return;
        try {
            if (!loadedChunks.isEmpty()) {
                Loaded loaded = loadedChunks.remove();
                World world = plugin.getServer().getWorld(loaded.world());
                if (world == null) throw new IOException("World unloaded during scan: " + loaded.world());
                visit(world, loaded.x(), loaded.z());
                return;
            }
            if (active == null) {
                active = regions.poll();
                if (active == null) {
                    for (World world : plugin.getServer().getWorlds()) world.save();
                    state.complete(true);
                    plugin.getLogger().info("Barrel scan complete. " + status());
                    return;
                }
                allocated = RegionChunks.allocated(active.path());
                slot = state.cursor(active.key());
                sinceSave = 0;
            }
            World world = plugin.getServer().getWorld(active.world());
            if (world == null) throw new IOException("World unloaded during scan: " + active.world());
            while (slot < 1024 && !allocated[slot]) slot++;
            if (slot < 1024) {
                var pos = RegionChunks.position(active.path(), slot);
                visit(world, pos.x(), pos.z());
                slot++;
                sinceSave++;
            }
            if (slot == 1024 || sinceSave >= 128) {
                // Save the world before advancing the durable cursor; replay is idempotent.
                world.save();
                state.cursor(active.key(), slot);
                state.save();
                sinceSave = 0;
            }
            if (slot == 1024) active = null;
        } catch (IOException | RuntimeException error) {
            fail(error);
        }
    }

    private void visit(World world, int x, int z) throws IOException {
        boolean loaded = world.isChunkLoaded(x, z);
        Chunk chunk = world.getChunkAt(x, z, false);
        if (chunk == null) throw new IOException("Unable to load generated chunk " + x + "," + z + " in " + world.getName());
        chunk.addPluginChunkTicket(plugin);
        try {
            process(chunk);
        } finally {
            chunk.removePluginChunkTicket(plugin);
            if (!loaded) world.unloadChunkRequest(x, z);
        }
        chunks++;
    }

    void fail(Exception error) {
        failure = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        plugin.getLogger().log(Level.SEVERE, "Barrel scan paused; do not uninstall. Fix the error, then /betterbarrels rescan", error);
    }

    String status() {
        return "Mode: " + (state.uninstall() ? "UNINSTALL (all barrels become chests)" : "legacy migration")
                + "; " + (failure != null ? "PAUSED: " + failure : state.complete() ? "COMPLETE" : "RUNNING")
                + "; chunks visited this run: " + chunks + "; barrels converted: " + converted
                + "; region files remaining: " + (regions.size() + (active == null ? 0 : 1))
                + "; initially loaded chunks queued: " + loadedChunks.size()
                + ". Scope: all worlds currently loaded in Bukkit.";
    }

    boolean safeToUninstall() {
        return state.uninstall() && state.complete() && failure == null;
    }
}
