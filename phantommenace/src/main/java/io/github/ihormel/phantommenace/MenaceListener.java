package io.github.ihormel.phantommenace;

import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import java.util.Arrays;
import org.bukkit.GameMode;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.ItemStack;

final class MenaceListener implements Listener {
    private final Enchantment enchantment;

    MenaceListener(Enchantment enchantment) {
        this.enchantment = enchantment;
    }

    static boolean helmet(ItemStack item) {
        return item != null && item.getAmount() == 1 && switch (item.getType()) {
            case LEATHER_HELMET, CHAINMAIL_HELMET, IRON_HELMET, GOLDEN_HELMET, COPPER_HELMET,
                    DIAMOND_HELMET, NETHERITE_HELMET, TURTLE_HELMET -> true;
            default -> false;
        };
    }

    private boolean eligible(Player player, ItemStack item) {
        return helmet(item) && !item.containsEnchantment(enchantment)
                && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void prepare(PrepareItemEnchantEvent event) {
        if (!(event.getInventory() instanceof EnchantingInventory table)
                || !RepellantItems.isRepellant(table.getSecondary())) return;
        Arrays.fill(event.getOffers(), null);
        if (!eligible(event.getEnchanter(), event.getItem())) {
            event.setCancelled(true);
            return;
        }
        event.getOffers()[0] = new EnchantmentOffer(enchantment, 1, 1);
        // Vanilla pre-cancels enchanted helmets; this fuel adds one specific enchantment.
        event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void enchant(EnchantItemEvent event) {
        if (!(event.getInventory() instanceof EnchantingInventory table)
                || !RepellantItems.isRepellant(table.getSecondary())) return;
        if (event.whichButton() != 0 || !eligible(event.getEnchanter(), event.getItem())) {
            event.setCancelled(true);
            event.getEnchanter().sendMessage("Phantom repellant only adds Phantom Menace to an unprotected helmet.");
            return;
        }
        event.setExpLevelCost(1);
        event.getEnchantsToAdd().clear();
        event.getEnchantsToAdd().put(enchantment, 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void spawn(PhantomPreSpawnEvent event) {
        if (event.getReason() == SpawnReason.NATURAL && event.getSpawningEntity() instanceof Player player) {
            var helmet = player.getInventory().getHelmet();
            if (helmet(helmet) && helmet.containsEnchantment(enchantment)) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepareCraft(PrepareItemCraftEvent event) {
        if (Arrays.stream(event.getInventory().getMatrix()).anyMatch(RepellantItems::isRepellant)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        if (Arrays.stream(event.getInventory().getMatrix()).anyMatch(RepellantItems::isRepellant)) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage("Phantom repellant cannot be used as crafting lapis.");
        }
    }
}
