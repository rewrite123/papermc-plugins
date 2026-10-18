package io.github.ihormel.mobbanner;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;

final class WardBlocks implements Listener {
    private final BannerItems items;
    private final Logger logger;

    WardBlocks(BannerItems items, Logger logger) {
        this.items = items;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        var type = items.read(event.getItemInHand());
        if (type == null || !event.canBuild()) return;
        if (!(event.getBlockPlaced().getState() instanceof Banner banner)) {
            event.setCancelled(true);
            logger.severe("Ward placement did not produce a banner at " + event.getBlockPlaced().getLocation());
            return;
        }
        items.write(banner.getPersistentDataContainer(), type);
        if (!banner.update(false, false)) {
            event.setCancelled(true);
            logger.severe("Could not persist mob ward at " + banner.getLocation());
            event.getPlayer().sendMessage("Could not place the ward banner. Please contact an administrator.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        if (!(event.getBlockState() instanceof Banner banner)) return;
        var type = items.read(banner);
        if (type == null) return;
        for (var drop : event.getItems()) {
            if (drop.getItemStack().getType().name().endsWith("_BANNER")) {
                drop.setItemStack(items.create(type));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        explode(event.blockList(), event.getYield());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        explode(event.blockList(), event.getYield());
    }

    private void explode(List<Block> blocks, float yield) {
        var iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (!(block.getState() instanceof Banner banner)) continue;
            var type = items.read(banner);
            if (type == null) continue;
            // Replace the vanilla explosion drop with a marked ward item, never both.
            iterator.remove();
            block.setType(Material.AIR, false);
            if (ThreadLocalRandom.current().nextFloat() < yield) {
                block.getWorld().dropItemNaturally(block.getLocation(), items.create(type));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Banner banner)) return;
        var type = items.read(banner);
        if (type == null) return;
        BlockFace support = block.getBlockData() instanceof Directional directional
                ? directional.getFacing().getOppositeFace() : BlockFace.DOWN;
        if (block.getRelative(support).getType().isSolid()) return;
        event.setCancelled(true);
        block.setType(Material.AIR, false);
        block.getWorld().dropItemNaturally(block.getLocation(), items.create(type));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block block = event.getToBlock();
        if (!(block.getState() instanceof Banner banner)) return;
        var type = items.read(banner);
        if (type == null) return;
        block.setType(Material.AIR, false);
        block.getWorld().dropItemNaturally(block.getLocation(), items.create(type));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent event) {
        if (hitsWard(event.getBlock(), event.getDirection(), event.getBlocks())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent event) {
        if (hitsWard(event.getBlock(), event.getDirection(), event.getBlocks())) event.setCancelled(true);
    }

    private boolean hitsWard(Block piston, BlockFace direction, List<Block> moved) {
        if (ward(piston.getRelative(direction))) return true;
        return moved.stream().anyMatch(block -> ward(block) || ward(block.getRelative(direction)));
    }

    private boolean ward(Block block) {
        return block.getState() instanceof Banner banner && items.read(banner) != null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        for (var input : event.getInventory().getMatrix()) {
            if (items.read(input) != null) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }
}
