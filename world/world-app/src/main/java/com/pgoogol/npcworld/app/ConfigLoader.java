package com.pgoogol.npcworld.app;

import com.pgoogol.npcworld.core.RewardConfig;
import com.pgoogol.npcworld.core.WorldConfig;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** Reads the stage configuration file (YAML) shared with the brains. */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    public static AppConfig load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, Object> root = new Yaml().load(reader);
            return parse(root);
        }
    }

    static AppConfig parse(Map<String, Object> root) {
        Section world = section(root, "world");
        Section rewards = section(root, "rewards");
        Section brain = section(root, "brain");
        Section training = section(root, "training");
        Section show = section(root, "show");

        WorldConfig worldConfig = new WorldConfig(
                new Section("", root).number("seed").longValue(),
                world.integer("width"),
                world.integer("height"),
                world.integer("npcs"),
                world.real("food_share"),
                world.real("water_share"),
                world.real("lava_share"),
                world.integer("food_regrow_ticks"),
                world.integer("need_per_tick"),
                world.integer("need_max"),
                world.integer("need_threshold"),
                world.integer("need_energy_loss"),
                world.integer("lava_energy_loss"),
                world.integer("consume_relief"),
                world.integer("energy_max"),
                world.integer("energy_regen"),
                world.integer("life_limit"),
                new RewardConfig(
                        rewards.real("per_tick"),
                        rewards.real("consume"),
                        rewards.integer("consume_need_min"),
                        rewards.real("death")));

        return new AppConfig(
                worldConfig,
                brain.text("address"),
                training.number("ticks").longValue(),
                training.integer("stats_window"),
                training.text("out_dir"),
                show.real("ticks_per_second"));
    }

    @SuppressWarnings("unchecked")
    private static Section section(Map<String, Object> root, String name) {
        Object value = root.get(name);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Missing config section: " + name);
        }
        return new Section(name + ".", (Map<String, Object>) map);
    }

    private record Section(String prefix, Map<String, Object> values) {

        Number number(String key) {
            if (values.get(key) instanceof Number number) {
                return number;
            }
            throw new IllegalArgumentException("Missing or non-numeric config value: " + prefix + key);
        }

        int integer(String key) {
            return number(key).intValue();
        }

        double real(String key) {
            return number(key).doubleValue();
        }

        String text(String key) {
            if (values.get(key) instanceof String text) {
                return text;
            }
            throw new IllegalArgumentException("Missing config value: " + prefix + key);
        }
    }
}
