package io.github.ihormel.reduceendermanblockdmg;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.entity.Enderman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ReduceEndermanBlockDamagePlugin extends JavaPlugin implements Listener {
    private BlockReturns returns;

    @Override
    public void onEnable() {
        returns = new BlockReturns(System::currentTimeMillis,
                () -> ThreadLocalRandom.current().nextLong(10_000, 45_001),
                getServer().getPluginManager()::callEvent, getServer()::getWorld,
                Bukkit::createBlockData, getLogger()::warning,
                world -> Boolean.TRUE.equals(world.getGameRuleValue(GameRules.MOB_GRIEFING)));
        getServer().getPluginManager().registerEvents(this, this);
        for (var world : getServer().getWorlds()) {
            for (Enderman entity : world.getEntitiesByClass(Enderman.class)) returns.load(entity);
        }
        getServer().getScheduler().runTaskTimer(this, returns::tick, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preventOtherPlacement(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Enderman entity && returns.tracked(entity)
                && !event.getTo().isAir() && !returns.ownPlacement(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void pickup(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Enderman entity
                && (event.getTo().isAir() || event.getTo() == Material.WATER)
                && !event.getBlock().isEmpty() && entity.getCarriedBlock() == null) {
            returns.pickedUp(entity, event.getBlock(), event.getBlock().getBlockData());
        }
    }

    @EventHandler
    public void death(EntityDeathEvent event) {
        if (event.getEntity() instanceof Enderman entity) returns.forget(entity);
    }

    @EventHandler
    public void load(EntitiesLoadEvent event) {
        for (var entity : event.getEntities()) {
            if (entity instanceof Enderman enderman) returns.load(enderman);
        }
    }

    @EventHandler
    public void unload(EntitiesUnloadEvent event) {
        for (var entity : event.getEntities()) {
            if (entity instanceof Enderman enderman) returns.unload(enderman);
        }
    }

    @Override
    public void onDisable() {
        if (returns != null) returns.clear();
    }
}
