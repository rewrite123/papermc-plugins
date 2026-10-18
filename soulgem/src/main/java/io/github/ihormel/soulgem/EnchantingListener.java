package io.github.ihormel.soulgem;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

final class EnchantingListener implements Listener {
    private final JavaPlugin plugin;
    private final GemItems gems;
    private final EnchantingOffers offers;

    EnchantingListener(JavaPlugin plugin, GemItems gems, EnchantingOffers offers) {
        this.plugin = plugin;
        this.gems = gems;
        this.offers = offers;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.useInteractedBlock() == Event.Result.DENY || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.ENCHANTING_TABLE) return;
        var player = event.getPlayer();
        if (player.isSneaking()) return;
        var inventory = player.getInventory();
        var input = inventory.getItemInMainHand();
        if (input.getType() == Material.GLASS_BOTTLE && plugin.getServer().getPluginManager()
                .isPluginEnabled("craftablebottleofenchanting")) return;
        event.setCancelled(true);
        if (!allowed(player)) {
            player.sendMessage("You cannot use soul gem enchanting right now.");
            return;
        }
        var fuel = inventory.getItemInOffHand();
        if (!gems.charged(fuel)) {
            player.sendMessage("Hold gear in your main hand and charged soul gems in your off-hand.");
            return;
        }
        if (input.getType().isAir() || input.getAmount() != 1 || gems.marked(input)
                || !player.getItemOnCursor().getType().isAir()) {
            player.sendMessage("Hold one piece of gear or one book, and empty your cursor.");
            return;
        }
        var table = event.getClickedBlock().getLocation();
        int shelves = offers.bookshelves(table);
        var choices = offers.create(input, gems.kind(fuel), shelves, player.getEnchantmentSeed());
        if (choices.stream().allMatch(choice -> choice.result() == null)) {
            player.sendMessage("No compatible improvements are available for this item. Try more bookshelves.");
            return;
        }
        player.openInventory(new GemMenu(player, table, shelves, choices).getInventory());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GemMenu menu)) return;
        boolean cancelled = event.isCancelled();
        event.setCancelled(true);
        int index = GemMenu.offerIndex(event.getRawSlot());
        if (cancelled || index < 0 || !(event.getWhoClicked() instanceof Player player)
                || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) return;
        var view = event.getView();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory() != view || menu.used) return;
            commit(player, menu, index);
        });
    }

    void commit(Player player, GemMenu menu, int index) {
        if (menu.used) return;
        if (!allowed(player) || !player.getUniqueId().equals(menu.owner)) {
            player.sendMessage("You cannot use soul gem enchanting right now.");
            return;
        }
        var table = menu.table;
        if (!table.getWorld().equals(player.getWorld())
                || !table.getWorld().isChunkLoaded(table.getBlockX() >> 4, table.getBlockZ() >> 4)
                || table.clone().add(0.5, 0.5, 0.5).distanceSquared(player.getLocation()) > 36
                || table.getBlock().getType() != Material.ENCHANTING_TABLE) {
            player.sendMessage("Stay near the enchanting table to enchant.");
            player.closeInventory();
            return;
        }
        var inventory = player.getInventory();
        if (inventory.getHeldItemSlot() != menu.heldSlot
                || !menu.gear.equals(inventory.getItemInMainHand())
                || !menu.fuel.equals(inventory.getItemInOffHand())
                || player.getEnchantmentSeed() != menu.seed
                || offers.bookshelves(table) != menu.shelves
                || !player.getItemOnCursor().getType().isAir()) {
            player.sendMessage("Your items or enchanting setup changed. Reopen the enchanting table.");
            player.closeInventory();
            return;
        }
        var offer = menu.offers.get(index);
        if (offer.result() == null) {
            player.sendMessage("This offer has no compatible improvements.");
            return;
        }
        if (player.getLevel() < Math.max(offer.requiredLevel(), offer.cost())
                || !gems.charged(menu.fuel) || menu.fuel.getAmount() < offer.cost()) {
            player.sendMessage("This offer requires level " + offer.requiredLevel() + " and "
                    + offer.cost() + " charged soul gems; it consumes " + offer.cost() + " levels.");
            return;
        }
        menu.used = true;
        var fuel = menu.fuel.clone();
        fuel.setAmount(fuel.getAmount() - offer.cost());
        inventory.setItemInMainHand(offer.result().clone());
        inventory.setItemInOffHand(fuel.getAmount() == 0 ? new ItemStack(Material.AIR) : fuel);
        player.giveExpLevels(-offer.cost());
        player.setEnchantmentSeed(ThreadLocalRandom.current().nextInt());
        player.closeInventory();
        player.sendMessage("Your gear has been enchanted using " + offer.cost() + " charged soul gems.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GemMenu) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void nativeOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() == InventoryType.ENCHANTING) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("Use charged soul gems in your off-hand at an enchanting table.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void nativePrepare(PrepareItemEnchantEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void nativeEnchant(EnchantItemEvent event) {
        event.setCancelled(true);
    }

    private boolean allowed(Player player) {
        return player.hasPermission("soulgem.use") && !player.isDead()
                && player.getGameMode() != GameMode.SPECTATOR;
    }
}
