package io.github.ihormel.betterbarrels;

import java.io.IOException;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

final class BarrelListener implements Listener {
    private final JavaPlugin plugin;
    private final BarrelStorage storage;
    private final Conversion conversion;
    private final ScanState state;
    private final WorldScan scan;

    BarrelListener(JavaPlugin plugin, BarrelStorage storage, Conversion conversion, ScanState state, WorldScan scan) {
        this.plugin = plugin;
        this.storage = storage;
        this.conversion = conversion;
        this.state = state;
        this.scan = scan;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.BARREL) return;
        boolean denied = event.useInteractedBlock() == Event.Result.DENY;
        event.setCancelled(true);
        if (denied || event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        Barrel barrel = (Barrel) event.getClickedBlock().getState();
        try {
            if (conversion.prepare(barrel, state.uninstall())) {
                player.sendMessage("This barrel was converted safely to a chest. Click it again to open.");
                return;
            }
            if (barrel.isLocked()) {
                player.sendMessage("This barrel is locked. An administrator must unlock it before use.");
                return;
            }
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
            Stock stock = storage.read(barrel);
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!hand.isEmpty()) {
                int accepted = stock.insert(hand, player.isSneaking() ? hand.getAmount() : 1);
                if (accepted == 0) {
                    player.sendActionBar(Component.text("Barrel full or item does not match (including item data)."));
                    return;
                }
                storage.write(barrel, stock);
                player.getInventory().setItemInMainHand(hand.asQuantity(hand.getAmount() - accepted));
            } else if (stock.count() > 0) {
                int requested = player.isSneaking() ? stock.type().getMaxStackSize() : 1;
                int room = room(player.getInventory(), stock.type(), requested);
                if (room == 0) {
                    player.sendActionBar(Component.text("Your inventory is full."));
                    return;
                }
                ItemStack taken = stock.take(room);
                storage.write(barrel, stock);
                for (ItemStack leftover : player.getInventory().addItem(taken).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
            player.sendActionBar(Component.text(stock.count() + " / " + Stock.CAPACITY + " ")
                    .append(stock.count() == 0 ? Component.text("(empty)") : stock.type().displayName()));
        } catch (IOException | RuntimeException error) {
            report(error);
            player.sendMessage("Barrel operation failed. Your administrator must check the server log and conversion backups.");
        }
    }

    static int room(Inventory inventory, ItemStack type, int limit) {
        int result = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.isEmpty()) result += Math.min(type.getMaxStackSize(), inventory.getMaxStackSize());
            else if (item.isSimilar(type)) {
                result += Math.max(0, Math.min(type.getMaxStackSize(), inventory.getMaxStackSize()) - item.getAmount());
            }
            if (result >= limit) return limit;
        }
        return result;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() instanceof Barrel) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof Barrel || event.getDestination().getHolder() instanceof Barrel) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) {
        if (!(event.getBlock().getState() instanceof Barrel barrel)) return;
        try {
            if (!barrel.getInventory().isEmpty() || storage.read(barrel).count() > 0) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("Empty this barrel before breaking it, or use /betterbarrels uninstall confirm.");
            }
        } catch (RuntimeException error) {
            event.setCancelled(true);
            report(error);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        if (!(event.getBlockPlaced().getState() instanceof Barrel barrel)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!(event.getBlockPlaced().getState() instanceof Barrel current)) return;
            try {
                conversion.prepare(current, state.uninstall());
            } catch (IOException | RuntimeException error) {
                report(error);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> block.getType() == Material.BARREL);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> block.getType() == Material.BARREL);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void burn(BlockBurnEvent event) {
        if (event.getBlock().getType() == Material.BARREL) event.setCancelled(true);
    }

    @EventHandler
    public void chunk(ChunkLoadEvent event) {
        try {
            scan.process(event.getChunk());
        } catch (IOException | RuntimeException error) {
            report(error);
        }
    }

    @EventHandler
    public void world(WorldLoadEvent event) {
        try {
            scan.addWorld(event.getWorld());
        } catch (IOException | RuntimeException error) {
            report(error);
        }
    }

    private void report(Exception error) {
        scan.fail(error);
        plugin.getLogger().log(Level.SEVERE, "BetterBarrels operation failed; data was not silently discarded", error);
    }
}
