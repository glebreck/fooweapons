package com.fooweapons.weapon;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class WeaponRegistry {
    private final Map<String, Weapon> byId = new HashMap<>();
    private final WeaponLoader loader = new WeaponLoader();

    public void loadFromClasspath(ClassLoader cl, String... resourcePaths) throws IOException {
        Map<String, Weapon> loaded = new HashMap<>();
        for (String path : resourcePaths) {
            try (InputStream in = cl.getResourceAsStream(path)) {
                if (in == null) throw new IOException("Resource not found: " + path);
                Weapon w = loader.load(in);
                loaded.put(w.id(), w);
            }
        }
        byId.putAll(loaded);
    }

    public Optional<Weapon> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public int size() {
        return byId.size();
    }
}
