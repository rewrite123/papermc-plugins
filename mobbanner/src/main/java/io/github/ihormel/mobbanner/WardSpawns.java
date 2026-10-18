package io.github.ihormel.mobbanner;

import java.util.function.Consumer;
import org.bukkit.Chunk;
import org.bukkit.block.Banner;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

final class WardSpawns implements Listener {
    private final BannerItems items;
    private final Consumer<Runnable> schedule;
    private final SpawnBoost boost;

    WardSpawns(BannerItems items, Consumer<Runnable> schedule, SpawnBoost boost) {
        this.items = items;
        this.schedule = schedule;
        this.boost = boost;
    }

    enum Mode { NONE, SUPPRESS, ATTRACT }

    Mode mode(Chunk chunk, EntityType type) {
        boolean attracts = false;
        var states = chunk.getTileEntities(
                block -> block.getType().name().endsWith("_BANNER"), false);
        for (var state : states) {
            if (state instanceof Banner banner && items.read(banner) == type) {
                if (!WardPyramid.powered(banner)) return Mode.SUPPRESS;
                attracts = true;
            }
        }
        return attracts ? Mode.ATTRACT : Mode.NONE;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!MobTypes.SUPPORTED.contains(event.getEntityType()) || !MobTypes.blocks(event.getSpawnReason())) return;
        if (mode(event.getLocation().getChunk(), event.getEntityType()) == Mode.SUPPRESS) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttract(CreatureSpawnEvent event) {
        if (boost.isSpawning() || !MobTypes.SUPPORTED.contains(event.getEntityType())
                || !MobTypes.blocks(event.getSpawnReason())
                || mode(event.getLocation().getChunk(), event.getEntityType()) != Mode.ATTRACT) return;
        var source = event.getEntity();
        var location = event.getLocation().clone();
        schedule.accept(() -> {
            if (event.isCancelled() || !source.isValid()
                    || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                    || mode(location.getChunk(), event.getEntityType()) != Mode.ATTRACT) return;
            boost.attempt(source, location);
        });
    }
}
