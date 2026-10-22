package io.github.ihormel.usefulcauldrons;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Fired before automatic intake, dyeing, cooking or charcoal conversion.
 * Cancellation leaves the items and cauldron contents unchanged.
 */
public final class CauldronProcessEvent extends BlockEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String operation;
    private final Entity entity;
    private boolean cancelled;

    public CauldronProcessEvent(Block block, String operation, @Nullable Entity entity) {
        super(block);
        this.operation = operation;
        this.entity = entity;
    }

    public String getOperation() {
        return operation;
    }

    public @Nullable Entity getEntity() {
        return entity;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
