package io.github.ihormel.mobbanner;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Banner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class BannerItemsTest extends PaperTest {
    @Test
    void createsIdentifiablePatternedGlowingBannerForEverySupportedMob() {
        for (EntityType type : MobTypes.SUPPORTED) {
            ItemStack item = items.create(type);
            assertEquals(1, item.getAmount());
            assertTrue(item.getType().name().endsWith("_BANNER"));
            assertEquals(type, items.read(item));
            BannerMeta meta = (BannerMeta) item.getItemMeta();
            assertTrue(meta.getEnchantmentGlintOverride());
            assertEquals(2, meta.numberOfPatterns());
            assertNotNull(meta.displayName());
            assertNotNull(meta.lore());
            assertEquals(type, items.read(item.clone()));
        }
    }

    @Test
    void ordinaryOrForgedNamedBannersAreNotWards() {
        ItemStack ordinary = new ItemStack(Material.WHITE_BANNER);
        var meta = ordinary.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Zombie Ward Banner"));
        ordinary.setItemMeta(meta);
        assertNull(items.read(ordinary));
        assertNull(items.read(new ItemStack(Material.STONE)));
        assertNull(items.read((ItemStack) null));
    }

    @Test
    void persistentBlockMarkerSurvivesStateUpdateAndNewFactory() {
        var world = server.addSimpleWorld("world");
        var block = world.getBlockAt(0, 64, 0);
        block.setType(Material.BLACK_BANNER);
        Banner banner = (Banner) block.getState();
        items.write(banner.getPersistentDataContainer(), EntityType.BAT);
        assertTrue(banner.update());
        BannerItems reloaded = new BannerItems(new NamespacedKey("mobbanner", "mob"));
        assertEquals(EntityType.BAT, reloaded.read((Banner) block.getState()));
    }

    @Test
    void unknownOrUnsupportedMarkersDoNotSuppressMobs() {
        ItemStack item = new ItemStack(Material.WHITE_BANNER);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("mobbanner", "mob"),
                PersistentDataType.STRING, "NOT_A_MOB");
        item.setItemMeta(meta);
        assertNull(items.read(item));
        assertThrows(IllegalArgumentException.class, () -> items.create(EntityType.COW));
    }
}
