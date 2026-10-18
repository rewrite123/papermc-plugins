package io.github.ihormel.enchantcap;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.view.AnvilView;

final class AnvilCostListener implements Listener {
    static final int MAX_CHARGE = 39;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        if (!event.isCancelled() && event.getView() instanceof AnvilView anvil) {
            // Set this before vanilla calculates a result, otherwise expensive results are discarded.
            anvil.setMaximumRepairCost(Integer.MAX_VALUE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareAnvilEvent event) {
        AnvilView anvil = event.getView();
        anvil.setMaximumRepairCost(Integer.MAX_VALUE);
        if (event.getResult() != null && !event.getResult().isEmpty()
                && anvil.getRepairCost() > MAX_CHARGE) {
            anvil.setRepairCost(MAX_CHARGE);
        }
    }
}
