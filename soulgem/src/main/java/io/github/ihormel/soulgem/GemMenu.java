package io.github.ihormel.soulgem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

final class GemMenu implements InventoryHolder {
    private final Inventory inventory;
    final UUID owner;
    final Location table;
    final int heldSlot;
    final ItemStack gear;
    final ItemStack fuel;
    final int shelves;
    final int seed;
    final List<EnchantingOffers.Offer> offers;
    boolean used;

    GemMenu(Player player, Location table, int shelves, List<EnchantingOffers.Offer> offers) {
        owner = player.getUniqueId();
        this.table = table.clone();
        this.shelves = shelves;
        this.offers = List.copyOf(offers);
        heldSlot = player.getInventory().getHeldItemSlot();
        gear = player.getInventory().getItemInMainHand().clone();
        fuel = player.getInventory().getItemInOffHand().clone();
        seed = player.getEnchantmentSeed();
        inventory = Bukkit.createInventory(this, 9, Component.text("Soul Gem Enchanting"));
        inventory.setItem(0, gear.clone());
        inventory.setItem(8, fuel.clone());
        for (int index = 0; index < offers.size(); index++) {
            var offer = offers.get(index);
            var icon = offer.result() == null ? new ItemStack(Material.BARRIER) : offer.result().clone();
            var meta = icon.getItemMeta();
            meta.displayName(Component.text("Offer " + (index + 1), NamedTextColor.GOLD));
            var lore = new ArrayList<Component>();
            if (offer.result() == null) {
                lore.add(Component.text("No compatible improvement available.", NamedTextColor.RED));
            } else {
                lore.add(Component.text("Requires level " + offer.requiredLevel(), NamedTextColor.GRAY));
                lore.add(Component.text("Costs " + offer.cost() + " levels + " + offer.cost()
                        + " charged gems", NamedTextColor.GRAY));
                EnchantingOffers.enchantments(offer.result()).entrySet().stream()
                        .sorted(java.util.Comparator.comparing(e -> e.getKey().getKey().toString()))
                        .forEach(entry -> lore.add(Component.translatable(entry.getKey())
                                .append(Component.text(" " + entry.getValue()))));
            }
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(2 + index * 2, icon);
        }
    }

    static int offerIndex(int slot) {
        return slot == 2 || slot == 4 || slot == 6 ? (slot - 2) / 2 : -1;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
