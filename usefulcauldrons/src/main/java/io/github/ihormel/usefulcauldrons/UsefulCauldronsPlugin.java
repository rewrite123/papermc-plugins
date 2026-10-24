package io.github.ihormel.usefulcauldrons;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class UsefulCauldronsPlugin extends JavaPlugin implements Listener {
    static final NamespacedKey OUTPUT = new NamespacedKey("usefulcauldrons", "output");
    static final NamespacedKey TRAIL = new NamespacedKey("usefulcauldrons", "trail");
    static final NamespacedKey TRAIL_UNTIL = new NamespacedKey("usefulcauldrons", "trail_until");
    private final Map<UUID, Item> items = new HashMap<>();
    private final Map<UUID, ContentsStore.Position> occupants = new HashMap<>();
    private final Map<UUID, Vector> lastTrail = new HashMap<>();
    private final Set<ContentsStore.Position> draining = new HashSet<>();
    private Map<ContentsStore.Position, CauldronContents> contents;
    private ContentsStore store;
    private MealsListener meals;
    private CauldronDisplays displays;
    private boolean dirty;
    private int ticks;

    @Override
    public void onEnable() {
        store = new ContentsStore(getDataFolder().toPath().resolve("contents.yml"));
        try {
            contents = store.load();
        } catch (IOException exception) {
            throw new IllegalStateException("UsefulCauldrons cannot start safely. Fix contents.yml before restarting.", exception);
        }
        meals = new MealsListener(this);
        displays = new CauldronDisplays();
        displays.removeLoadedRemnants();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(meals, this);
        getServer().getPluginManager().registerEvents(displays, this);
        for (var world : getServer().getWorlds()) {
            for (var item : world.getEntitiesByClass(Item.class)) track(item);
        }
        displays.sync(contents, 0);
        getServer().getScheduler().runTaskTimer(this, this::tick, 10, 10);
        HelpGuide.register(this, "usefulcauldrons");
    }

    @Override
    public void onDisable() {
        try {
            if (contents != null) save();
        } finally {
            if (displays != null) displays.clear();
            items.clear();
            occupants.clear();
            lastTrail.clear();
        }
    }

    private void save() {
        try {
            store.save(contents);
            dirty = false;
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "Could not save cauldron contents. Stop the server and resolve the storage error.", exception);
            throw new IllegalStateException("Cauldron contents could not be persisted", exception);
        }
    }

    static boolean cauldron(Block block) {
        return block.getType() == Material.CAULDRON || block.getType() == Material.WATER_CAULDRON;
    }

    static boolean heated(Block block) {
        var below = block.getRelative(BlockFace.DOWN);
        return switch (below.getType()) {
            case FIRE, SOUL_FIRE, LAVA -> true;
            case CAMPFIRE, SOUL_CAMPFIRE -> below.getBlockData() instanceof Lightable light && light.isLit();
            default -> false;
        };
    }

    static boolean sealed(Block block) {
        return block.getRelative(BlockFace.UP).getType().isOccluding();
    }

    static Block containing(org.bukkit.Location location) {
        var block = location.getBlock();
        if (!cauldron(block)) {
            block = block.getRelative(BlockFace.DOWN);
            if (!cauldron(block)) return null;
        }
        double x = location.getX() - block.getX();
        double y = location.getY() - block.getY();
        double z = location.getZ() - block.getZ();
        return x > 0.125 && x < 0.875 && z > 0.125 && z < 0.875 && y >= 0.2 && y < 1
                ? block : null;
    }

    private boolean allowed(Block block, String operation, Entity entity) {
        return new CauldronProcessEvent(block, operation, entity).callEvent();
    }

    private void track(Item item) {
        if (!item.getPersistentDataContainer().has(OUTPUT)) items.put(item.getUniqueId(), item);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void spawn(ItemSpawnEvent event) {
        track(event.getEntity());
    }

    @EventHandler
    public void load(ChunkLoadEvent event) {
        for (var entity : event.getChunk().getEntities()) {
            if (entity instanceof Item item) track(item);
        }
    }

    private void tick() {
        ticks += 10;
        for (var item : new ArrayList<>(items.values())) {
            var location = item.getLocation();
            if (!item.isValid() || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                    || item.getPersistentDataContainer().has(OUTPUT)) {
                items.remove(item.getUniqueId());
                continue;
            }
            var block = containing(location);
            if (block != null && !sealed(block)) intake(block, item);
        }
        for (var entry : new ArrayList<>(contents.entrySet())) {
            var block = entry.getKey().loadedBlock();
            if (block == null) continue;
            var state = entry.getValue();
            if (!cauldron(block)) {
                spill(block, state);
                contents.remove(entry.getKey());
                dirty = true;
                continue;
            }
            if (block.getType() != Material.WATER_CAULDRON && state.dye != null) {
                state.dye = null;
                state.uses = 0;
                dirty = true;
            }
            process(block, state);
            charcoal(block, state);
            if (state.dye != null && heated(block)) {
                dust(block.getLocation().clone().add(0.5, 1.15, 0.5), state.dye, 8, 0.15, 0.4);
            }
            if (!state.hasItems() && state.dye == null) {
                contents.remove(entry.getKey());
                dirty = true;
            }
        }
        displays.sync(contents, ticks);
        occupants();
        if (ticks % 20 == 0) {
            long now = System.currentTimeMillis();
            for (var player : Bukkit.getOnlinePlayers()) meals.tick(player, now);
            if (dirty) save();
        }
    }

    private void intake(Block block, Item entity) {
        var stack = entity.getItemStack();
        if (Foods.id(stack) != null) return;
        var position = ContentsStore.Position.of(block);
        var state = contents.get(position);
        boolean water = block.getType() == Material.WATER_CAULDRON;
        boolean ordinary = stack.isSimilar(new ItemStack(stack.getType()));
        if (water && ordinary) {
            DyeColor color = Dyeing.dye(stack.getType());
            if (color != null) {
                if (state != null && (state.hasItems() || state.dye == color)) return;
                if (!allowed(block, "dye_water", entity)) return;
                if (state == null) state = new CauldronContents();
                state.dye = color;
                state.uses = 64;
                contents.put(position, state);
                remove(entity, 1);
                dirty = true;
                return;
            }
        }
        if (water && (state == null || !state.hasItems())) {
            var transformed = Dyeing.transform(stack, state == null ? null : state.dye);
            if (transformed != null) {
                int amount = state != null && state.dye != null ? Math.min(stack.getAmount(), state.uses) : stack.getAmount();
                if (!allowed(block, "dye_item", entity)) return;
                if (state == null || state.dye == null) {
                    if (!drain(block)) return;
                } else state.useDye(amount);
                transformed.setAmount(amount);
                remove(entity, amount);
                eject(block, transformed);
                dirty = true;
                return;
            }
        }
        if (!ordinary || !Recipes.ingredient(stack.getType())) return;
        if (!water && !heated(block)) return;
        if (state != null && state.size() == CauldronContents.CAPACITY) return;
        if (!allowed(block, "deposit", entity)) return;
        if (state == null) {
            state = new CauldronContents();
            contents.put(position, state);
        }
        int accepted = state.deposit(stack.getType(), stack.getAmount());
        remove(entity, accepted);
        dirty = true;
    }

    private static void remove(Item item, int amount) {
        var stack = item.getItemStack();
        if (amount == stack.getAmount()) item.remove();
        else {
            stack.setAmount(stack.getAmount() - amount);
            item.setItemStack(stack);
        }
    }

    private void process(Block block, CauldronContents state) {
        var plan = Cooking.next(state, block.getType() == Material.WATER_CAULDRON, heated(block), sealed(block));
        if (plan == null || !allowed(block, plan.id(), null)) return;
        if (plan.water() > 0 && !drain(block)) return;
        plan.apply(state);
        plan.returns().forEach((material, amount) -> {
            if (material == Material.HONEYCOMB) eject(block, WaxItems.create(amount));
            else eject(block, material, amount);
        });
        if (plan.food() != null && Foods.candy(plan.food())) {
            eject(block, Foods.create(plan.food()));
            state.food = null;
            state.servings = 0;
        }
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.5f, 1);
        dirty = true;
    }

    private void charcoal(Block block, CauldronContents state) {
        boolean working = block.getType() == Material.CAULDRON && heated(block) && sealed(block)
                && state.servings == 0 && Charcoal.batch(state).output() > 0;
        if (!working) {
            if (state.charcoalTicks > 0) {
                state.charcoalTicks = 0;
                dirty = true;
            }
            return;
        }
        state.charcoalTicks += 10;
        dirty = true;
        block.getWorld().spawnParticle(Particle.LARGE_SMOKE, block.getLocation().clone().add(0.5, 2.1, 0.5),
                5, 0.2, 0.1, 0.2, 0.02);
        if (state.charcoalTicks < 1200 || !allowed(block, "charcoal", null)) {
            state.charcoalTicks = Math.min(state.charcoalTicks, 1200);
            return;
        }
        var batch = Charcoal.batch(state);
        state.consume(batch.costs());
        state.charcoalTicks = 0;
        eject(block, Material.CHARCOAL, batch.output());
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1);
        block.getWorld().spawnParticle(Particle.CLOUD, block.getLocation().clone().add(0.5, 2.1, 0.5),
                30, 0.3, 0.3, 0.3, 0.02);
    }

    private boolean drain(Block block) {
        var next = block.getState(true);
        var level = (Levelled) next.getBlockData();
        if (level.getLevel() == 1) next.setType(Material.CAULDRON);
        else {
            level.setLevel(level.getLevel() - 1);
            next.setBlockData(level);
        }
        var event = new CauldronLevelChangeEvent(block, null, CauldronLevelChangeEvent.ChangeReason.UNKNOWN, next);
        var position = ContentsStore.Position.of(block);
        draining.add(position);
        try {
            if (!event.callEvent()) return false;
            if (!event.getNewState().update(true, false)) {
                throw new IllegalStateException("Could not update cauldron water at " + position);
            }
            return true;
        } finally {
            draining.remove(position);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        if (block == null || !cauldron(block) || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND) return;
        var state = contents.get(ContentsStore.Position.of(block));
        var player = event.getPlayer();
        var hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir() && player.isSneaking() && state != null && state.hasItems()) {
            if (!allowed(block, "retrieve", player)) return;
            event.setCancelled(true);
            if (state.servings > 0 && !Foods.prepaid(state.food)
                    && !player.getInventory().containsAtLeast(new ItemStack(Material.BOWL), state.servings)
                    && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                player.sendMessage(Component.text("Keep " + state.servings
                        + " empty bowls in your inventory to retrieve all cooked servings.", NamedTextColor.YELLOW));
                return;
            }
            state.ingredients.forEach((material, amount) -> give(player, new ItemStack(material, amount)));
            state.ingredients.clear();
            if (state.servings > 0) {
                if (!Foods.prepaid(state.food) && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    player.getInventory().removeItem(new ItemStack(Material.BOWL, state.servings));
                }
                for (int i = 0; i < state.servings; i++) {
                    give(player, Foods.create(state.food));
                }
                state.servings = 0;
                state.food = null;
            }
            state.charcoalTicks = 0;
            displays.update(block, state, ticks);
            dirty = true;
            return;
        }
        if (state != null && state.servings > 0
                && ((Foods.prepaid(state.food) && hand.getType().isAir())
                    || (!Foods.prepaid(state.food) && hand.getType() == Material.BOWL))) {
            if (!allowed(block, "collect", player)) return;
            event.setCancelled(true);
            if (!Foods.prepaid(state.food)) takeHand(player);
            give(player, Foods.create(state.food));
            if (--state.servings == 0) state.food = null;
            displays.update(block, state, ticks);
            dirty = true;
            return;
        }
        if (block.getType() == Material.WATER_CAULDRON && !hand.getType().isAir()
                && (state == null || !state.hasItems()) && Foods.id(hand) == null) {
            var transformed = Dyeing.transform(hand, state == null ? null : state.dye);
            if (transformed != null) {
                if (!allowed(block, "dye_item", player)) return;
                event.setCancelled(true);
                if (state == null || state.dye == null) {
                    if (!drain(block)) return;
                } else state.useDye(1);
                transformed.setAmount(1);
                takeHand(player);
                give(player, transformed);
                dirty = true;
                return;
            }
        }
        if (state != null && (state.hasItems() || state.dye != null)) {
            if (hand.getType() == Material.WATER_BUCKET) return;
            if (hand.getType() == Material.BUCKET && !state.hasItems()) return;
            event.setCancelled(true);
            player.sendActionBar(Component.text("Sneak-right-click with an empty hand to retrieve ingredients. "
                    + state.servings + " servings; " + state.uses + " dye uses.", NamedTextColor.GOLD));
        }
    }

    private static void takeHand(Player player) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        var hand = player.getInventory().getItemInMainHand();
        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().setItemInMainHand(hand);
    }

    void give(Player player, ItemStack stack) {
        int left = stack.getAmount();
        while (left > 0) {
            var part = stack.clone();
            part.setAmount(Math.min(left, part.getMaxStackSize()));
            player.getInventory().addItem(part).values().forEach(item -> {
                var entity = player.getWorld().dropItem(player.getLocation(), item);
                entity.getPersistentDataContainer().set(OUTPUT, PersistentDataType.BYTE, (byte) 1);
            });
            left -= part.getAmount();
        }
    }

    private void eject(Block block, Material material, int amount) {
        int max = material.getMaxStackSize();
        while (amount > 0) {
            int size = Math.min(max, amount);
            eject(block, new ItemStack(material, size));
            amount -= size;
        }
    }

    private void eject(Block block, ItemStack stack) {
        var item = block.getWorld().dropItem(block.getLocation().clone().add(0.5, sealed(block) ? 2.2 : 1.2, 0.5), stack);
        item.getPersistentDataContainer().set(OUTPUT, PersistentDataType.BYTE, (byte) 1);
        item.setVelocity(new Vector(0.12, 0.2, 0));
        items.remove(item.getUniqueId());
    }

    private void spill(Block block, CauldronContents state) {
        state.ingredients.forEach((material, amount) -> eject(block, material, amount));
        if (state.food != null) {
            for (int i = 0; i < state.servings; i++) {
                eject(block, Foods.create(state.food));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fluid(CauldronLevelChangeEvent event) {
        var position = ContentsStore.Position.of(event.getBlock());
        var state = contents.get(position);
        if (state == null || draining.contains(position)) return;
        if (event.getNewState().getType() == Material.WATER_CAULDRON
                && (event.getReason() == CauldronLevelChangeEvent.ChangeReason.BUCKET_EMPTY
                    || event.getReason() == CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL
                    || event.getReason() == CauldronLevelChangeEvent.ChangeReason.BOTTLE_EMPTY)) return;
        if (!state.hasItems() && (state.dye == null
                || event.getReason() == CauldronLevelChangeEvent.ChangeReason.BUCKET_FILL
                || event.getReason() == CauldronLevelChangeEvent.ChangeReason.BUCKET_EMPTY)) return;
        event.setCancelled(true);
        if (event.getEntity() instanceof Player player) {
            player.sendActionBar(Component.text("Retrieve the cauldron's contents first.", NamedTextColor.YELLOW));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) {
        var state = contents.get(ContentsStore.Position.of(event.getBlock()));
        if (state != null && state.hasItems()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Empty the cauldron before breaking it.", NamedTextColor.YELLOW));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explode(EntityExplodeEvent event) {
        protect(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explode(BlockExplodeEvent event) {
        protect(event.blockList());
    }

    private void protect(java.util.List<Block> blocks) {
        blocks.removeIf(block -> {
            var state = contents.get(ContentsStore.Position.of(block));
            return state != null && state.hasItems();
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::managed)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void piston(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::managed)) event.setCancelled(true);
    }

    private boolean managed(Block block) {
        return contents.containsKey(ContentsStore.Position.of(block));
    }

    private void occupants() {
        long now = System.currentTimeMillis();
        Set<UUID> present = new HashSet<>();
        for (var entry : contents.entrySet()) {
            var block = entry.getKey().loadedBlock();
            var state = entry.getValue();
            if (block == null || !cauldron(block)) continue;
            for (var entity : block.getWorld().getNearbyEntities(block.getLocation().clone().add(0.5, 0.5, 0.5), 0.5, 0.6, 0.5)) {
                if (!(entity instanceof Player) && !(entity instanceof Sheep)) continue;
                if (!block.equals(containing(entity.getLocation()))) continue;
                present.add(entity.getUniqueId());
                if (entry.getKey().equals(occupants.put(entity.getUniqueId(), entry.getKey()))) continue;
                if (entity instanceof Player player && state.count(Material.SUGAR) > 0 && heated(block)
                        && allowed(block, "sugar_burn", player)) MealsListener.burn(player, now);
                if (state.dye == null || state.uses < 1 || !allowed(block, "dye_entity", entity)) continue;
                if (entity instanceof Sheep sheep) {
                    if (sheep.getColor() == state.dye) continue;
                    sheep.setColor(state.dye);
                } else if (entity instanceof Player player) {
                    player.getPersistentDataContainer().set(TRAIL, PersistentDataType.STRING, state.dye.name());
                    player.getPersistentDataContainer().set(TRAIL_UNTIL, PersistentDataType.LONG, now + 60_000);
                }
                state.useDye(1);
                dirty = true;
            }
        }
        occupants.keySet().retainAll(present);
        Set<UUID> online = new HashSet<>();
        for (var player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
            var pdc = player.getPersistentDataContainer();
            if (pdc.getOrDefault(TRAIL_UNTIL, PersistentDataType.LONG, 0L) <= now) {
                pdc.remove(TRAIL);
                pdc.remove(TRAIL_UNTIL);
                lastTrail.remove(player.getUniqueId());
                continue;
            }
            var previous = lastTrail.put(player.getUniqueId(), player.getLocation().toVector());
            if (previous != null && previous.distanceSquared(player.getLocation().toVector()) < 0.01) continue;
            var color = pdc.get(TRAIL, PersistentDataType.STRING);
            if (color != null) dust(player.getLocation().add(0, 0.2, 0), DyeColor.valueOf(color), 8, 0.2, 0.25);
        }
        lastTrail.keySet().retainAll(online);
    }

    private static void dust(org.bukkit.Location location, DyeColor color, int count, double width, double height) {
        location.getWorld().spawnParticle(Particle.DUST, location, count, width, height, width,
                new Particle.DustOptions(color.getColor(), 1.2f));
    }
}
