package io.github.ihormel.betterlanterns;

import java.util.function.Consumer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Candle;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

final class LanternEvents implements Listener {
    private final LanternEngine engine;
    private final SoulItems items;
    private final Consumer<Runnable> nextTick;
    private final java.util.Set<Position> pending = new java.util.HashSet<>();
    private final java.util.Set<java.util.UUID> handledInteractions = new java.util.HashSet<>();

    LanternEvents(LanternEngine engine, SoulItems items, Consumer<Runnable> nextTick) {
        this.engine = engine;
        this.items = items;
        this.nextTick = nextTick;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void loaded(ChunkLoadEvent event) { engine.load(event.getChunk()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void unloaded(ChunkUnloadEvent event) { engine.unload(event.getChunk()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void spawn(CreatureSpawnEvent event) {
        if (engine.protects(event.getEntity(), event.getSpawnReason())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preventMixing(BlockPlaceEvent event) {
        boolean wasSoul = engine.isSoul(Position.of(event.getBlockPlaced()));
        if (event.getBlockReplacedState().getType() == Material.CANDLE
                && wasSoul != items.isSoul(event.getItemInHand())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("[betterlanterns] Soul candles and ordinary candles cannot share a block.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void placed(BlockPlaceEvent event) {
        if (!event.canBuild()) return;
        var position = Position.of(event.getBlockPlaced());
        if (items.isSoul(event.getItemInHand()) && event.getBlockPlaced().getType() == Material.CANDLE) {
            engine.candle(position, engine.isLit(position));
        }
        observe(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void broken(BlockBreakEvent event) { observe(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void physics(BlockPhysicsEvent event) { observe(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void flowed(BlockFromToEvent event) { observe(event.getToBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void burned(BlockBurnEvent event) { observe(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void faded(BlockFadeEvent event) { observe(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void exploded(BlockExplodeEvent event) { event.blockList().forEach(this::observe); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void exploded(EntityExplodeEvent event) { event.blockList().forEach(this::observe); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent event) {
        event.getBlocks().forEach(block -> { observe(block); observe(block.getRelative(event.getDirection())); });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void piston(BlockPistonRetractEvent event) {
        event.getBlocks().forEach(block -> { observe(block); observe(block.getRelative(event.getDirection())); });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void brokenDrops(BlockDropItemEvent event) {
        var position = Position.of(event.getBlock());
        if (!engine.isSoul(position)) return;
        for (var item : event.getItems()) {
            if (item.getItemStack().getType() == Material.CANDLE && !items.isSoul(item.getItemStack())
                    && engine.claimCandleDrops(position, item.getItemStack().getAmount())) {
                item.setItemStack(items.candle(item.getItemStack().getAmount()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void dropped(ItemSpawnEvent event) {
        if (event.getEntity().getItemStack().getType() == Material.CANDLE
                && !items.isSoul(event.getEntity().getItemStack())
                && event.getLocation().getBlock().getType() != Material.CANDLE
                && engine.isSoul(Position.of(event.getLocation().getBlock()))
                && engine.claimCandleDrops(Position.of(event.getLocation().getBlock()), event.getEntity().getItemStack().getAmount())) {
            event.getEntity().setItemStack(items.candle(event.getEntity().getItemStack().getAmount()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.useInteractedBlock() == Event.Result.DENY) return;
        Block block = event.getClickedBlock();
        var position = Position.of(block);
        boolean soulItem = items.isSoul(event.getItem());
        if (soulItem && block.getType() == Material.CAKE) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("[betterlanterns] Place soul candles on a solid surface, not on a cake.");
            return;
        }
        if (block.getType() != Material.CANDLE) return;
        boolean soulBlock = engine.isSoul(position);
        if (event.getItem() != null && event.getItem().getType() == Material.CANDLE && soulItem != soulBlock
                && !event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("[betterlanterns] Soul candles and ordinary candles cannot share a block.");
            return;
        }
        if (!soulBlock || event.getPlayer().isSneaking()) return;
        if (handledInteractions.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        Material held = event.getItem() == null ? Material.AIR : event.getItem().getType();
        boolean ignition = held == Material.FLINT_AND_STEEL || held == Material.FIRE_CHARGE;
        if (!ignition && held != Material.AIR) return;
        boolean lit = engine.isLit(position);
        if ((ignition && lit) || (!ignition && !lit)) return;
        if (event.useItemInHand() == Event.Result.DENY && ignition) return;
        var data = (Candle) block.getBlockData();
        if (ignition && data.isWaterlogged()) return;
        event.setCancelled(true);
        var playerId = event.getPlayer().getUniqueId();
        handledInteractions.add(playerId);
        nextTick.accept(() -> handledInteractions.remove(playerId));
        engine.candle(position, ignition);
        block.getWorld().playSound(block.getLocation(), ignition ? Sound.ITEM_FLINTANDSTEEL_USE : Sound.BLOCK_CANDLE_EXTINGUISH,
                1, 1);
        if (ignition && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            var item = event.getItem();
            var remaining = held == Material.FIRE_CHARGE ? item.subtract(1) : item.damage(1, event.getPlayer());
            if (event.getHand() == EquipmentSlot.OFF_HAND) event.getPlayer().getInventory().setItemInOffHand(remaining);
            else event.getPlayer().getInventory().setItemInMainHand(remaining);
        }
    }

    private void observe(Block block) {
        var position = Position.of(block);
        if (!LanternEngine.isLantern(block.getType()) && !engine.isSoul(position) && !engine.nearby(position)) return;
        engine.changed(position);
        if (!pending.add(position)) return;
        nextTick.accept(() -> {
            pending.remove(position);
            var world = block.getWorld();
            if (!world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) return;
            if (LanternEngine.isLantern(block.getType())) engine.source(position, true);
            else if (block.getType() != Material.CANDLE) {
                engine.removeSource(position);
                engine.removeCandle(position);
            }
            engine.changed(position);
        });
    }
}
