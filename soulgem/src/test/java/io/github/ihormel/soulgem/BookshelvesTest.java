package io.github.ihormel.soulgem;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookshelvesTest extends PaperTest {
    private EnchantingOffers offers;
    private World world;
    private Location table;

    @BeforeEach
    void setUpShelves() {
        offers = new EnchantingOffers();
        world = server.addSimpleWorld("world");
        table = new Location(world, 8, 64, 8);
        world.getBlockAt(8, 64, 8).setType(Material.ENCHANTING_TABLE);
        world.getChunkAt(0, 0).load();
    }

    @Test
    void emptyTableHasNoBookshelfPower() {
        assertEquals(0, offers.bookshelves(table));
    }

    @Test
    void shelvesCountAtBothHeightsAndDiagonalsButNotNextToTheTable() {
        table.clone().add(2, 0, 0).getBlock().setType(Material.BOOKSHELF);
        table.clone().add(2, 1, 0).getBlock().setType(Material.BOOKSHELF);
        table.clone().add(-2, 0, -2).getBlock().setType(Material.BOOKSHELF);
        table.clone().add(-1, 0, 0).getBlock().setType(Material.BOOKSHELF);
        assertEquals(3, offers.bookshelves(table));
    }

    @Test
    void eachBookshelfHeightNeedsItsOwnClearGap() {
        table.clone().add(2, 0, 0).getBlock().setType(Material.BOOKSHELF);
        table.clone().add(2, 1, 0).getBlock().setType(Material.BOOKSHELF);
        assertEquals(2, offers.bookshelves(table));
        table.clone().add(1, 0, 0).getBlock().setType(Material.STONE);
        assertEquals(1, offers.bookshelves(table));
        table.clone().add(1, 1, 0).getBlock().setType(Material.STONE);
        assertEquals(0, offers.bookshelves(table));
    }

    @Test
    void shelvesAboveTheTwoValidLayersDoNotCount() {
        table.clone().add(2, 2, 0).getBlock().setType(Material.BOOKSHELF);
        assertEquals(0, offers.bookshelves(table));
    }

    @Test
    void bookshelfPowerCapsAtFifteen() {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) != 2 && Math.abs(z) != 2) continue;
                for (int y = 0; y <= 1; y++) {
                    table.clone().add(x, y, z).getBlock().setType(Material.BOOKSHELF);
                }
            }
        }
        assertEquals(15, offers.bookshelves(table));
    }

    @Test
    void unloadedChunksAreNotReadOrLoaded() {
        World unloaded = mock(World.class);
        assertEquals(0, offers.bookshelves(new Location(unloaded, 0, 64, 0)));
        verify(unloaded, never()).getBlockAt(any(Location.class));
        verify(unloaded, never()).getChunkAt(anyInt(), anyInt());
        verify(unloaded, never()).loadChunk(anyInt(), anyInt());
    }
}
