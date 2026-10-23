package io.github.ihormel.ihatepowderedsnow;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.event.world.WorldInitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class PluginTest {
    private ServerMock server;

    private static final class GenerationWorld extends org.mockbukkit.mockbukkit.world.WorldMock {
        private final java.util.List<org.bukkit.generator.BlockPopulator> populators = new java.util.ArrayList<>();

        @Override
        public java.util.List<org.bukkit.generator.BlockPopulator> getPopulators() { return populators; }
    }

    private GenerationWorld world(String name) {
        var world = new GenerationWorld();
        world.setName(name);
        server.addWorld(world);
        return world;
    }

    @BeforeEach
    void setup() { server = MockBukkit.mock(); }

    @AfterEach
    void cleanup() { MockBukkit.unmock(); }

    @Test
    void registersExistingAndFutureWorldsExactlyOnceAndCleansUpOnDisable() {
        var existing = world("existing");
        var other = new org.bukkit.generator.BlockPopulator() {};
        existing.getPopulators().add(other);
        var plugin = MockBukkit.load(IHatePowderedSnowPlugin.class);
        assertEquals(2, existing.getPopulators().size());
        assertTrue(existing.getPopulators().contains(other));
        var future = world("future");
        server.getPluginManager().callEvent(new WorldInitEvent(future));
        server.getPluginManager().callEvent(new WorldInitEvent(existing));
        assertEquals(1, future.getPopulators().stream().filter(SnowPopulator.class::isInstance).count());
        assertEquals(1, existing.getPopulators().stream().filter(SnowPopulator.class::isInstance).count());
        server.getPluginManager().disablePlugin(plugin);
        assertEquals(java.util.List.of(other), existing.getPopulators());
        assertTrue(future.getPopulators().isEmpty());
        server.getPluginManager().enablePlugin(plugin);
        assertEquals(2, existing.getPopulators().size());
        assertEquals(1, future.getPopulators().size());
    }

    @Test
    void enablingDoesNotConvertExistingPowderAndPluginLoadsAtStartup() {
        var world = world("world");
        world.getBlockAt(0, 70, 0).setType(org.bukkit.Material.POWDER_SNOW);
        var plugin = MockBukkit.load(IHatePowderedSnowPlugin.class);
        assertEquals(org.bukkit.Material.POWDER_SNOW, world.getBlockAt(0, 70, 0).getType());
        assertEquals(org.bukkit.plugin.PluginLoadOrder.STARTUP, plugin.getDescription().getLoad());
    }
}
