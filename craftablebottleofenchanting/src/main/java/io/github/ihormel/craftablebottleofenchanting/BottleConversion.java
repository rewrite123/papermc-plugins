package io.github.ihormel.craftablebottleofenchanting;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class BottleConversion {
    static final int XP_COST = 11;

    private BottleConversion() {}

    static boolean convert(Player player, int bottleSlot) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        ItemStack source = storage[bottleSlot];
        if (source == null || source.getType() != Material.GLASS_BOTTLE || source.getAmount() < 1) {
            player.sendMessage("Hold glass bottles in your main hand to bottle experience.");
            return false;
        }
        int experience = player.calculateTotalExperiencePoints();
        if (experience < XP_COST) {
            player.sendMessage("Bottle Experience requires 11 XP points, not levels.");
            return false;
        }
        ItemStack[] result = plan(storage, bottleSlot);
        if (result == null) {
            player.sendMessage("Make room in your inventory for a Bottle o' Enchanting.");
            return false;
        }
        player.setExperienceLevelAndProgress(experience - XP_COST);
        player.getInventory().setStorageContents(result);
        player.sendMessage("Bottled experience for 11 XP points. No lapis consumed.");
        return true;
    }

    static ItemStack[] plan(ItemStack[] storage, int bottleSlot) {
        ItemStack[] result = new ItemStack[storage.length];
        for (int slot = 0; slot < storage.length; slot++) {
            result[slot] = storage[slot] == null ? null : storage[slot].clone();
        }
        ItemStack source = result[bottleSlot];
        if (source.getAmount() == 1) {
            result[bottleSlot] = null;
        } else {
            source.setAmount(source.getAmount() - 1);
        }
        ItemStack output = new ItemStack(Material.EXPERIENCE_BOTTLE);
        for (ItemStack stack : result) {
            if (stack != null && stack.isSimilar(output) && stack.getAmount() < stack.getMaxStackSize()) {
                stack.setAmount(stack.getAmount() + 1);
                return result;
            }
        }
        for (int slot = 0; slot < result.length; slot++) {
            if (result[slot] == null || result[slot].getType().isAir()) {
                result[slot] = output;
                return result;
            }
        }
        return null;
    }
}
