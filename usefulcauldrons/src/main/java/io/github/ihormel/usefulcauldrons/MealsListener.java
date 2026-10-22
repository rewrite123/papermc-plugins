package io.github.ihormel.usefulcauldrons;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

final class MealsListener implements Listener {
    static final NamespacedKey IMMUNE = new NamespacedKey("usefulcauldrons", "immune_until");
    static final NamespacedKey BURN = new NamespacedKey("usefulcauldrons", "burn_until");
    private final UsefulCauldronsPlugin plugin;
    private final Set<UUID> chocolateAbsorption = new HashSet<>();
    private final Set<UUID> burnDamage = new HashSet<>();

    MealsListener(UsefulCauldronsPlugin plugin) {
        this.plugin = plugin;
    }

    static boolean active(Player player, NamespacedKey key, long now) {
        return player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L) > now;
    }

    static void burn(Player player, long now) {
        // Re-entering refreshes the burn, but standing in the cauldron does not extend it every tick.
        player.getPersistentDataContainer().set(BURN, PersistentDataType.LONG, now + 20_000);
        player.setFireTicks(400);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent event) {
        String id = Foods.id(event.getItem());
        if (id == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (event.isCancelled()) return;
            var player = event.getPlayer();
            if (!player.isOnline() || player.isDead()) return;
            if (!Foods.candy(id) && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                plugin.give(player, new ItemStack(Material.BOWL));
            }
            apply(player, id, System.currentTimeMillis());
        });
    }

    void apply(Player player, String id, long now) {
        if (Foods.prepaid(id)) {
            for (var effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
            boolean chocolate = id.equals("hot_chocolate");
            player.getPersistentDataContainer().set(IMMUNE, PersistentDataType.LONG, now + (chocolate ? 8000 : 6000));
            if (chocolate) {
                chocolateAbsorption.add(player.getUniqueId());
                try {
                    Foods.absorption(player, 2);
                } finally {
                    chocolateAbsorption.remove(player.getUniqueId());
                }
            }
            return;
        }
        if (id.equals("hearty_stew")) {
            player.setFoodLevel(20);
            player.setSaturation(Math.max(player.getSaturation(), 12));
        }
        if (active(player, IMMUNE, now)) return;
        if (Foods.candy(id)) {
            Foods.effect(player, PotionEffectType.SPEED, Foods.candySeconds(id));
            if (id.endsWith(":GOLDEN_APPLE")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                Foods.absorption(player, 4);
            }
            return;
        }
        var effect = Foods.EFFECTS.get(id);
        if (effect != null) Foods.effect(player, effect.type(), effect.seconds());
        if (id.equals("golden_flavor")) Foods.absorption(player, 5);
        if (id.equals("hearty_stew")) {
            Foods.absorption(player, 1);
        }
        if (id.startsWith("suspicious:")) {
            var flower = Recipes.FLOWERS.get(Material.valueOf(id.substring(11)));
            player.addPotionEffect(new PotionEffect(flower.effect(), flower.ticks(), 0));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void effect(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player player && event.getNewEffect() != null
                && active(player, IMMUNE, System.currentTimeMillis())
                && !(chocolateAbsorption.contains(player.getUniqueId())
                    && event.getModifiedType().equals(PotionEffectType.ABSORPTION))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fireDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                && active(player, BURN, System.currentTimeMillis())
                && !burnDamage.contains(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void death(PlayerDeathEvent event) {
        event.getPlayer().getPersistentDataContainer().remove(IMMUNE);
        event.getPlayer().getPersistentDataContainer().remove(BURN);
    }

    void tick(Player player, long now) {
        if (!active(player, BURN, now) || player.isDead()) return;
        player.setFireTicks(40);
        if (player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)
                || player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        burnDamage.add(player.getUniqueId());
        try {
            player.damage(1, DamageSource.builder(DamageType.ON_FIRE).build());
        } finally {
            burnDamage.remove(player.getUniqueId());
        }
    }
}
