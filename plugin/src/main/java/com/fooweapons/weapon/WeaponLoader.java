package com.fooweapons.weapon;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
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
            req(sounds, "dry_fire")
        );
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
