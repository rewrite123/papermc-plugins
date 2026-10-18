package io.github.ihormel.inventorysorter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.bukkit.inventory.InventoryView;

final class DoubleShiftClick {
    static final long WINDOW_NANOS = 500_000_000L;
    private record Click(InventoryView view, int rawSlot, long time) {}
    private final Map<UUID, Click> clicks = new HashMap<>();
    private final LongSupplier clock;

    DoubleShiftClick(LongSupplier clock) {
        this.clock = clock;
    }

    boolean click(UUID player, InventoryView view, int rawSlot) {
        long now = clock.getAsLong();
        Click previous = clicks.put(player, new Click(view, rawSlot, now));
        if (previous != null && previous.view() == view && previous.rawSlot() == rawSlot
                && now - previous.time() >= 0 && now - previous.time() < WINDOW_NANOS) {
            clicks.remove(player);
            return true;
        }
        return false;
    }

    void clear(UUID player) {
        clicks.remove(player);
    }

    void clear() {
        clicks.clear();
    }
}
