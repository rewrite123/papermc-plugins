package io.github.ihormel.endercraftbuildings;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Allay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

final class BuildingListener implements Listener {
    static final int NAME_TIMEOUT_TICKS = 60 * 20;
    static final int MAX_NAME_LENGTH = 32;

    record ScanSession(Block first) {}

    record PendingName(String token, Blueprint blueprint, long expires) {}

    static final class PlaceSession {
        final Block clicked;
        final Block anchor;
        final Facing facing;
        final Blueprint blueprint;
        final Location start;
        final long created;
        boolean using;

        PlaceSession(Block clicked, Block anchor, Facing facing, Blueprint blueprint, Location start, long created) {
            this.clicked = clicked;
            this.anchor = anchor;
            this.facing = facing;
            this.blueprint = blueprint;
            this.start = start;
            this.created = created;
        }
    }

    private record Flash(Block[] corners, long expires) {}

    private final Server server;
    private final CrystalItems items;
    private final Buildings buildings;
    private final TaperRecords tapers;
    private final DoubleSupplier random;
    private final Consumer<Runnable> sync;
    private final DoubleSupplier taperChance;
    private final DoubleSupplier placementChance;
    private final Map<UUID, ScanSession> scans = new HashMap<>();
    private final Map<UUID, PendingName> names = new ConcurrentHashMap<>();
    private final Map<UUID, PlaceSession> placements = new HashMap<>();
    private final Map<UUID, Flash> flashes = new HashMap<>();
    private final Map<UUID, Long> deniedAt = new HashMap<>();
    private final Map<UUID, Long> handledAt = new HashMap<>();
    private long tick;

    BuildingListener(Server server, CrystalItems items, Buildings buildings, TaperRecords tapers, DoubleSupplier random,
            Consumer<Runnable> sync, DoubleSupplier taperChance, DoubleSupplier placementChance) {
        this.server = server;
        this.items = items;
        this.buildings = buildings;
        this.tapers = tapers;
        this.random = random;
        this.sync = sync;
        this.taperChance = taperChance;
        this.placementChance = placementChance;
    }

    ScanSession scan(Player player) { return scans.get(player.getUniqueId()); }

    PendingName pendingName(Player player) { return names.get(player.getUniqueId()); }

    PlaceSession placement(Player player) { return placements.get(player.getUniqueId()); }

    @EventHandler(priority = EventPriority.HIGH)
    public void interact(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        String kind = items.kind(item);
        Block clicked = event.getClickedBlock();
        if (clicked != null && tapers.contains(clicked)) {
            boolean crystal = CrystalItems.MEMORY.equals(kind) || CrystalItems.STORAGE.equals(kind);
            if (!crystal && player.isSneaking() && item != null && !item.getType().isAir()) return;
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            if ((crystal || event.getHand() == EquipmentSlot.HAND) && firstThisTick(player)) {
                imprint(player, item, clicked);
            }
            return;
        }
        if (CrystalItems.MEMORY.equals(kind)) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            if (clicked != null && firstThisTick(player)) scanClick(player, event.getHand(), clicked);
        } else if (CrystalItems.STORAGE.equals(kind)) {
            event.setUseInteractedBlock(Event.Result.DENY);
            if (clicked != null) {
                placeClick(event, player, item, clicked, event.getBlockFace());
            } else {
                PlaceSession session = placements.get(player.getUniqueId());
                boolean ready = session != null && tick - session.created <= 1;
                event.setUseItemInHand(ready ? Event.Result.ALLOW : Event.Result.DENY);
                Long denied = deniedAt.get(player.getUniqueId());
                if (!ready && (denied == null || tick - denied > 1)) {
                    actionBar(player, "Aim at a nearby block to recall the building.", NamedTextColor.RED);
                }
            }
        }
    }

    private boolean firstThisTick(Player player) {
        Long last = handledAt.put(player.getUniqueId(), tick);
        return last == null || last != tick;
    }

    void scanClick(Player player, EquipmentSlot hand, Block clicked) {
        UUID id = player.getUniqueId();
        if (names.containsKey(id)) {
            message(player, "Type a name for your scanned building in chat first, or type cancel.", NamedTextColor.YELLOW);
            return;
        }
        ScanSession session = scans.get(id);
        if (session == null) {
            scans.put(id, new ScanSession(clicked));
            message(player, "First corner set. Right-click the opposite corner (max 32 x 32 x 32). "
                    + "Sneak + right-click to cancel.", NamedTextColor.LIGHT_PURPLE);
            return;
        }
        if (player.isSneaking()) {
            scans.remove(id);
            message(player, "Scan cancelled.", NamedTextColor.GRAY);
            return;
        }
        if (!session.first().getWorld().equals(clicked.getWorld()) || !Buildings.withinLimit(session.first(), clicked)) {
            message(player, "That selection is too large. Each side can be at most 32 blocks.", NamedTextColor.RED);
            return;
        }
        Buildings.Scan scan = buildings.scan(session.first(), clicked, Facing.fromYaw(player.getLocation().getYaw()));
        scans.remove(id);
        if (scan.blueprint().blockCount() == 0) {
            message(player, "There are no copyable blocks in that selection.", NamedTextColor.RED);
            return;
        }
        String token = UUID.randomUUID().toString();
        items.setToken(player.getInventory().getItem(hand), token);
        names.put(id, new PendingName(token, scan.blueprint(), tick + NAME_TIMEOUT_TICKS));
        String skipped = scan.skipped() == 0 ? "" : " Skipped " + scan.skipped() + " unobtainable block"
                + (scan.skipped() == 1 ? "" : "s") + " (fluids, bedrock, portals, spawners, etc.).";
        message(player, "Scanned " + scan.blueprint().blockCount() + " blocks." + skipped, NamedTextColor.AQUA);
        message(player, "Type a name for this building in chat (max 32 characters), or type cancel.",
                NamedTextColor.LIGHT_PURPLE);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1f);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void chat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!names.containsKey(player.getUniqueId())) return;
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
        sync.accept(() -> name(player, text));
    }

    void name(Player player, String raw) {
        UUID id = player.getUniqueId();
        PendingName pending = names.remove(id);
        if (pending == null) return;
        String text = raw.strip();
        if (text.equalsIgnoreCase("cancel")) {
            message(player, "Scan discarded.", NamedTextColor.GRAY);
            return;
        }
        if (text.isEmpty() || text.length() > MAX_NAME_LENGTH || text.chars().anyMatch(Character::isISOControl)) {
            names.put(id, pending);
            message(player, "Building names must be 1-32 characters. Try again, or type cancel.", NamedTextColor.RED);
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (ItemStack stack : inventory.getContents()) {
            if (items.hasToken(stack, pending.token())) {
                items.imprintMemory(stack, pending.blueprint(), text);
                message(player, "\"" + text + "\" is stored in your memory storage crystal.", NamedTextColor.AQUA);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
                return;
            }
        }
        message(player, "Your memory storage crystal is no longer in your inventory; the scan was lost.",
                NamedTextColor.RED);
    }

    void imprint(Player player, ItemStack source, Block taper) {
        String kind = items.kind(source);
        if (!CrystalItems.MEMORY.equals(kind) && !CrystalItems.STORAGE.equals(kind)) {
            message(player, "Use a memory storage crystal or a storage crystal on the taper.", NamedTextColor.GRAY);
            return;
        }
        Blueprint blueprint = items.blueprint(source);
        if (blueprint == null) {
            message(player, "That crystal does not hold a building yet.", NamedTextColor.RED);
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int emptySlot = -1;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (CrystalItems.EMPTY.equals(items.kind(inventory.getItem(slot)))) {
                emptySlot = slot;
                break;
            }
        }
        if (emptySlot < 0) {
            message(player, "You need an empty storage crystal in your inventory.", NamedTextColor.RED);
            return;
        }
        boolean free = player.getGameMode() == GameMode.CREATIVE;
        Map<Material, Integer> cost = buildings.cost(blueprint);
        if (!free) {
            var missing = new LinkedHashMap<Material, Integer>();
            cost.forEach((material, amount) -> {
                int have = available(inventory, material);
                if (have < amount) missing.put(material, amount - have);
            });
            if (!missing.isEmpty()) {
                message(player, "Missing materials for \"" + items.buildingName(source) + "\": " + describe(missing),
                        NamedTextColor.RED);
                return;
            }
            cost.forEach((material, amount) -> inventory.removeItem(new ItemStack(material, amount)));
        }
        ItemStack empty = inventory.getItem(emptySlot);
        empty.setAmount(empty.getAmount() - 1);
        inventory.setItem(emptySlot, empty.getAmount() <= 0 ? null : empty);
        ItemStack result = items.copyFrom(source);
        inventory.addItem(result).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        Location center = taper.getLocation().add(0.5, 1.0, 0.5);
        taper.getWorld().spawnParticle(Particle.PORTAL, center, 60, 0.3, 0.4, 0.3, 0.6);
        taper.getWorld().playSound(center, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.8f);
        message(player, "The taper splits the light: a storage crystal for \"" + items.buildingName(source)
                + "\" is ready.", NamedTextColor.AQUA);
        if (random.getAsDouble() < taperChance.getAsDouble()) taper.getWorld().spawnEntity(center, EntityType.ENDERMITE);
    }

    static int available(PlayerInventory inventory, Material material) {
        var plain = new ItemStack(material);
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) if (stack != null && stack.isSimilar(plain)) total += stack.getAmount();
        return total;
    }

    static String describe(Map<Material, Integer> missing) {
        var parts = new java.util.ArrayList<String>();
        for (var entry : missing.entrySet()) {
            if (parts.size() == 8) {
                parts.add("and " + (missing.size() - 8) + " more");
                break;
            }
            parts.add(entry.getValue() + " " + entry.getKey().getKey().getKey().replace('_', ' '));
        }
        return String.join(", ", parts);
    }

    private void placeClick(PlayerInteractEvent event, Player player, ItemStack item, Block clicked, BlockFace face) {
        UUID id = player.getUniqueId();
        Blueprint blueprint = items.blueprint(item);
        if (blueprint == null) {
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }
        Facing facing = Facing.fromYaw(player.getLocation().getYaw());
        Block anchor = Materials.free(clicked) || face == null ? clicked : clicked.getRelative(face);
        if (!Buildings.inWorld(blueprint, anchor)) {
            event.setUseItemInHand(Event.Result.DENY);
            placements.remove(id);
            deniedAt.put(id, tick);
            actionBar(player, "The building would not fit inside the world's height limits.", NamedTextColor.RED);
            return;
        }
        List<Buildings.Target> targets = buildings.plan(blueprint, anchor, facing);
        int blocked = Buildings.obstructions(targets);
        if (blocked > 0) {
            event.setUseItemInHand(Event.Result.DENY);
            placements.remove(id);
            deniedAt.put(id, tick);
            flashes.put(id, new Flash(Buildings.corners(blueprint, anchor, facing), tick + 40));
            actionBar(player, blocked + " block" + (blocked == 1 ? " is" : "s are") + " in the way.", NamedTextColor.RED);
            return;
        }
        event.setUseItemInHand(Event.Result.ALLOW);
        placements.put(id, new PlaceSession(clicked, anchor, facing, blueprint, player.getLocation().clone(), tick));
        actionBar(player, String.format("Hold right-click to recall %s (%.1fs)", items.buildingName(item),
                CrystalItems.holdSeconds(blueprint.blockCount())), NamedTextColor.LIGHT_PURPLE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void consume(PlayerItemConsumeEvent event) {
        if (!CrystalItems.STORAGE.equals(items.kind(event.getItem()))) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        PlaceSession session = placements.remove(player.getUniqueId());
        if (session == null) return;
        complete(player, session, event.getHand());
    }

    boolean complete(Player player, PlaceSession session, EquipmentSlot hand) {
        if (!Buildings.inWorld(session.blueprint, session.anchor)) return false;
        List<Buildings.Target> targets = buildings.plan(session.blueprint, session.anchor, session.facing);
        int blocked = Buildings.obstructions(targets);
        if (blocked > 0) {
            actionBar(player, blocked + " block" + (blocked == 1 ? " is" : "s are") + " in the way.", NamedTextColor.RED);
            return false;
        }
        ItemStack crystal = player.getInventory().getItem(hand);
        for (Buildings.Target target : targets) {
            var check = new BlockPlaceEvent(target.block(), target.block().getState(), session.clicked, crystal, player,
                    true, hand);
            server.getPluginManager().callEvent(check);
            if (check.isCancelled() || !check.canBuild()) {
                actionBar(player, "You are not allowed to build there.", NamedTextColor.RED);
                return false;
            }
        }
        for (Buildings.Target target : targets) target.block().setBlockData(target.data(), false);
        crystal.setAmount(crystal.getAmount() - 1);
        player.getInventory().setItem(hand, crystal.getAmount() <= 0 ? null : crystal);
        Block[] corners = Buildings.corners(session.blueprint, session.anchor, session.facing);
        Location center = corners[0].getLocation().add(corners[1].getLocation()).multiply(0.5).add(0.5, 0.5, 0.5);
        var world = session.anchor.getWorld();
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 200, session.blueprint.width() / 3.0,
                session.blueprint.height() / 3.0, session.blueprint.length() / 3.0, 0.05);
        world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1f);
        actionBar(player, "The building returns from the End. Your storage crystal shatters.", NamedTextColor.AQUA);
        if (random.getAsDouble() < placementChance.getAsDouble()) {
            world.spawnEntity(session.anchor.getLocation().add(0.5, 0, 0.5), EntityType.ENDERMITE);
        }
        return true;
    }

    void tick() {
        tick++;
        Iterator<Map.Entry<UUID, PendingName>> pending = names.entrySet().iterator();
        while (pending.hasNext()) {
            var entry = pending.next();
            if (entry.getValue().expires() > tick) continue;
            pending.remove();
            Player player = server.getPlayer(entry.getKey());
            if (player != null) message(player, "Naming timed out; the scan was discarded.", NamedTextColor.GRAY);
        }
        var scanIterator = scans.entrySet().iterator();
        while (scanIterator.hasNext()) {
            var entry = scanIterator.next();
            Player player = server.getPlayer(entry.getKey());
            if (player == null) {
                scanIterator.remove();
                continue;
            }
            if (!holding(player, CrystalItems.MEMORY)) {
                scanIterator.remove();
                message(player, "Scan cancelled.", NamedTextColor.GRAY);
                continue;
            }
            if (tick % 4 != 0) continue;
            Block first = entry.getValue().first();
            Block hovered = player.getTargetBlockExact(64);
            if (hovered == null || !hovered.getWorld().equals(first.getWorld())) hovered = first;
            boolean fits = Buildings.withinLimit(first, hovered);
            outline(player, first, hovered, fits ? Color.fromRGB(170, 90, 255) : Color.RED);
        }
        var placeIterator = placements.entrySet().iterator();
        while (placeIterator.hasNext()) {
            var entry = placeIterator.next();
            Player player = server.getPlayer(entry.getKey());
            PlaceSession session = entry.getValue();
            if (player == null) {
                placeIterator.remove();
                continue;
            }
            boolean raised = player.isHandRaised() && CrystalItems.STORAGE.equals(items.kind(player.getActiveItem()));
            if (raised) session.using = true;
            boolean moved = !player.getWorld().equals(session.start.getWorld())
                    || player.getLocation().distanceSquared(session.start) > 0.25;
            boolean stopped = !raised && (session.using || tick - session.created > 5);
            boolean lookedAway = false;
            if (raised && !moved) {
                Block target = player.getTargetBlockExact(6);
                lookedAway = target == null || !target.equals(session.clicked);
            }
            if (stopped || moved || lookedAway) {
                placeIterator.remove();
                if (raised) player.clearActiveItem();
                if (session.using) actionBar(player, "Recall cancelled.", NamedTextColor.GRAY);
                continue;
            }
            if (tick % 4 == 0) {
                Block[] corners = Buildings.corners(session.blueprint, session.anchor, session.facing);
                outline(player, corners[0], corners[1], Color.fromRGB(80, 255, 200));
            }
        }
        var flashIterator = flashes.entrySet().iterator();
        while (flashIterator.hasNext()) {
            var entry = flashIterator.next();
            Player player = server.getPlayer(entry.getKey());
            if (player == null || entry.getValue().expires() <= tick) {
                flashIterator.remove();
                continue;
            }
            if (tick % 4 == 0) outline(player, entry.getValue().corners()[0], entry.getValue().corners()[1], Color.RED);
        }
    }

    private boolean holding(Player player, String kind) {
        return kind.equals(items.kind(player.getInventory().getItemInMainHand()))
                || kind.equals(items.kind(player.getInventory().getItemInOffHand()));
    }

    static void outline(Player player, Block a, Block b, Color color) {
        double minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX()) + 1;
        double minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY()) + 1;
        double minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ()) + 1;
        double longest = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        double step = Math.max(0.5, longest / 32);
        var dust = new Particle.DustOptions(color, 0.9f);
        var world = a.getWorld();
        double[] xs = {minX, maxX}, ys = {minY, maxY}, zs = {minZ, maxZ};
        for (double y : ys) for (double z : zs) line(player, world, minX, y, z, maxX, y, z, step, dust);
        for (double x : xs) for (double z : zs) line(player, world, x, minY, z, x, maxY, z, step, dust);
        for (double x : xs) for (double y : ys) line(player, world, x, y, minZ, x, y, maxZ, step, dust);
    }

    private static void line(Player player, org.bukkit.World world, double x1, double y1, double z1, double x2,
            double y2, double z2, double step, Particle.DustOptions dust) {
        double length = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        int points = Math.max(1, (int) Math.ceil(length / step));
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            player.spawnParticle(Particle.DUST, new Location(world, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t,
                    z1 + (z2 - z1) * t), 1, 0, 0, 0, 0, dust);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void placeTaper(BlockPlaceEvent event) {
        if (!CrystalItems.TAPER.equals(items.kind(event.getItemInHand()))) return;
        Block block = event.getBlockPlaced();
        tapers.add(block);
        sync.accept(() -> quiet(block));
    }

    /** Stops the vault from advertising trial-chamber loot and ensures a trial key unlocks nothing. */
    static void quiet(Block block) {
        if (block.getType() != Material.VAULT || !(block.getState() instanceof org.bukkit.block.Vault vault)) return;
        var empty = org.bukkit.Bukkit.getLootTable(org.bukkit.NamespacedKey.minecraft("empty"));
        if (empty != null) {
            vault.setLootTable(empty);
            vault.setDisplayedLootTable(empty);
        }
        vault.setDisplayedItem(new ItemStack(Material.AIR));
        vault.update(true, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void breakTaper(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!tapers.contains(block)) return;
        tapers.remove(block);
        event.setDropItems(false);
        event.setExpToDrop(0);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), items.taper());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void entityExplode(EntityExplodeEvent event) { event.blockList().removeIf(tapers::contains); }

    @EventHandler(ignoreCancelled = true)
    public void blockExplode(BlockExplodeEvent event) { event.blockList().removeIf(tapers::contains); }

    @EventHandler
    public void prepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (items.custom(stack)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void prepareSmithing(PrepareSmithingEvent event) {
        for (ItemStack stack : event.getInventory().getContents()) {
            if (items.custom(stack)) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void crafter(CrafterCraftEvent event) {
        if (event.getBlock().getState() instanceof InventoryHolder holder) {
            for (ItemStack stack : holder.getInventory().getContents()) {
                if (items.custom(stack)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void allay(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Allay
                && items.custom(event.getPlayer().getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        scans.remove(id);
        names.remove(id);
        placements.remove(id);
        flashes.remove(id);
        deniedAt.remove(id);
        handledAt.remove(id);
    }

    private static void message(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text(text, color));
    }

    private static void actionBar(Player player, String text, NamedTextColor color) {
        player.sendActionBar(Component.text(text, color));
    }
}
