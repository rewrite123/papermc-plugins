package io.github.ihormel.mobbanner;

import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

final class KillRewards implements Listener {
    private final BannerItems items;

    KillRewards(BannerItems items) {
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        EntityType type = event.getEntityType();
        var player = event.getEntity().getKiller();
        if (player == null || !MobTypes.SUPPORTED.contains(type)) return;
        NamespacedKey key = new NamespacedKey("mobbanner", "kills_" + type.name().toLowerCase(Locale.ROOT));
        var data = player.getPersistentDataContainer();
        long previous = data.getOrDefault(key, PersistentDataType.LONG, 0L);
        long total = previous == Long.MAX_VALUE ? previous : previous + 1;
        data.set(key, PersistentDataType.LONG, total);
        if (total != previous && MobTypes.rewards(total)) {
            items.give(player, type);
            player.sendMessage("You earned a mob banner for killing " + total
                    + " mobs of type " + MobTypes.name(type) + "!");
        }
    }
}
