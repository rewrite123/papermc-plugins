package io.github.ihormel.usefulcauldrons;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class ContentsStore {
    record Position(UUID world, int x, int y, int z) {
        static Position of(Block block) {
            return new Position(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        Block loadedBlock() {
            var level = Bukkit.getWorld(world);
            return level != null && level.isChunkLoaded(x >> 4, z >> 4) ? level.getBlockAt(x, y, z) : null;
        }

        String key() {
            return world + "," + x + "," + y + "," + z;
        }

        static Position parse(String key) {
            var parts = key.split(",", -1);
            if (parts.length != 4) throw new IllegalArgumentException("Invalid cauldron position: " + key);
            return new Position(UUID.fromString(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        }
    }

    private final Path path;

    ContentsStore(Path path) {
        this.path = path;
    }

    Map<Position, CauldronContents> load() throws IOException {
        var result = new HashMap<Position, CauldronContents>();
        if (!Files.exists(path)) return result;
        var yaml = new YamlConfiguration();
        try {
            yaml.load(path.toFile());
            if (yaml.getInt("schema") != 1) throw new IllegalArgumentException("Unsupported contents schema");
            var entries = yaml.getConfigurationSection("cauldrons");
            if (entries == null) throw new IllegalArgumentException("Missing cauldrons section");
            for (var key : entries.getKeys(false)) {
                var section = entries.getConfigurationSection(key);
                if (section == null) throw new IllegalArgumentException("Invalid entry: " + key);
                var contents = new CauldronContents();
                var ingredients = section.getConfigurationSection("ingredients");
                if (ingredients == null) throw new IllegalArgumentException("Missing ingredients: " + key);
                for (var name : ingredients.getKeys(false)) {
                    var material = Material.valueOf(name);
                    int amount = ingredients.getInt(name);
                    if (!Recipes.ingredient(material) || amount < 1 || amount > CauldronContents.CAPACITY) {
                        throw new IllegalArgumentException("Invalid ingredient: " + name);
                    }
                    contents.ingredients.put(material, amount);
                }
                contents.food = section.getString("food");
                contents.servings = section.getInt("servings");
                contents.uses = section.getInt("uses");
                contents.charcoalTicks = section.getInt("charcoal-ticks");
                if (section.contains("dye")) contents.dye = DyeColor.valueOf(section.getString("dye", ""));
                if (contents.size() > CauldronContents.CAPACITY || contents.servings < 0 || contents.servings > 8
                        || (contents.servings > 0 && !Foods.valid(contents.food))
                        || (contents.servings == 0 && contents.food != null)
                        || contents.uses < 0 || contents.uses > 64 || (contents.dye == null) != (contents.uses == 0)
                        || contents.charcoalTicks < 0 || contents.charcoalTicks > 1200) {
                    throw new IllegalArgumentException("Invalid cauldron contents: " + key);
                }
                result.put(Position.parse(key), contents);
            }
        } catch (InvalidConfigurationException | IllegalArgumentException exception) {
            throw new IOException("Cannot read cauldron contents; original file preserved: " + path, exception);
        }
        return result;
    }

    void save(Map<Position, CauldronContents> contents) throws IOException {
        var yaml = new YamlConfiguration();
        yaml.set("schema", 1);
        var entries = yaml.createSection("cauldrons");
        contents.forEach((position, state) -> {
            var section = entries.createSection(position.key());
            var ingredients = section.createSection("ingredients");
            state.ingredients.forEach((material, amount) -> ingredients.set(material.name(), amount));
            section.set("food", state.food);
            section.set("servings", state.servings);
            section.set("dye", state.dye == null ? null : state.dye.name());
            section.set("uses", state.uses);
            section.set("charcoal-ticks", state.charcoalTicks);
        });
        Files.createDirectories(path.getParent());
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, yaml.saveToString());
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
