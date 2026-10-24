package io.github.ihormel.endercraftbuildings;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
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
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
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
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.RayTraceResult;

final class BuildingListener implements Listener {
    static final int NAME_TIMEOUT_TICKS = 60 * 20;
    static final int MAX_NAME_LENGTH = 32;
    static final int OUTLINE_TICKS = 30 * 20;
    /** Clients repeat right-click on a block every 4 ticks while it is held; a longer gap means it was released. */
    static final int CHARGE_GAP_TICKS = 10;
    static final double AIR_TARGET_DISTANCE = 3.0;
    private static final Color SCAN_COLOR = Color.fromRGB(170, 90, 255);
    private static final Color PLACE_COLOR = Color.fromRGB(80, 255, 200);

    /** The block a scan click selects: the hovered block, or the air block {@link #AIR_TARGET_DISTANCE} ahead. */
    record Aim(Block block, boolean air) {}

    record ScanSession(Block first) {}

    record PendingName(String token, Blueprint blueprint, long expires) {}

    /** A placed storage crystal whose outline is currently visible. */
    static final class Shown {
        final PlacedCrystals.Placed placed;
        final Blueprint blueprint;
        final Block[] corners;
        long expires;
        int blocked;

        Shown(PlacedCrystals.Placed placed, Blueprint blueprint, Block[] corners) {
            this.placed = placed;
            this.blueprint = blueprint;
            this.corners = corners;
        }
    }

    static final class Charge {
        final Block block;
        final Shown shown;
        final long start;
        final int holdTicks;
        long last;

        Charge(Block block, Shown shown, long start, int holdTicks) {
            this.block = block;
            this.shown = shown;
            this.start = start;
            this.holdTicks = holdTicks;
            this.last = start;
        }
    }

    private final Server server;
    private final CrystalItems items;
    private final Buildings buildings;
    private final TaperRecords tapers;
    private final PlacedCrystals crystals;
    private final Function<Player, Aim> aim;
    private final DoubleSupplier random;
    private final Consumer<Runnable> sync;
    private final DoubleSupplier taperChance;
    private final DoubleSupplier placementChance;
    private final Map<UUID, ScanSession> scans = new HashMap<>();
    private final Map<UUID, PendingName> names = new ConcurrentHashMap<>();
    private final Map<Block, Shown> shown = new HashMap<>();
    private final Map<UUID, Charge> charges = new HashMap<>();
    private final Map<UUID, Long> handledAt = new HashMap<>();
    private long tick;

    BuildingListener(Server server, CrystalItems items, Buildings buildings, TaperRecords tapers,
            PlacedCrystals crystals, Function<Player, Aim> aim, DoubleSupplier random, Consumer<Runnable> sync,
            DoubleSupplier taperChance, DoubleSupplier placementChance) {
        this.server = server;
        this.items = items;
        this.buildings = buildings;
        this.tapers = tapers;
        this.crystals = crystals;
        this.aim = aim;
        this.random = random;
        this.sync = sync;
        this.taperChance = taperChance;
        this.placementChance = placementChance;
    }

    ScanSession scan(Player player) { return scans.get(player.getUniqueId()); }

    PendingName pendingName(Player player) { return names.get(player.getUniqueId()); }

    Charge charge(Player player) { return charges.get(player.getUniqueId()); }

    Shown shown(Block block) { return shown.get(block); }

    /** Hovered block within the player's reach, otherwise the air block three blocks along their view. */
    static Aim aim(Player player) {
        var range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        double reach = range == null ? 4.5 : range.getValue();
        RayTraceResult hit = player.rayTraceBlocks(reach, FluidCollisionMode.NEVER);
        if (hit != null && hit.getHitBlock() != null) return new Aim(hit.getHitBlock(), false);
        Location eye = player.getEyeLocation();
        Location point = eye.clone().add(eye.getDirection().multiply(AIR_TARGET_DISTANCE));
        World world = eye.getWorld();
        int y = Math.clamp(point.getBlockY(), world.getMinHeight(), world.getMaxHeight() - 1);
        return new Aim(world.getBlockAt(point.getBlockX(), y, point.getBlockZ()), true);
    }

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
            if ((crystal || event.getHand() == EquipmentSlot.HAND) && fresh(player)) imprint(player, item, clicked);
            return;
        }
        if (clicked != null) {
            PlacedCrystals.Placed placed = crystals.get(clicked);
            if (placed != null) {
                if (!CrystalItems.STORAGE.equals(kind) && !CrystalItems.MEMORY.equals(kind) && player.isSneaking()
                        && item != null && !item.getType().isAir()) {
                    return;
                }
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setUseItemInHand(Event.Result.DENY);
                if (fresh(player)) chargeClick(player, clicked, placed);
                return;
            }
        }
        if (CrystalItems.MEMORY.equals(kind)) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            if (fresh(player)) scanClick(player, event.getHand(), clicked != null ? clicked : aim.apply(player).block());
        } else if (CrystalItems.STORAGE.equals(kind)) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            if (!fresh(player)) return;
            if (clicked != null) {
                placeCrystal(player, event.getHand(), item, clicked, event.getBlockFace());
            } else {
                actionBar(player, "Right-click a block to place the storage crystal.", NamedTextColor.GRAY);
            }
        }
    }

    /**
     * One right-click can arrive as a block packet plus an item packet, and as main- plus off-hand events.
     * Real repeat clicks are at least 4 ticks apart, so anything within 2 ticks is the same click.
     */
    private boolean fresh(Player player) {
        Long last = handledAt.get(player.getUniqueId());
        if (last != null && tick - last < 2) return false;
        handledAt.put(player.getUniqueId(), tick);
        return true;
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
            message(player, "First corner set. Right-click the opposite corner (max 32 x 32 x 32); aim at open air "
                    + "to pick the spot 3 blocks ahead. Sneak + right-click to cancel.", NamedTextColor.LIGHT_PURPLE);
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
        Location center = taper.getLocation().clone().add(0.5, 1.0, 0.5);
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

    /** Puts a storage crystal down as an amethyst cluster marking the building's front-left-bottom corner. */
    void placeCrystal(Player player, EquipmentSlot hand, ItemStack item, Block clicked, BlockFace face) {
        Blueprint blueprint = items.blueprint(item);
        if (blueprint == null) return;
        boolean replace = Materials.free(clicked);
        if (!replace && (face == null || !face.isCartesian())) return;
        Block target = replace ? clicked : clicked.getRelative(face);
        if (!Materials.free(target)) {
            actionBar(player, "There is no room for the crystal there.", NamedTextColor.RED);
            return;
        }
        if (!Buildings.inWorld(blueprint, target)) {
            actionBar(player, "The building would not fit inside the world's height limits.", NamedTextColor.RED);
            return;
        }
        var replaced = target.getState();
        var cluster = (Directional) server.createBlockData(Material.AMETHYST_CLUSTER);
        cluster.setFacing(replace ? BlockFace.UP : face);
        target.setBlockData(cluster, false);
        var check = new BlockPlaceEvent(target, replaced, clicked, item, player, true, hand);
        server.getPluginManager().callEvent(check);
        if (check.isCancelled() || !check.canBuild()) {
            replaced.update(true, false);
            return;
        }
        var placed = new PlacedCrystals.Placed(items.rawBlueprint(item), items.buildingName(item),
                items.blockCount(item), Facing.fromYaw(player.getLocation().getYaw()));
        crystals.put(target, placed);
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
            player.getInventory().setItem(hand, item.getAmount() <= 0 ? null : item);
        }
        target.getWorld().playSound(target.getLocation().clone().add(0.5, 0.5, 0.5), Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 1f, 1f);
        Shown outline = show(target, placed, blueprint);
        if (outline.blocked > 0) {
            actionBar(player, blockedText(outline.blocked) + " Clear the red box, then hold right-click on the crystal.",
                    NamedTextColor.RED);
        } else {
            actionBar(player, String.format("Hold right-click on the crystal to recall %s (%.1fs)", placed.name(),
                    CrystalItems.holdSeconds(placed.count())), NamedTextColor.LIGHT_PURPLE);
        }
    }

    /** Shows (or keeps showing) the outline of a placed crystal for another {@link #OUTLINE_TICKS}. */
    private Shown show(Block block, PlacedCrystals.Placed placed, Blueprint blueprint) {
        Shown current = shown.get(block);
        if (current == null) {
            current = new Shown(placed, blueprint, Buildings.corners(blueprint, block, placed.facing()));
            current.blocked = blocked(block, current);
            shown.put(block, current);
        }
        current.expires = tick + OUTLINE_TICKS;
        return current;
    }

    private int blocked(Block block, Shown outline) {
        return Buildings.obstructions(buildings.plan(outline.blueprint, block, outline.placed.facing()), block);
    }

    void chargeClick(Player player, Block block, PlacedCrystals.Placed placed) {
        UUID id = player.getUniqueId();
        Shown outline = shown.get(block);
        if (outline == null) {
            Blueprint blueprint;
            try {
                blueprint = Blueprint.decode(placed.blueprint());
            } catch (IllegalArgumentException exception) {
                actionBar(player, "This crystal's hologram is unreadable.", NamedTextColor.RED);
                return;
            }
            outline = show(block, placed, blueprint);
        } else {
            show(block, placed, outline.blueprint);
        }
        Charge charge = charges.get(id);
        if (charge != null && charge.block.equals(block) && tick - charge.last <= CHARGE_GAP_TICKS) {
            charge.last = tick;
            return;
        }
        outline.blocked = blocked(block, outline);
        if (outline.blocked > 0) {
            charges.remove(id);
            actionBar(player, blockedText(outline.blocked), NamedTextColor.RED);
            return;
        }
        int holdTicks = (int) Math.round(CrystalItems.holdSeconds(placed.count()) * 20);
        charges.put(id, new Charge(block, outline, tick, holdTicks));
        actionBar(player, String.format("Keep holding right-click to recall %s (%.1fs)", placed.name(),
                holdTicks / 20.0), NamedTextColor.LIGHT_PURPLE);
    }

    boolean complete(Player player, Charge charge) {
        Block block = charge.block;
        PlacedCrystals.Placed placed = crystals.get(block);
        if (placed == null) return false;
        Blueprint blueprint = charge.shown.blueprint;
        if (!Buildings.inWorld(blueprint, block)) return false;
        List<Buildings.Target> targets = buildings.plan(blueprint, block, placed.facing());
        int blocked = Buildings.obstructions(targets, block);
        if (blocked > 0) {
            charge.shown.blocked = blocked;
            actionBar(player, blockedText(blocked), NamedTextColor.RED);
            return false;
        }
        ItemStack crystal = items.storageCrystal(placed.blueprint(), placed.name(), placed.count());
        for (Buildings.Target target : targets) {
            var check = new BlockPlaceEvent(target.block(), target.block().getState(), block, crystal, player, true,
                    EquipmentSlot.HAND);
            server.getPluginManager().callEvent(check);
            if (check.isCancelled() || !check.canBuild()) {
                actionBar(player, "You are not allowed to build there.", NamedTextColor.RED);
                return false;
            }
        }
        crystals.remove(block);
        shown.remove(block);
        block.setType(Material.AIR, false);
        for (Buildings.Target target : targets) target.block().setBlockData(target.data(), false);
        Block[] corners = charge.shown.corners;
        Location center = corners[0].getLocation().clone().add(corners[1].getLocation()).multiply(0.5).add(0.5, 0.5, 0.5);
        World world = block.getWorld();
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 200, blueprint.width() / 3.0, blueprint.height() / 3.0,
                blueprint.length() / 3.0, 0.05);
        world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        world.playSound(block.getLocation().clone().add(0.5, 0.5, 0.5), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1f);
        actionBar(player, "The building returns from the End. The storage crystal shatters.", NamedTextColor.AQUA);
        if (random.getAsDouble() < placementChance.getAsDouble()) {
            world.spawnEntity(block.getLocation().clone().add(0.5, 0, 0.5), EntityType.ENDERMITE);
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void consume(PlayerItemConsumeEvent event) {
        // Storage crystals from 0.1.0 still carry a consumable component; never let them be eaten.
        if (CrystalItems.STORAGE.equals(items.kind(event.getItem()))) event.setCancelled(true);
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
        tickScans();
        tickCharges();
        tickOutlines();
    }

    private void tickScans() {
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
            Block hovered = aim.apply(player).block();
            if (!hovered.getWorld().equals(first.getWorld())) hovered = first;
            outline(player, first, hovered, Buildings.withinLimit(first, hovered) ? SCAN_COLOR : Color.RED);
        }
        if (tick % 4 != 0) return;
        for (Player player : server.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (scans.containsKey(id) || names.containsKey(id) || !holding(player, CrystalItems.MEMORY)) continue;
            Aim target = aim.apply(player);
            if (target.air()) outline(player, target.block(), target.block(), SCAN_COLOR);
        }
    }

    private void tickCharges() {
        var chargeIterator = charges.entrySet().iterator();
        while (chargeIterator.hasNext()) {
            var entry = chargeIterator.next();
            Charge charge = entry.getValue();
            Player player = server.getPlayer(entry.getKey());
            if (player == null || shown.get(charge.block) != charge.shown) {
                chargeIterator.remove();
                continue;
            }
            if (tick - charge.last > CHARGE_GAP_TICKS) {
                chargeIterator.remove();
                actionBar(player, "Recall cancelled.", NamedTextColor.GRAY);
                continue;
            }
            long elapsed = tick - charge.start;
            if (elapsed >= charge.holdTicks) {
                chargeIterator.remove();
                complete(player, charge);
                continue;
            }
            double progress = (double) elapsed / charge.holdTicks;
            Location center = charge.block.getLocation().clone().add(0.5, 0.5, 0.5);
            if (elapsed % 4 == 0) {
                int filled = (int) Math.round(progress * 20);
                actionBar(player, "Recalling " + charge.shown.placed.name() + " " + "|".repeat(filled)
                        + ".".repeat(20 - filled) + " " + (int) (progress * 100) + "%", NamedTextColor.LIGHT_PURPLE);
                charge.block.getWorld().spawnParticle(Particle.PORTAL, center, 12, 0.2, 0.3, 0.2, 0.5);
            }
            if (elapsed % 10 == 0) {
                charge.block.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f,
                        (float) (0.5 + 1.5 * progress));
            }
        }
    }

    private void tickOutlines() {
        var outlineIterator = shown.entrySet().iterator();
        while (outlineIterator.hasNext()) {
            var entry = outlineIterator.next();
            Block block = entry.getKey();
            Shown outline = entry.getValue();
            if (outline.expires <= tick) {
                outlineIterator.remove();
                continue;
            }
            if (tick % 20 == 0) {
                if (crystals.get(block) == null) {
                    outlineIterator.remove();
                    continue;
                }
                outline.blocked = blocked(block, outline);
            }
            if (tick % 4 == 0) {
                Color color = outline.blocked > 0 ? Color.RED : PLACE_COLOR;
                for (Player player : block.getWorld().getPlayers()) {
                    if (player.getLocation().distanceSquared(block.getLocation()) <= 96 * 96) {
                        outline(player, outline.corners[0], outline.corners[1], color);
                    }
                }
            }
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

    private static void line(Player player, World world, double x1, double y1, double z1, double x2, double y2,
            double z2, double step, Particle.DustOptions dust) {
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

    @EventHandler
    public void chunkLoad(ChunkLoadEvent event) {
        List<Block> blocks = tapers.blocks(event.getChunk());
        if (!blocks.isEmpty()) sync.accept(() -> blocks.forEach(BuildingListener::quiet));
    }

    /**
     * Freezes the vault under a taper: it stays inactive (no opening, sounds or loot cycling) and always shows an
     * ender eye. Vault state only changes once the game time passes the next state update time.
     */
    static void quiet(Block block) {
        if (block.getType() != Material.VAULT || !(block.getState() instanceof org.bukkit.block.Vault vault)) return;
        if (vault.getBlockData() instanceof org.bukkit.block.data.type.Vault data
                && data.getVaultState() != org.bukkit.block.data.type.Vault.State.INACTIVE) {
            data.setVaultState(org.bukkit.block.data.type.Vault.State.INACTIVE);
            vault.setBlockData(data);
        }
        vault.setNextStateUpdateTime(Long.MAX_VALUE);
        vault.setDisplayedItem(new ItemStack(Material.ENDER_EYE));
        vault.update(true, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) {
        Block block = event.getBlock();
        boolean creative = event.getPlayer().getGameMode() == GameMode.CREATIVE;
        if (tapers.contains(block)) {
            tapers.remove(block);
            event.setDropItems(false);
            event.setExpToDrop(0);
            if (!creative) block.getWorld().dropItemNaturally(block.getLocation().clone().add(0.5, 0.5, 0.5), items.taper());
            return;
        }
        PlacedCrystals.Placed placed = crystals.get(block);
        if (placed == null) return;
        event.setDropItems(false);
        event.setExpToDrop(0);
        pickUp(block, placed, !creative);
    }

    /** Physics (e.g. the supporting block disappearing) pops the crystal off as its storage crystal item. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void destroy(BlockDestroyEvent event) {
        PlacedCrystals.Placed placed = crystals.get(event.getBlock());
        if (placed == null) return;
        event.setWillDrop(false);
        pickUp(event.getBlock(), placed, true);
    }

    private void pickUp(Block block, PlacedCrystals.Placed placed, boolean drop) {
        crystals.remove(block);
        shown.remove(block);
        charges.values().removeIf(charge -> charge.block.equals(block));
        if (drop) {
            block.getWorld().dropItemNaturally(block.getLocation().clone().add(0.5, 0.5, 0.5),
                    items.storageCrystal(placed.blueprint(), placed.name(), placed.count()));
        }
    }

    private boolean protectedBlock(Block block) { return tapers.contains(block) || crystals.get(block) != null; }

    @EventHandler(ignoreCancelled = true)
    public void entityExplode(EntityExplodeEvent event) { event.blockList().removeIf(this::protectedBlock); }

    @EventHandler(ignoreCancelled = true)
    public void blockExplode(BlockExplodeEvent event) { event.blockList().removeIf(this::protectedBlock); }

    @EventHandler(ignoreCancelled = true)
    public void entityChangeBlock(EntityChangeBlockEvent event) {
        if (crystals.get(event.getBlock()) != null) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void pistonExtend(BlockPistonExtendEvent event) {
        if (pistonTouchesCrystal(event.getBlock(), event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void pistonRetract(BlockPistonRetractEvent event) {
        if (pistonTouchesCrystal(event.getBlock(), event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    /** Amethyst clusters break when pushed, so refuse any piston move that could reach a placed crystal. */
    private boolean pistonTouchesCrystal(Block piston, List<Block> moved, BlockFace direction) {
        if (crystals.get(piston.getRelative(direction)) != null) return true;
        for (Block block : moved) {
            if (crystals.get(block) != null || crystals.get(block.getRelative(direction)) != null
                    || crystals.get(block.getRelative(direction.getOppositeFace())) != null) {
                return true;
            }
        }
        return false;
    }

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
        charges.remove(id);
        handledAt.remove(id);
    }

    private static String blockedText(int blocked) {
        return blocked + " block" + (blocked == 1 ? " is" : "s are") + " in the way.";
    }

    private static void message(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text(text, color));
    }

    private static void actionBar(Player player, String text, NamedTextColor color) {
        player.sendActionBar(Component.text(text, color));
    }
}
