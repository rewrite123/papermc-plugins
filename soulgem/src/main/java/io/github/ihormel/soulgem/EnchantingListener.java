package io.github.ihormel.soulgem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Makes the vanilla enchanting table run on charged soul gems instead of lapis lazuli. */
final class EnchantingListener implements Listener {
    static final Component LAPIS_REFUSED = Component.text(
            "Enchanting uses charged soul gems instead of lapis lazuli.", NamedTextColor.LIGHT_PURPLE);
    private final GemItems gems;

    EnchantingListener(GemItems gems) {
        this.gems = gems;
    }

    private static ItemStack fuel(Inventory inventory) {
        return inventory instanceof EnchantingInventory table ? table.getSecondary() : null;
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static boolean phantomRepellant(ItemStack fuel) {
        return fuel != null && fuel.getType() == Material.LAPIS_LAZULI && fuel.hasItemMeta()
                && Byte.valueOf((byte) 1).equals(fuel.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey("phantommenace", "repellant"), PersistentDataType.BYTE))
                && Bukkit.getPluginManager().isPluginEnabled("phantommenace");
    }

    /**
     * Runs first so other plugins can still veto. Paper pre-cancels this event for items
     * that already carry enchantments; soul gems may upgrade those, so it is un-cancelled.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void prepare(PrepareItemEnchantEvent event) {
        var player = event.getEnchanter();
        var fuel = fuel(event.getInventory());
        if (phantomRepellant(fuel)) return;
        if (!empty(fuel) && !gems.charged(fuel)) {
            event.setCancelled(true);
            if (fuel.getType() == Material.LAPIS_LAZULI) player.sendActionBar(LAPIS_REFUSED);
            return;
        }
        if (!allowed(player)) {
            event.setCancelled(true);
            return;
        }
        if (event.isCancelled() && upgradeable(event.getItem()) && hasOffer(event)) event.setCancelled(false);
        if (event.isCancelled() || gems.kind(fuel) != GemItems.Kind.ECHO) return;
        for (var offer : event.getOffers()) {
            if (offer != null) {
                offer.setEnchantmentLevel(EnchantingRules.boosted(offer.getEnchantment(),
                        offer.getEnchantmentLevel(), true));
            }
        }
    }

    private static boolean upgradeable(ItemStack item) {
        return item != null && item.getAmount() == 1 && item.getType() != Material.ENCHANTED_BOOK
                && !item.getEnchantments().isEmpty();
    }

    private static boolean hasOffer(PrepareItemEnchantEvent event) {
        for (var offer : event.getOffers()) {
            if (offer != null && offer.getCost() > 0) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void enchant(EnchantItemEvent event) {
        var player = event.getEnchanter();
        var fuel = fuel(event.getInventory());
        if (phantomRepellant(fuel)) return;
        boolean creativeWithoutFuel = empty(fuel) && player.getGameMode() == GameMode.CREATIVE;
        if (!allowed(player) || (!gems.charged(fuel) && !creativeWithoutFuel)) {
            event.setCancelled(true);
            player.sendMessage(LAPIS_REFUSED);
            return;
        }
        var added = EnchantingRules.improvements(EnchantingRules.enchantments(event.getItem()),
                event.getEnchantsToAdd(), gems.kind(fuel) == GemItems.Kind.ECHO);
        if (added.isEmpty()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("That offer would not improve this item. "
                    + "Try another offer or change your bookshelves.", NamedTextColor.RED));
            return;
        }
        event.getEnchantsToAdd().clear();
        event.getEnchantsToAdd().putAll(added);
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        upgradeLegacy(event.getPlayer().getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        upgradeLegacy(event.getPlayer().getInventory());
        upgradeLegacy(event.getInventory());
    }

    void upgradeLegacy(Inventory inventory) {
        var contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            var upgraded = gems.upgradeLegacy(contents[slot]);
            if (upgraded != null) inventory.setItem(slot, upgraded);
        }
    }

    private static boolean allowed(Player player) {
        return player.hasPermission("soulgem.use") && !player.isDead()
                && player.getGameMode() != GameMode.SPECTATOR;
    }
}
