package io.github.ihormel.betterbarrels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;

final class Conversion {
    private final BarrelStorage storage;
    private final Path backups;
    private final Function<ItemStack, byte[]> encode;

    Conversion(BarrelStorage storage, Path backups, Function<ItemStack, byte[]> encode) {
        this.storage = storage;
        this.backups = backups;
        this.encode = encode;
    }

    boolean prepare(Barrel barrel, boolean uninstall) throws IOException {
        boolean legacyItems = !barrel.getInventory().isEmpty();
        if (uninstall || legacyItems) {
            toChest(barrel);
            return true;
        }
        if (!storage.managed(barrel)) storage.write(barrel, new Stock(null, 0));
        return false;
    }

    void toChest(Barrel barrel) throws IOException {
        List<ItemStack> contents = new ArrayList<>(storage.read(barrel).stacks());
        for (ItemStack item : barrel.getInventory().getContents()) {
            if (item != null && !item.isEmpty()) contents.add(item.clone());
        }
        // A durable, non-overwritten snapshot remains available for manual disaster recovery.
        if (!contents.isEmpty()) backup(barrel, contents);
        var name = barrel.customName();
        String lock = barrel.getLock();
        var block = barrel.getBlock();
        var data = (org.bukkit.block.data.type.Chest) Material.CHEST.createBlockData();
        data.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
        data.setFacing(((org.bukkit.block.data.type.Barrel) barrel.getBlockData()).getFacing()
                .getDirection().getY() == 0
                ? ((org.bukkit.block.data.type.Barrel) barrel.getBlockData()).getFacing()
                : org.bukkit.block.BlockFace.NORTH);
        List<ItemStack> overflow = new ArrayList<>();
        try {
            block.setType(Material.CHEST, false);
            block.setBlockData(data, false);
            if (!(block.getState() instanceof Chest chest)) {
                throw new IllegalStateException("Cannot create recovery chest at " + block.getLocation());
            }
            chest.customName(name);
            chest.setLock(lock);
            for (ItemStack item : contents) overflow.addAll(chest.getSnapshotInventory().addItem(item).values());
            if (!chest.update(false, false)) {
                throw new IllegalStateException("Cannot save recovery chest at " + block.getLocation());
            }
        } catch (RuntimeException failure) {
            if (!barrel.update(true, false)) {
                failure.addSuppressed(new IllegalStateException("Cannot restore original barrel; recover from conversion-backups"));
            }
            throw failure;
        }
        for (ItemStack item : overflow) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 1, 0.5), item);
        }
    }

    private void backup(Barrel barrel, List<ItemStack> contents) throws IOException {
        Path directory = backups.resolve(barrel.getWorld().getUID().toString());
        Files.createDirectories(directory);
        Path target = directory.resolve(barrel.getX() + "_" + barrel.getY() + "_" + barrel.getZ()
                + "_" + java.util.UUID.randomUUID() + ".txt");
        StringBuilder text = new StringBuilder("BetterBarrels conversion snapshot v1\n");
        for (ItemStack item : contents) {
            text.append(Base64.getEncoder().encodeToString(encode.apply(item))).append('\n');
        }
        try (FileChannel file = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer bytes = java.nio.charset.StandardCharsets.UTF_8.encode(text.toString());
            while (bytes.hasRemaining()) file.write(bytes);
            file.force(true);
        }
    }
}
