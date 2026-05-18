package com.fooweapons.weapon;

import com.fooweapons.fire.mode.FireMode;
import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WeaponLoader {

    public Weapon load(InputStream input) {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(input);
        if (root == null) throw new IllegalArgumentException("Empty YAML");
        String id = req(root, "id");
        String displayName = req(root, "display_name");
        int cmd = ((Number) req(root, "custom_model_data")).intValue();

        Map<String, Object> fire = req(root, "fire");
        Map<String, Object> mag = req(root, "mag");
        Map<String, Object> sounds = req(root, "sounds");

        List<FireMode> modes = parseModes(req(fire, "modes"));
        FireMode defaultMode = FireMode.fromYamlName(req(fire, "default_mode"));
        if (!modes.contains(defaultMode)) {
            throw new IllegalArgumentException(
                "default_mode " + defaultMode.yamlName() + " is not in modes list " + modes);
        }

        return new Weapon(
            id,
            displayName,
            cmd,
            num(fire, "damage").doubleValue(),
            num(fire, "headshot_multiplier").doubleValue(),
            num(fire, "range").doubleValue(),
            num(fire, "falloff_start").doubleValue(),
            num(fire, "falloff_end").doubleValue(),
            num(fire, "base_spread_degrees").doubleValue(),
            num(fire, "moving_spread_penalty_degrees").doubleValue(),
            num(fire, "rate_per_second").intValue(),
            num(mag, "size").intValue(),
            num(mag, "reload_time_ticks").intValue(),
            req(sounds, "fire"),
            req(sounds, "reload"),
            req(sounds, "dry_fire"),
            modes,
            defaultMode
        );
    }

    @SuppressWarnings("unchecked")
    private static List<FireMode> parseModes(Object raw) {
        if (!(raw instanceof List<?> rawList)) {
            throw new IllegalArgumentException("modes must be a YAML list");
        }
        if (rawList.isEmpty()) {
            throw new IllegalArgumentException("modes must not be empty");
        }
        List<FireMode> out = new ArrayList<>();
        for (Object o : rawList) {
            out.add(FireMode.fromYamlName(String.valueOf(o)));
        }
        return List.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    private static <T> T req(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) throw new IllegalArgumentException("Missing field: " + key);
        return (T) v;
    }

    private static Number num(Map<String, Object> map, String key) {
        return (Number) req(map, key);
    }
}
