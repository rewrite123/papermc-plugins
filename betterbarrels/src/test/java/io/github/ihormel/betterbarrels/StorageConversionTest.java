package io.github.ihormel.betterbarrels;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageConversionTest extends PaperTest {
    @TempDir Path temp;
    private BarrelStorage storage;
    private Conversion conversion;
    private World world;
    private Barrel barrel;

    @BeforeEach
    void setupStorage() {
        TestCodec codec = new TestCodec();
        storage = new BarrelStorage(codec::decode, codec::encode);
        conversion = new Conversion(storage, temp.resolve("backups"), codec::encode);
        world = server.addSimpleWorld("barrel-world");
        world.getBlockAt(0, 65, 0).setType(Material.BARREL);
        barrel = (Barrel) world.getBlockAt(0, 65, 0).getState();
    }

    @Test
    void emptyLegacyBarrelBecomesManagedWithoutChangingBlock() throws IOException {
        assertFalse(conversion.prepare(barrel, false));
        assertEquals(Material.BARREL, barrel.getBlock().getType());
        assertTrue(storage.managed((Barrel) barrel.getBlock().getState()));
        assertEquals(0, storage.read((Barrel) barrel.getBlock().getState()).count());
    }

    @Test
    void storageRoundTripsThroughBlockStateAndNeverUsesVanillaInventory() {
        ItemStack named = new ItemStack(Material.DIAMOND);
        named.editMeta(meta -> meta.displayName(Component.text("Saved")));
        storage.write(barrel, new Stock(named, 2048));
        Barrel reloaded = (Barrel) barrel.getBlock().getState();
        assertEquals(2048, storage.read(reloaded).count());
        assertTrue(named.isSimilar(storage.read(reloaded).type()));
        assertTrue(reloaded.getInventory().isEmpty());
        storage.write(reloaded, new Stock(null, 0));
        Barrel empty = (Barrel) barrel.getBlock().getState();
        assertFalse(empty.getPersistentDataContainer().has(BarrelStorage.ITEM));
    }

    @Test
    void mixedLegacyContentsMoveToChestNotSingleItemStorage() throws IOException {
        barrel.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 37));
        barrel.getInventory().setItem(20, new ItemStack(Material.EMERALD, 8));
        assertTrue(conversion.prepare(barrel, false));
        Chest chest = (Chest) world.getBlockAt(0, 65, 0).getState();
        assertEquals(37, chest.getInventory().all(Material.DIAMOND).values().stream().mapToInt(ItemStack::getAmount).sum());
        assertEquals(8, chest.getInventory().all(Material.EMERALD).values().stream().mapToInt(ItemStack::getAmount).sum());
        try (var files = Files.walk(temp)) {
            assertEquals(1, files.filter(path -> path.toString().endsWith(".txt")).count());
        }
    }

    @Test
    void fullStoredBarrelFillsChestAndDropsExactlyOverflow() throws IOException {
        storage.write(barrel, new Stock(new ItemStack(Material.DIAMOND), 2048));
        assertTrue(conversion.prepare((Barrel) barrel.getBlock().getState(), true));
        Chest chest = (Chest) world.getBlockAt(0, 65, 0).getState();
        int inside = chest.getInventory().all(Material.DIAMOND).values().stream().mapToInt(ItemStack::getAmount).sum();
        int dropped = world.getEntitiesByClass(org.bukkit.entity.Item.class).stream()
                .mapToInt(entity -> entity.getItemStack().getAmount()).sum();
        assertEquals(1728, inside);
        assertEquals(320, dropped);
        assertEquals(2048, inside + dropped);
    }

    @Test
    void uninstallAlsoConvertsEmptyBarrelsWithoutDrops() throws IOException {
        conversion.prepare(barrel, true);
        assertEquals(Material.CHEST, world.getBlockAt(0, 65, 0).getType());
        assertTrue(world.getEntitiesByClass(org.bukkit.entity.Item.class).isEmpty());
    }

    @Test
    void legacyItemsUnexpectedlyInsertedIntoManagedBarrelArePreservedToo() throws IOException {
        storage.write(barrel, new Stock(new ItemStack(Material.DIAMOND), 20));
        barrel = (Barrel) barrel.getBlock().getState();
        barrel.getInventory().setItem(2, new ItemStack(Material.DIRT, 30));
        conversion.prepare(barrel, false);
        Chest chest = (Chest) world.getBlockAt(0, 65, 0).getState();
        assertEquals(20, chest.getInventory().getItem(0).getAmount());
        assertEquals(Material.DIAMOND, chest.getInventory().getItem(0).getType());
        assertEquals(30, chest.getInventory().getItem(1).getAmount());
    }

    @Test
    void failedBackupDoesNotReplaceOrClearOriginalBarrel() throws IOException {
        Path blocked = temp.resolve("not-a-directory");
        Files.writeString(blocked, "occupied");
        TestCodec codec = new TestCodec();
        conversion = new Conversion(storage, blocked, codec::encode);
        barrel.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
        assertThrows(IOException.class, () -> conversion.prepare(barrel, true));
        assertEquals(Material.BARREL, barrel.getBlock().getType());
        assertEquals(5, barrel.getInventory().getItem(0).getAmount());
    }

    @Test
    void corruptDataIsNotSilentlyConvertedOrDropped() {
        barrel.getPersistentDataContainer().set(BarrelStorage.COUNT, PersistentDataType.INTEGER, 5);
        barrel.update();
        assertThrows(IllegalStateException.class, () -> storage.read(barrel));
        assertThrows(IllegalStateException.class, () -> conversion.prepare(barrel, true));
        assertEquals(Material.BARREL, barrel.getBlock().getType());
    }

    @Test
    void failedStateUpdateIsExplicit() {
        Barrel detached = spy(barrel);
        doReturn(false).when(detached).update(false, false);
        assertThrows(IllegalStateException.class, () -> storage.write(detached, new Stock(null, 0)));
    }

    @Test
    void sixteenStackItemsRemainLegalAndTotalIsConserved() throws IOException {
        storage.write(barrel, new Stock(new ItemStack(Material.ENDER_PEARL), 2048));
        conversion.prepare((Barrel) barrel.getBlock().getState(), true);
        Chest chest = (Chest) world.getBlockAt(0, 65, 0).getState();
        int inside = chest.getInventory().all(Material.ENDER_PEARL).values().stream()
                .mapToInt(ItemStack::getAmount).sum();
        int dropped = world.getEntitiesByClass(org.bukkit.entity.Item.class).stream()
                .mapToInt(entity -> entity.getItemStack().getAmount()).sum();
        assertEquals(432, inside);
        assertEquals(1616, dropped);
        assertTrue(chest.getInventory().all(Material.ENDER_PEARL).values().stream()
                .allMatch(item -> item.getAmount() <= 16));
    }

    @Test
    void conversionKeepsNameLockAndDoesNotMergeAdjacentChest() throws IOException {
        world.getBlockAt(1, 65, 0).setType(Material.CHEST);
        barrel.customName(Component.text("Private supplies"));
        barrel.setLock("secret");
        barrel.update();
        conversion.prepare((Barrel) barrel.getBlock().getState(), true);
        Chest chest = (Chest) world.getBlockAt(0, 65, 0).getState();
        assertEquals(Component.text("Private supplies"), chest.customName());
        assertTrue(chest.isLocked());
        assertEquals(org.bukkit.block.data.type.Chest.Type.SINGLE,
                ((org.bukkit.block.data.type.Chest) chest.getBlockData()).getType());
    }
}
