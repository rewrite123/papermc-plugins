package io.github.ihormel.soulgem;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class GemChargingTest extends PaperTest {
    private GemItems gems;
    private GemCharging listener;
    private Player player;
    private PlayerInventory inventory;

    @BeforeEach
    void setUpCharging() {
        gems = new GemItems(MockBukkit.createMockPlugin("soulgem"));
        listener = new GemCharging(gems);
        inventory = server.addPlayer().getInventory();
        player = mock(Player.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.hasPermission("soulgem.use")).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getExperiencePointsNeededForNextLevel()).thenReturn(7);
        inventory.setItemInOffHand(gems.create(GemItems.Kind.AMETHYST, false));
    }

    private int gain(int amount) {
        var event = new PlayerExpChangeEvent(player, amount);
        listener.gainExperience(event);
        return event.getAmount();
    }

    @Test
    void partialPickupGoesToTheGemNotThePlayer() {
        assertEquals(0, gain(3));
        assertEquals(3, gems.progress(inventory.getItemInOffHand()));
        assertEquals(7, gems.required(inventory.getItemInOffHand()));
        assertFalse(gems.charged(inventory.getItemInOffHand()));
        verify(player, never()).giveExpLevels(anyInt());
    }

    @Test
    void exactThresholdChargesOneGem() {
        assertEquals(0, gain(7));
        assertTrue(gems.charged(inventory.getItemInOffHand()));
        assertEquals(1, inventory.getItemInOffHand().getAmount());
    }

    @Test
    void excessXpGoesToThePlayerAndDoesNotChargeMoreGems() {
        assertEquals(13, gain(20));
        assertTrue(gems.charged(inventory.getItemInOffHand()));
        assertEquals(10, gain(10));
    }

    @Test
    void targetLocksWhenFirstXpIsAbsorbed() {
        when(player.getExperiencePointsNeededForNextLevel()).thenReturn(112);
        assertEquals(0, gain(10));
        when(player.getExperiencePointsNeededForNextLevel()).thenReturn(7);
        assertEquals(0, gain(100));
        assertEquals(112, gems.required(inventory.getItemInOffHand()));
        assertEquals(8, gain(10));
        assertTrue(gems.charged(inventory.getItemInOffHand()));
    }

    @Test
    void echoGemsUseTheSameCost() {
        inventory.setItemInOffHand(gems.create(GemItems.Kind.ECHO, false));
        assertEquals(1, gain(8));
        assertTrue(gems.charged(inventory.getItemInOffHand()));
        assertEquals(GemItems.Kind.ECHO, gems.kind(inventory.getItemInOffHand()));
    }

    @Test
    void mainHandGemDoesNotAbsorbXp() {
        inventory.setItemInMainHand(gems.create(GemItems.Kind.ECHO, false));
        inventory.setItemInOffHand(new ItemStack(Material.AIR));
        assertEquals(9, gain(9));
    }

    @Test
    void zeroAndNegativeAwardsDoNotInitializeProgress() {
        assertEquals(0, gain(0));
        assertEquals(-5, gain(-5));
        assertEquals(0, gems.required(inventory.getItemInOffHand()));
    }

    @Test
    void unauthorizedDeadAndSpectatorPlayersKeepTheirXp() {
        when(player.hasPermission("soulgem.use")).thenReturn(false);
        assertEquals(7, gain(7));
        when(player.hasPermission("soulgem.use")).thenReturn(true);
        when(player.isDead()).thenReturn(true);
        assertEquals(7, gain(7));
        when(player.isDead()).thenReturn(false);
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        assertEquals(7, gain(7));
    }

    @Test
    void unsupportedOverstackedGemDoesNotLoseXp() {
        var gem = gems.create(GemItems.Kind.ECHO, false);
        gem.setAmount(2);
        inventory.setItemInOffHand(gem);
        assertEquals(7, gain(7));
        assertFalse(gems.charged(inventory.getItemInOffHand()));
        verify(player).sendMessage("Hold just one uncharged soul gem in your off-hand to charge it.");
    }

    @Test
    void corruptProgressIsReportedWithoutXpLoss() {
        var gem = gems.create(GemItems.Kind.ECHO, false);
        gem.editMeta(meta -> meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey("soulgem", "progress"),
                org.bukkit.persistence.PersistentDataType.INTEGER, -1));
        inventory.setItemInOffHand(gem);
        assertEquals(7, gain(7));
        verify(player).sendMessage("This soul gem has invalid charge data and cannot absorb XP.");
    }
}
