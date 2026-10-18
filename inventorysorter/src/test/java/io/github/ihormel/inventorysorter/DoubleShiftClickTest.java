package io.github.ihormel.inventorysorter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;

class DoubleShiftClickTest {
    private final AtomicLong time = new AtomicLong();
    private final DoubleShiftClick clicks = new DoubleShiftClick(time::get);
    private final UUID player = UUID.randomUUID();
    private final InventoryView view = mock(InventoryView.class);

    @Test
    void recognizesPairButNotThirdClick() {
        assertFalse(clicks.click(player, view, 9));
        time.set(DoubleShiftClick.WINDOW_NANOS - 1);
        assertTrue(clicks.click(player, view, 9));
        assertFalse(clicks.click(player, view, 9));
    }

    @Test
    void rejectsClicksOutsideWindow() {
        assertFalse(clicks.click(player, view, 9));
        time.set(DoubleShiftClick.WINDOW_NANOS);
        assertFalse(clicks.click(player, view, 9));
    }

    @Test
    void requiresSamePlayerViewAndSlot() {
        assertFalse(clicks.click(player, view, 9));
        assertFalse(clicks.click(UUID.randomUUID(), view, 9));
        assertFalse(clicks.click(player, view, 10));
        assertFalse(clicks.click(player, mock(InventoryView.class), 10));
    }

    @Test
    void resetsOnCloseQuitOrShutdown() {
        clicks.click(player, view, 9);
        clicks.clear(player);
        assertFalse(clicks.click(player, view, 9));
        clicks.clear();
        assertFalse(clicks.click(player, view, 9));
    }
}
