package io.github.ihormel.usefulcauldrons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class CauldronDisplays implements Listener {
    static final NamespacedKey MARKER = new NamespacedKey("usefulcauldrons", "contents_display");
    private final Map<ContentsStore.Position, Map<String, ItemDisplay>> displays = new HashMap<>();

    private record Icon(String key, Material material, String food, int amount) {
        ItemStack stack() {
            var stack = food == null ? new ItemStack(material) : Foods.create(food);
            stack.setAmount(Math.min(amount, stack.getMaxStackSize()));
            return stack;
        }
    }

    void removeLoadedRemnants() {
        for (var world : Bukkit.getWorlds()) {
            for (var display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (display.getPersistentDataContainer().has(MARKER)) display.remove();
            }
        }
    }

    void sync(Map<ContentsStore.Position, CauldronContents> contents, int ticks) {
        for (var position : new ArrayList<>(displays.keySet())) {
            if (!contents.containsKey(position) || position.loadedBlock() == null) remove(position);
        }
        for (var entry : contents.entrySet()) {
            var block = entry.getKey().loadedBlock();
            if (block != null) update(block, entry.getValue(), ticks);
        }
    }

    void update(Block block, CauldronContents contents, int ticks) {
        var position = ContentsStore.Position.of(block);
        if (!contents.hasItems() || !UsefulCauldronsPlugin.cauldron(block)) {
            remove(position);
            return;
        }
        var icons = new ArrayList<Icon>();
        contents.ingredients.forEach((material, amount) ->
                icons.add(new Icon("ingredient:" + material.name(), material, null, amount)));
        if (contents.servings > 0) {
            icons.add(new Icon("food:" + contents.food, null, contents.food, contents.servings));
        }
        var current = displays.computeIfAbsent(position, ignored -> new HashMap<>());
        var keys = icons.stream().map(Icon::key).toList();
        current.entrySet().removeIf(entry -> {
            if (keys.contains(entry.getKey()) && entry.getValue().isValid()) return false;
            entry.getValue().remove();
            return true;
        });
        for (int index = 0; index < icons.size(); index++) {
            var icon = icons.get(index);
            var display = current.get(icon.key());
            if (display == null) {
                display = block.getWorld().spawn(block.getLocation().clone().add(0.5, 0.94, 0.5),
                        ItemDisplay.class, entity -> {
                            entity.getPersistentDataContainer().set(MARKER, PersistentDataType.BYTE, (byte) 1);
                            entity.setPersistent(false);
                            entity.setInvulnerable(true);
                            entity.setGravity(false);
                            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                            entity.setViewRange(0.5f);
                            entity.setDisplayWidth(1);
                            entity.setDisplayHeight(1);
                            entity.setShadowRadius(0);
                            entity.setInterpolationDuration(10);
                            entity.setItemStack(icon.stack());
                        });
                current.put(icon.key(), display);
            } else if (display.getItemStack().getAmount() != Math.min(icon.amount(), display.getItemStack().getMaxStackSize())) {
                display.setItemStack(icon.stack());
            }
            display.setInterpolationDelay(0);
            display.setTransformation(pose(index, icons.size(), ticks));
        }
    }

    static Transformation pose(int index, int count, int ticks) {
        double angle = Math.floorMod(ticks, 240) * Math.PI / 120 + index * Math.PI * 2 / count;
        float radius = count == 1 ? 0.12f : 0.23f;
        return new Transformation(
                new Vector3f((float) Math.cos(angle) * radius, (float) Math.sin(angle * 2) * 0.035f,
                        (float) Math.sin(angle) * radius),
                new Quaternionf().rotateY((float) -angle),
                new Vector3f(0.28f), new Quaternionf());
    }

    void remove(ContentsStore.Position position) {
        var current = displays.remove(position);
        if (current != null) current.values().forEach(ItemDisplay::remove);
    }

    void clear() {
        displays.values().forEach(current -> current.values().forEach(ItemDisplay::remove));
        displays.clear();
    }

    @EventHandler
    public void load(EntitiesLoadEvent event) {
        for (var entity : event.getEntities()) {
            if (entity instanceof ItemDisplay && entity.getPersistentDataContainer().has(MARKER)) entity.remove();
        }
    }

    @EventHandler
    public void unload(EntitiesUnloadEvent event) {
        var chunk = event.getChunk();
        for (var position : new ArrayList<>(displays.keySet())) {
            if (position.world().equals(chunk.getWorld().getUID())
                    && position.x() >> 4 == chunk.getX() && position.z() >> 4 == chunk.getZ()) {
                remove(position);
            }
        }
    }
}
