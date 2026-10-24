package io.github.ihormel.enchantcap;

import java.util.function.IntSupplier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.view.AnvilView;

final class AnvilCostListener implements Listener {
    static final int MAX_CHARGE = 39;
    private final IntSupplier additionalLevels;

    AnvilCostListener() {
        this(0);
    }

    AnvilCostListener(int additionalLevels) {
        if (additionalLevels < 0 || additionalLevels > 254) {
            throw new IllegalArgumentException("Additional enchantment levels must be between 0 and 254");
        }
        this.additionalLevels = () -> additionalLevels;
    }

    AnvilCostListener(IntSupplier additionalLevels) {
        this.additionalLevels = additionalLevels;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        if (!event.isCancelled() && event.getView() instanceof AnvilView anvil) {
            // Set this before vanilla calculates a result, otherwise expensive results are discarded.
            anvil.setMaximumRepairCost(Integer.MAX_VALUE);
            configureLevels(anvil);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareAnvilEvent event) {
        AnvilView anvil = event.getView();
        anvil.setMaximumRepairCost(Integer.MAX_VALUE);
        configureLevels(anvil);
        var result = event.getResult();
        if (result == null || result.isEmpty() || anvil.getRepairCost() <= 0) return;
        var inventory = event.getInventory();
        var first = inventory.getFirstItem();
        var second = inventory.getSecondItem();
        int cost = MaterialRepairCost.charge(Math.min(anvil.getRepairCost(), MAX_CHARGE),
                first, second, result, anvil.getRepairItemCountCost());
        if (cost != anvil.getRepairCost()) anvil.setRepairCost(cost);
        var adjusted = EnchantmentLimits.restrict(result, first, additionalLevels.getAsInt());
        if (adjusted != result) event.setResult(adjusted);
    }

    private void configureLevels(AnvilView anvil) {
        boolean bypass = additionalLevels.getAsInt() > 0;
        if (bypass || anvil.bypassesEnchantmentLevelRestriction()) {
            anvil.bypassEnchantmentLevelRestriction(bypass);
        }
    }
}
