package io.github.ihormel.mobbanner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Locale;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class KillRewardsTest extends PaperTest {
    @Test
    void endermanKillsAwardBannerAtTheSameMilestone() {
        var player = server.addPlayer("Alex");
        player.getPersistentDataContainer().set(key(EntityType.ENDERMAN), PersistentDataType.LONG, 99L);
        new KillRewards(items).onDeath(death(player, EntityType.ENDERMAN));
        assertEquals(100L, count(player, EntityType.ENDERMAN));
        assertEquals(EntityType.ENDERMAN, items.read(player.getInventory().getItem(0)));
        assertEquals("You earned a mob banner for killing 100 mobs of type Enderman!", player.nextMessage());
    }

    @Test
    void countsPerPlayerAndPerTypeAndRewardsAtOneHundred() {
        var alice = server.addPlayer("Alice");
        Player bob = server.addPlayer("Bob");
        KillRewards rewards = new KillRewards(items);
        for (int count = 0; count < 99; count++) rewards.onDeath(death(alice, EntityType.ZOMBIE));
        assertEquals(99L, count(alice, EntityType.ZOMBIE));
        assertTrue(alice.getInventory().isEmpty());
        rewards.onDeath(death(alice, EntityType.ZOMBIE));
        assertEquals(EntityType.ZOMBIE, items.read(alice.getInventory().getItem(0)));
        assertEquals("You earned a mob banner for killing 100 mobs of type Zombie!", alice.nextMessage());
        rewards.onDeath(death(alice, EntityType.BAT));
        rewards.onDeath(death(bob, EntityType.ZOMBIE));
        assertEquals(100L, count(alice, EntityType.ZOMBIE));
        assertEquals(1L, count(alice, EntityType.BAT));
        assertEquals(1L, count(bob, EntityType.ZOMBIE));
    }

    @Test
    void persistsAcrossListenerRecreationAndDoesNotRewardTwice() {
        Player player = server.addPlayer();
        player.getPersistentDataContainer().set(key(EntityType.BAT), PersistentDataType.LONG, 199L);
        new KillRewards(items).onDeath(death(player, EntityType.BAT));
        new KillRewards(items).onDeath(death(player, EntityType.BAT));
        assertEquals(201L, count(player, EntityType.BAT));
        assertEquals(1, player.getInventory().getItem(0).getAmount());
        assertEquals(EntityType.BAT, items.read(player.getInventory().getItem(0)));
    }

    @Test
    void ignoresUncreditedAndUnsupportedKills() {
        Player player = server.addPlayer();
        KillRewards rewards = new KillRewards(items);
        rewards.onDeath(death(null, EntityType.ZOMBIE));
        rewards.onDeath(death(player, EntityType.COW));
        assertTrue(player.getPersistentDataContainer().getKeys().isEmpty());
    }

    @Test
    void fullInventoryDropsRewardAtKillerWithoutLosingIt() {
        Player player = mock(Player.class);
        var dataOwner = server.addPlayer();
        World world = mock(World.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getPersistentDataContainer()).thenReturn(dataOwner.getPersistentDataContainer());
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(dataOwner.getLocation());
        when(player.getInventory()).thenReturn(inventory);
        ItemStack reward = items.create(EntityType.BAT);
        when(inventory.addItem(org.mockito.ArgumentMatchers.<ItemStack>any()))
                .thenReturn(new java.util.HashMap<>(Map.of(0, reward)));
        player.getPersistentDataContainer().set(key(EntityType.BAT), PersistentDataType.LONG, 99L);
        new KillRewards(items).onDeath(death(player, EntityType.BAT));
        verify(world).dropItemNaturally(player.getLocation(), reward);
    }

    @Test
    void counterDoesNotOverflowAndAwardAgain() {
        Player player = server.addPlayer();
        player.getPersistentDataContainer().set(key(EntityType.BAT), PersistentDataType.LONG, Long.MAX_VALUE);
        new KillRewards(items).onDeath(death(player, EntityType.BAT));
        assertEquals(Long.MAX_VALUE, count(player, EntityType.BAT));
        assertTrue(player.getInventory().isEmpty());
    }

    private EntityDeathEvent death(Player killer, EntityType type) {
        LivingEntity mob = mock(LivingEntity.class);
        when(mob.getKiller()).thenReturn(killer);
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(mob);
        when(event.getEntityType()).thenReturn(type);
        return event;
    }

    private NamespacedKey key(EntityType type) {
        return new NamespacedKey("mobbanner", "kills_" + type.name().toLowerCase(Locale.ROOT));
    }

    private long count(Player player, EntityType type) {
        return player.getPersistentDataContainer().getOrDefault(key(type), PersistentDataType.LONG, 0L);
    }
}
