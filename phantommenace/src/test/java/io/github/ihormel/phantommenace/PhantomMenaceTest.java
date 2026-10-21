package io.github.ihormel.phantommenace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.view.EnchantmentView;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class PhantomMenaceTest {
    private ServerMock server;
    private MenaceListener listener;
    private Player player;
    private EnchantingInventory table;
    private EnchantmentView view;
    private Enchantment menace;
    private org.bukkit.plugin.java.JavaPlugin plugin;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        // MockBukkit cannot bootstrap custom registries; use a real registered enchantment
        // as the injected identity to test purchases and spawn behavior.
        menace = Enchantment.RESPIRATION;
        plugin = MockBukkit.createMockPlugin("phantommenace");
        listener = new MenaceListener(menace, plugin);
        player = mock(Player.class);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        table = mock(EnchantingInventory.class);
        when(table.getSecondary()).thenReturn(RepellantItems.create());
        view = mock(EnchantmentView.class);
        when(view.getTopInventory()).thenReturn(table);
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void repellantIsNonDrinkableLapisWithGlowingAwkwardPotionAppearance() {
        var item = RepellantItems.create();
        assertEquals(Material.LAPIS_LAZULI, item.getType());
        assertEquals(Material.POTION.getKey(), item.getItemMeta().getItemModel());
        assertTrue(item.getItemMeta().getEnchantmentGlintOverride());
        assertEquals(PotionType.AWKWARD, item.getData(
                io.papermc.paper.datacomponent.DataComponentTypes.POTION_CONTENTS).potion());
        assertTrue(RepellantItems.isRepellant(item));
        assertTrue(item.isSimilar(RepellantItems.create()));
        assertEquals(64, item.getMaxStackSize());
        assertFalse(RepellantItems.isRepellant(null));
        assertFalse(RepellantItems.isRepellant(new ItemStack(Material.LAPIS_LAZULI)));
        assertFalse(RepellantItems.isRepellant(item.withType(Material.CHARCOAL)));
    }

    private ItemStack potion(Material material, PotionType type) {
        var item = new ItemStack(material);
        item.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(type));
        return item;
    }

    @Test
    void brewingAcceptsOnlyWaterBottles() {
        var input = RepellantItems.waterInput();
        assertTrue(input.test(potion(Material.POTION, PotionType.WATER)));
        assertFalse(input.test(potion(Material.POTION, PotionType.AWKWARD)));
        assertFalse(input.test(potion(Material.SPLASH_POTION, PotionType.WATER)));
        assertFalse(input.test(potion(Material.LINGERING_POTION, PotionType.WATER)));
        assertFalse(input.test(new ItemStack(Material.GLASS_BOTTLE)));
        var custom = potion(Material.POTION, PotionType.WATER);
        custom.editMeta(PotionMeta.class, meta -> meta.addCustomEffect(
                new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 100, 1), true));
        assertFalse(input.test(custom));
    }

    private PrepareItemEnchantEvent prepare(ItemStack item) {
        var offers = new EnchantmentOffer[] {new EnchantmentOffer(Enchantment.UNBREAKING, 1, 5),
                new EnchantmentOffer(Enchantment.UNBREAKING, 2, 10), new EnchantmentOffer(Enchantment.UNBREAKING, 3, 30)};
        var event = new PrepareItemEnchantEvent(player, view, null, item, offers, 0);
        event.setCancelled(true);
        listener.prepare(event);
        return event;
    }

    @Test
    void everyHelmetGetsExactlyOneOneLevelOffer() {
        for (var type : Material.values()) {
            if (type.isLegacy() || !type.name().endsWith("_HELMET")) continue;
            var event = prepare(new ItemStack(type));
            assertFalse(event.isCancelled(), type.name());
            assertEquals(menace, event.getOffers()[0].getEnchantment());
            assertEquals(1, event.getOffers()[0].getEnchantmentLevel());
            assertEquals(1, event.getOffers()[0].getCost());
            assertNull(event.getOffers()[1]);
            assertNull(event.getOffers()[2]);
        }
    }

    @Test
    void enchantedHelmetsCanBeUpgradedButAlreadyProtectedHelmetsCannot() {
        var helmet = new ItemStack(Material.DIAMOND_HELMET);
        helmet.addUnsafeEnchantment(Enchantment.PROTECTION, 4);
        assertFalse(prepare(helmet).isCancelled());
        helmet.addUnsafeEnchantment(menace, 1);
        assertTrue(prepare(helmet).isCancelled());
    }

    @Test
    void invalidGearAndPlayersDoNotGetOffers() {
        for (var material : new Material[] {Material.BOOK, Material.ENCHANTED_BOOK, Material.DIAMOND_CHESTPLATE,
                Material.CARVED_PUMPKIN, Material.PLAYER_HEAD}) {
            var event = prepare(new ItemStack(material));
            assertTrue(event.isCancelled());
            assertTrue(java.util.Arrays.stream(event.getOffers()).allMatch(java.util.Objects::isNull));
        }
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        assertTrue(prepare(new ItemStack(Material.IRON_HELMET)).isCancelled());
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.isDead()).thenReturn(true);
        assertTrue(prepare(new ItemStack(Material.IRON_HELMET)).isCancelled());
    }

    private EnchantItemEvent enchant(ItemStack item, int button) {
        var event = new EnchantItemEvent(player, view, null, item, 30,
                new HashMap<>(Map.of(Enchantment.UNBREAKING, 3)), Enchantment.UNBREAKING, 3, button);
        listener.enchant(event);
        return event;
    }

    @Test
    void purchaseOnlyAddsMenaceAndPreservesExistingMetadata() {
        var helmet = new ItemStack(Material.NETHERITE_HELMET);
        helmet.addUnsafeEnchantment(Enchantment.PROTECTION, 4);
        helmet.editMeta(meta -> meta.displayName(net.kyori.adventure.text.Component.text("My helmet")));
        var before = helmet.clone();
        var event = enchant(helmet, 0);
        assertFalse(event.isCancelled());
        assertEquals(Map.of(menace, 1), event.getEnchantsToAdd());
        assertEquals(1, event.getExpLevelCost());
        assertEquals(before, helmet);
        assertEquals(RepellantItems.create(), table.getSecondary());
    }

    @Test
    void invalidPurchasesAreCancelled() {
        assertTrue(enchant(new ItemStack(Material.DIAMOND_HELMET), 1).isCancelled());
        assertTrue(enchant(new ItemStack(Material.DIAMOND_HELMET), 2).isCancelled());
        assertTrue(enchant(new ItemStack(Material.BOOK), 0).isCancelled());
        var protectedHelmet = new ItemStack(Material.DIAMOND_HELMET);
        protectedHelmet.addUnsafeEnchantment(menace, 1);
        assertTrue(enchant(protectedHelmet, 0).isCancelled());
    }

    @Test
    void regularLapisAndSoulGemsAreNotIntercepted() {
        when(table.getSecondary()).thenReturn(new ItemStack(Material.LAPIS_LAZULI));
        var item = new ItemStack(Material.DIAMOND_HELMET);
        assertTrue(prepare(item).isCancelled());
        var event = enchant(item, 0);
        assertEquals(Map.of(Enchantment.UNBREAKING, 3), event.getEnchantsToAdd());
        assertEquals(30, event.getExpLevelCost());
    }

    private org.bukkit.World bottleWorld;

    private EnchantItemEvent bottlePurchase() {
        bottleWorld = mock(org.bukkit.World.class);
        var block = mock(org.bukkit.block.Block.class);
        when(block.getLocation()).thenReturn(new Location(bottleWorld, 10, 64, 20));
        var event = new EnchantItemEvent(player, view, block, new ItemStack(Material.DIAMOND_HELMET), 1,
                new HashMap<>(Map.of(menace, 1)), menace, 1, 0);
        listener.returnBottle(event);
        return event;
    }

    @Test
    void successfulPurchaseDropsOneBottleOnlyAfterVanillaCommits() {
        var event = bottlePurchase();
        verify(bottleWorld, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
        event.getItem().addUnsafeEnchantment(menace, 1);
        server.getScheduler().performOneTick();
        verify(bottleWorld, times(1)).dropItemNaturally(
                new Location(bottleWorld, 10.5, 65, 20.5), new ItemStack(Material.GLASS_BOTTLE));
        server.getScheduler().performOneTick();
        verify(bottleWorld, times(1)).dropItemNaturally(any(Location.class), any(ItemStack.class));
    }

    @Test
    void uncommittedPurchaseDoesNotDropABottle() {
        bottlePurchase();
        server.getScheduler().performOneTick();
        verify(bottleWorld, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
    }

    @Test
    void laterCancellationDoesNotDropABottleEvenIfItemWasChanged() {
        var event = bottlePurchase();
        event.setCancelled(true);
        event.getItem().addUnsafeEnchantment(menace, 1);
        server.getScheduler().performOneTick();
        verify(bottleWorld, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
    }

    @Test
    void creativeEnchantingDoesNotCreateFreeBottles() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        var event = bottlePurchase();
        event.getItem().addUnsafeEnchantment(menace, 1);
        server.getScheduler().performOneTick();
        verify(bottleWorld, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
    }

    @Test
    void ordinaryFuelDoesNotReturnABottle() {
        when(table.getSecondary()).thenReturn(new ItemStack(Material.LAPIS_LAZULI));
        var event = bottlePurchase();
        event.getItem().addUnsafeEnchantment(menace, 1);
        server.getScheduler().performOneTick();
        verify(bottleWorld, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
    }

    private PhantomPreSpawnEvent spawn(Player source, SpawnReason reason) {
        var event = new PhantomPreSpawnEvent(new Location(server.addSimpleWorld("world"), 0, 90, 0), source, reason);
        listener.spawn(event);
        return event;
    }

    @Test
    void onlyNaturalSpawnsForTheWearerAreBlocked() {
        var inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        var helmet = new ItemStack(Material.DIAMOND_HELMET);
        helmet.addUnsafeEnchantment(menace, 1);
        when(inventory.getHelmet()).thenReturn(helmet);
        assertTrue(spawn(player, SpawnReason.NATURAL).isCancelled());
        assertFalse(spawn(player, SpawnReason.COMMAND).isCancelled());
        assertFalse(spawn(player, SpawnReason.SPAWNER).isCancelled());
        when(inventory.getHelmet()).thenReturn(new ItemStack(Material.DIAMOND_HELMET));
        assertFalse(spawn(player, SpawnReason.NATURAL).isCancelled());
        when(inventory.getHelmet()).thenReturn(null);
        assertFalse(spawn(player, SpawnReason.NATURAL).isCancelled());
        assertFalse(spawn(null, SpawnReason.NATURAL).isCancelled());
    }

    @Test
    void repellantCannotBeCraftedAsLapis() {
        var inventory = mock(CraftingInventory.class);
        when(inventory.getMatrix()).thenReturn(new ItemStack[] {null, RepellantItems.create()});
        var prepare = mock(PrepareItemCraftEvent.class);
        when(prepare.getInventory()).thenReturn(inventory);
        listener.prepareCraft(prepare);
        verify(inventory).setResult(null);
        var craft = mock(CraftItemEvent.class);
        when(craft.getInventory()).thenReturn(inventory);
        when(craft.getWhoClicked()).thenReturn(player);
        listener.craft(craft);
        verify(craft).setCancelled(true);
        verify(player).sendMessage("Phantom repellant cannot be used as crafting lapis.");
    }
}
