package io.github.ihormel.phantommenace;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry.EnchantmentCost;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.ItemTypeKeys;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.EquipmentSlotGroup;

public final class PhantomMenaceBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(RegistryEvents.ENCHANTMENT.compose(), event ->
                event.registry().register(TypedKey.create(RegistryKey.ENCHANTMENT, "phantommenace:phantom_menace"),
                        builder -> builder.description(Component.text("Phantom Menace"))
                                .supportedItems(RegistrySet.keySet(RegistryKey.ITEM,
                                        ItemTypeKeys.LEATHER_HELMET, ItemTypeKeys.CHAINMAIL_HELMET,
                                        ItemTypeKeys.IRON_HELMET, ItemTypeKeys.GOLDEN_HELMET, ItemTypeKeys.COPPER_HELMET,
                                        ItemTypeKeys.DIAMOND_HELMET, ItemTypeKeys.NETHERITE_HELMET,
                                        ItemTypeKeys.TURTLE_HELMET))
                                .weight(1).maxLevel(1)
                                .minimumCost(EnchantmentCost.of(1, 0))
                                .maximumCost(EnchantmentCost.of(1, 0))
                                .anvilCost(1).activeSlots(EquipmentSlotGroup.HEAD)));
    }
}
