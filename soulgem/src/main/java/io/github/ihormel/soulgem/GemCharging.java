package io.github.ihormel.soulgem;

import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;

final class GemCharging implements Listener {
    private final GemItems gems;

    GemCharging(GemItems gems) {
        this.gems = gems;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void gainExperience(PlayerExpChangeEvent event) {
        var player = event.getPlayer();
        var item = player.getInventory().getItemInOffHand();
        var kind = gems.kind(item);
        if (event.getAmount() <= 0 || kind == null || gems.charged(item)
                || !player.hasPermission("soulgem.use") || player.isDead()
                || player.getGameMode() == GameMode.SPECTATOR) return;
        if (item.getAmount() != 1) {
            player.sendMessage("Hold just one uncharged soul gem in your off-hand to charge it.");
            return;
        }
        int progress = gems.progress(item);
        int required = gems.required(item);
        if (progress < 0 || required < 0 || (required == 0 && progress != 0)
                || (required > 0 && progress >= required)) {
            player.sendMessage("This soul gem has invalid charge data and cannot absorb XP.");
            return;
        }
        if (required == 0) required = player.getExperiencePointsNeededForNextLevel();
        if (required <= 0) {
            player.sendMessage("Your XP level is outside the supported charging range.");
            return;
        }
        int absorbed = Math.min(event.getAmount(), required - progress);
        if (progress + absorbed == required) {
            player.getInventory().setItemInOffHand(gems.create(kind, true));
            player.sendMessage("Your " + kind.title + " is now charged!");
        } else {
            player.getInventory().setItemInOffHand(gems.withProgress(item, progress + absorbed, required));
        }
        event.setAmount(event.getAmount() - absorbed);
    }
}
