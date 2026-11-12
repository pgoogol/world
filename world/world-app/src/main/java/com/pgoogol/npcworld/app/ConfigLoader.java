package com.pgoogol.npcworld.app;

import com.pgoogol.npcworld.core.EconomyConfig;
import com.pgoogol.npcworld.core.PopulationConfig;
import com.pgoogol.npcworld.core.RewardConfig;
import com.pgoogol.npcworld.core.SeasonConfig;
import com.pgoogol.npcworld.core.WorldConfig;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Reads the stage configuration file (YAML) shared with the brains. Settings added in later stages
 * are optional: a missing one takes the value that reproduces the earlier stages.
 */
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
                world.numberOr("lifespan_ticks", 0).longValue(),
                world.numberOr("lifespan_spread", 0).doubleValue(),
                world.numberOr("aging_start_tick", 0).longValue(),
                world.numberOr("view_radius", 1).intValue(),
                seasons(optionalSection(root, "seasons")),
                economy(optionalSection(root, "economy")),
                population(optionalSection(root, "population")),
                new RewardConfig(
                        rewards.real("per_tick"),
                        rewards.real("consume"),
                        rewards.integer("consume_need_min"),
                        rewards.real("death"),
                        rewards.numberOr("winter_survived", 0).doubleValue()));

        return new AppConfig(
                worldConfig,
                brain.text("address"),
                training.number("ticks").longValue(),
                training.integer("stats_window"),
                training.text("out_dir"),
                show.real("ticks_per_second"));
    }

    private static SeasonConfig seasons(Section section) {
        if (section.values().isEmpty()) {
            return SeasonConfig.NONE;
        }
        return new SeasonConfig(
                section.integer("season_ticks"),
                section.integers("growth_percent"),
                section.integer("cold_energy_loss"),
                section.bool("winter_rot"));
    }

    private static EconomyConfig economy(Section section) {
        if (section.values().isEmpty()) {
            return EconomyConfig.NONE;
        }
        return new EconomyConfig(
                section.real("tree_share"),
                section.integer("tree_regrow_ticks"),
                section.integer("inventory_size"),
                section.integer("field_grow_ticks"),
                section.integer("field_yield"),
                section.integer("shelter_wood"),
                section.integer("shelter_work"),
                section.integer("shelter_capacity"),
                section.integer("store_wood"),
                section.integer("store_work"),
                section.integer("store_capacity"));
    }

    private static PopulationConfig population(Section section) {
        if (section.values().isEmpty()) {
            return PopulationConfig.CLASSIC;
        }
        return new PopulationConfig(
                section.bool("death_is_final"),
                section.bool("newborn_on_old_age"),
                section.integer("founders_radius"),
                section.integer("max_npcs"),
                section.integer("birth_food_cost"),
                section.integer("birth_reserve_per_npc"),
                section.integer("birth_interval_ticks"));
    }

    @SuppressWarnings("unchecked")
    private static Section section(Map<String, Object> root, String name) {
        Object value = root.get(name);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Missing config section: " + name);
        }
        return new Section(name + ".", (Map<String, Object>) map);
    }

    /** A section added after stage 1: a missing one is empty. */
    private static Section optionalSection(Map<String, Object> root, String name) {
        return root.containsKey(name) ? section(root, name) : new Section(name + ".", Map.of());
    }

    private record Section(String prefix, Map<String, Object> values) {

        Number number(String key) {
            if (values.get(key) instanceof Number number) {
                return number;
            }
            throw new IllegalArgumentException("Missing or non-numeric config value: " + prefix + key);
        }

        /** For settings added after stage 1: older config files do not have them. */
        Number numberOr(String key, Number fallback) {
            return values.containsKey(key) ? number(key) : fallback;
        }

        int integer(String key) {
            return number(key).intValue();
        }

        double real(String key) {
            return number(key).doubleValue();
        }

        boolean bool(String key) {
            if (values.get(key) instanceof Boolean flag) {
                return flag;
            }
            throw new IllegalArgumentException("Missing or non-boolean config value: " + prefix + key);
        }

        List<Integer> integers(String key) {
            if (values.get(key) instanceof List<?> list && list.stream().allMatch(Number.class::isInstance)) {
                return list.stream().map(item -> ((Number) item).intValue()).toList();
            }
            throw new IllegalArgumentException("Missing or non-numeric list: " + prefix + key);
        }

        String text(String key) {
            if (values.get(key) instanceof String text) {
                return text;
            }
            throw new IllegalArgumentException("Missing config value: " + prefix + key);
        }
    }
}
