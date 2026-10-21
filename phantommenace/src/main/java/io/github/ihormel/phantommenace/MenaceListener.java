package io.github.ihormel.phantommenace;

import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import java.util.Arrays;
import org.bukkit.GameMode;
import org.bukkit.Material;
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
import org.bukkit.plugin.java.JavaPlugin;

final class MenaceListener implements Listener {
    private final Enchantment enchantment;
    private final JavaPlugin plugin;

    MenaceListener(Enchantment enchantment, JavaPlugin plugin) {
        this.enchantment = enchantment;
        this.plugin = plugin;
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void returnBottle(EnchantItemEvent event) {
        if (!(event.getInventory() instanceof EnchantingInventory table)
                || !RepellantItems.isRepellant(table.getSecondary())
                || event.whichButton() != 0 || !eligible(event.getEnchanter(), event.getItem())
                || !event.getEnchantsToAdd().containsKey(enchantment)
                || event.getEnchanter().getGameMode() == GameMode.CREATIVE) return;
        var item = event.getItem();
        var location = event.getEnchantBlock().getLocation().add(0.5, 1, 0.5);
        // The event fires before vanilla commits the enchantment and consumes the fuel.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!event.isCancelled() && item.containsEnchantment(enchantment)) {
                location.getWorld().dropItemNaturally(location, new ItemStack(Material.GLASS_BOTTLE));
            }
        });
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
