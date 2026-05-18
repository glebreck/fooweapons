# Plan 2 Slice A — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add automatic-fire mechanic + selectable fire-mode toggle to the fooWeapons plugin, and ship `rifle_01` as the first weapon that exercises both.

**Architecture:** Two new packages — `com.fooweapons.fire.mode` (FireMode enum + cycle logic + Sneak+Q listener) and `com.fooweapons.fire.auto` (arm-swing pulse listener + per-tick tracker that calls into the existing `FireService.tryFire(...)`). Mode lives on the gun's PDC. The weapon YAML schema gains `modes: [...]` and `default_mode:`; pistol_01 is backfilled with `modes: [semi]`. `FireService` itself is unchanged — auto-fire is a different *trigger source*, not different fire logic.

**Tech Stack:** Java 21, Paper API 1.21.11-R0.1-SNAPSHOT, JUnit 5, SnakeYAML, Gradle Kotlin DSL.

**Spec:** [docs/superpowers/specs/2026-05-15-plan2-slice-a-design.md](../specs/2026-05-15-plan2-slice-a-design.md)

---

## File Map

**Create (Java main):**
- `plugin/src/main/java/com/fooweapons/fire/mode/FireMode.java`
- `plugin/src/main/java/com/fooweapons/fire/mode/FireModeCycle.java`
- `plugin/src/main/java/com/fooweapons/fire/mode/FireModeListener.java`
- `plugin/src/main/java/com/fooweapons/fire/auto/AutoFireTracker.java`
- `plugin/src/main/java/com/fooweapons/fire/auto/ArmSwingListener.java`

**Create (Java test):**
- `plugin/src/test/java/com/fooweapons/fire/mode/FireModeCycleTest.java`

**Create (config + assets):**
- `plugin/src/main/resources/weapons/rifle_01.yml`
- `resourcepack/assets/fooweapons/models/item/rifle_01.json`
- `resourcepack/assets/fooweapons/textures/item/rifle_01.png` (generated)

**Modify (Java main):**
- `plugin/src/main/java/com/fooweapons/weapon/Weapon.java` — add `modes`, `defaultMode`
- `plugin/src/main/java/com/fooweapons/weapon/WeaponLoader.java` — parse + validate
- `plugin/src/main/java/com/fooweapons/item/PdcKeys.java` — add `fireMode` key
- `plugin/src/main/java/com/fooweapons/item/ItemState.java` — add `getFireMode` / `setFireMode`
- `plugin/src/main/java/com/fooweapons/item/WeaponItemFactory.java` — write initial fire_mode PDC
- `plugin/src/main/java/com/fooweapons/fire/FireListener.java` — skip click-fire when mode is AUTO
- `plugin/src/main/java/com/fooweapons/hud/HudService.java` — read mode from PDC
- `plugin/src/main/java/com/fooweapons/feedback/SoundService.java` — add `playClick`, `playDeniedClick`
- `plugin/src/main/java/com/fooweapons/FooWeaponsPlugin.java` — load rifle_01, register new listeners + tracker

**Modify (config + assets):**
- `plugin/src/main/resources/weapons/pistol_01.yml` — backfill `modes: [semi]`, `default_mode: semi`
- `plugin/src/test/java/com/fooweapons/weapon/WeaponLoaderTest.java` — update existing fixture YAML; add tests for new validation rules
- `resourcepack/assets/minecraft/items/crossbow.json` — add case for CMD 1003
- `tools/generate_textures.py` — emit `rifle_01.png` in addition to `pistol_01.png`
- `PROGRESS.md` — check off completed items

---

## Task 1: FireMode enum

**Files:**
- Create: `plugin/src/main/java/com/fooweapons/fire/mode/FireMode.java`

- [ ] **Step 1: Create the enum file**

```java
package com.fooweapons.fire.mode;

public enum FireMode {
    SEMI("SEMI", "semi"),
    AUTO("AUTO", "auto");

    private final String label;
    private final String yamlName;

    FireMode(String label, String yamlName) {
        this.label = label;
        this.yamlName = yamlName;
    }

    public String label() { return label; }

    public String yamlName() { return yamlName; }

    public static FireMode fromYamlName(String s) {
        for (FireMode m : values()) {
            if (m.yamlName.equalsIgnoreCase(s)) return m;
        }
        throw new IllegalArgumentException("Unknown fire mode: " + s);
    }
}
```

- [ ] **Step 2: Build to confirm it compiles**

Run: `./gradlew :plugin:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/fire/mode/FireMode.java
git commit -m "feat: add FireMode enum with YAML-name round-trip"
```

---

## Task 2: FireModeCycle — pure-logic cycle with TDD

**Files:**
- Create: `plugin/src/test/java/com/fooweapons/fire/mode/FireModeCycleTest.java`
- Create: `plugin/src/main/java/com/fooweapons/fire/mode/FireModeCycle.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.fooweapons.fire.mode;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FireModeCycleTest {
    @Test
    void cyclesSemiToAuto() {
        assertEquals(FireMode.AUTO,
            FireModeCycle.next(FireMode.SEMI, List.of(FireMode.SEMI, FireMode.AUTO)));
    }

    @Test
    void cyclesAutoBackToSemi() {
        assertEquals(FireMode.SEMI,
            FireModeCycle.next(FireMode.AUTO, List.of(FireMode.SEMI, FireMode.AUTO)));
    }

    @Test
    void singleModeReturnsSelf() {
        assertEquals(FireMode.SEMI,
            FireModeCycle.next(FireMode.SEMI, List.of(FireMode.SEMI)));
    }

    @Test
    void currentNotInListThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> FireModeCycle.next(FireMode.AUTO, List.of(FireMode.SEMI)));
    }

    @Test
    void emptyListThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> FireModeCycle.next(FireMode.SEMI, List.of()));
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `./gradlew :plugin:test --tests "com.fooweapons.fire.mode.FireModeCycleTest"`
Expected: FAIL — `FireModeCycle` does not exist.

- [ ] **Step 3: Write the minimal implementation**

```java
package com.fooweapons.fire.mode;

import java.util.List;

public final class FireModeCycle {
    private FireModeCycle() {}

    public static FireMode next(FireMode current, List<FireMode> available) {
        if (available.isEmpty()) {
            throw new IllegalArgumentException("modes list is empty");
        }
        int idx = available.indexOf(current);
        if (idx < 0) {
            throw new IllegalArgumentException("current mode " + current + " not in available " + available);
        }
        return available.get((idx + 1) % available.size());
    }
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `./gradlew :plugin:test --tests "com.fooweapons.fire.mode.FireModeCycleTest"`
Expected: 5 tests passing.

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/fire/mode/FireModeCycle.java plugin/src/test/java/com/fooweapons/fire/mode/FireModeCycleTest.java
git commit -m "feat: add FireModeCycle pure-logic with tests"
```

---

## Task 3: Extend Weapon record with modes + defaultMode

**Files:**
- Modify: `plugin/src/main/java/com/fooweapons/weapon/Weapon.java`

- [ ] **Step 1: Add the two fields to the record**

Replace the entire file with:

```java
package com.fooweapons.weapon;

import com.fooweapons.fire.mode.FireMode;
import java.util.List;

public record Weapon(
    String id,
    String displayName,
    int customModelData,
    double damage,
    double headshotMultiplier,
    double range,
    double falloffStart,
    double falloffEnd,
    double baseSpreadDegrees,
    double movingSpreadPenaltyDegrees,
    int fireRatePerSecond,
    int magSize,
    int reloadTimeTicks,
    String fireSoundId,
    String reloadSoundId,
    String dryFireSoundId,
    List<FireMode> modes,
    FireMode defaultMode
) {
    public long fireCooldownMillis() {
        return 1000L / Math.max(1, fireRatePerSecond);
    }
}
```

- [ ] **Step 2: Build — expect a compile failure in WeaponLoader**

Run: `./gradlew :plugin:compileJava`
Expected: FAIL — `WeaponLoader.load(...)` no longer matches the record constructor (wrong arity).

This failure is intentional — Task 4 fixes it. Do NOT commit yet.

---

## Task 4: WeaponLoader parses and validates modes — extend TDD

**Files:**
- Modify: `plugin/src/test/java/com/fooweapons/weapon/WeaponLoaderTest.java`
- Modify: `plugin/src/main/java/com/fooweapons/weapon/WeaponLoader.java`

- [ ] **Step 1: Update existing test to include the new YAML fields and assert them**

Replace the entire contents of `WeaponLoaderTest.java` with:

```java
package com.fooweapons.weapon;

import com.fooweapons.fire.mode.FireMode;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WeaponLoaderTest {
    private static final String FULL_YAML = """
        id: pistol_01
        display_name: "Pistol"
        custom_model_data: 1001
        fire:
          modes: [semi]
          default_mode: semi
          damage: 4.0
          headshot_multiplier: 2.0
          range: 50.0
          falloff_start: 25.0
          falloff_end: 50.0
          rate_per_second: 4
          base_spread_degrees: 1.0
          moving_spread_penalty_degrees: 1.5
        mag:
          size: 12
          reload_time_ticks: 36
        sounds:
          fire: "minecraft:entity.firework_rocket.blast"
          reload: "minecraft:item.crossbow.loading_middle"
          dry_fire: "minecraft:block.dispenser.fail"
        """;

    private static Weapon load(String yaml) {
        return new WeaponLoader().load(
            new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void loadsAllFieldsFromYaml() {
        Weapon w = load(FULL_YAML);
        assertEquals("pistol_01", w.id());
        assertEquals("Pistol", w.displayName());
        assertEquals(1001, w.customModelData());
        assertEquals(4.0, w.damage());
        assertEquals(2.0, w.headshotMultiplier());
        assertEquals(50.0, w.range());
        assertEquals(25.0, w.falloffStart());
        assertEquals(50.0, w.falloffEnd());
        assertEquals(1.0, w.baseSpreadDegrees());
        assertEquals(1.5, w.movingSpreadPenaltyDegrees());
        assertEquals(4, w.fireRatePerSecond());
        assertEquals(12, w.magSize());
        assertEquals(36, w.reloadTimeTicks());
        assertEquals("minecraft:entity.firework_rocket.blast", w.fireSoundId());
        assertEquals("minecraft:item.crossbow.loading_middle", w.reloadSoundId());
        assertEquals("minecraft:block.dispenser.fail", w.dryFireSoundId());
        assertEquals(List.of(FireMode.SEMI), w.modes());
        assertEquals(FireMode.SEMI, w.defaultMode());
    }

    @Test
    void parsesMultipleModes() {
        String yaml = FULL_YAML.replace("modes: [semi]", "modes: [semi, auto]")
                               .replace("default_mode: semi", "default_mode: auto");
        Weapon w = load(yaml);
        assertEquals(List.of(FireMode.SEMI, FireMode.AUTO), w.modes());
        assertEquals(FireMode.AUTO, w.defaultMode());
    }

    @Test
    void throwsOnMissingId() {
        String yaml = "display_name: \"Nameless\"\n";
        assertThrows(IllegalArgumentException.class, () -> load(yaml));
    }

    @Test
    void throwsOnMissingModes() {
        String yaml = FULL_YAML.replace("  modes: [semi]\n", "");
        assertThrows(IllegalArgumentException.class, () -> load(yaml));
    }

    @Test
    void throwsOnEmptyModes() {
        String yaml = FULL_YAML.replace("modes: [semi]", "modes: []");
        assertThrows(IllegalArgumentException.class, () -> load(yaml));
    }

    @Test
    void throwsOnMissingDefaultMode() {
        String yaml = FULL_YAML.replace("  default_mode: semi\n", "");
        assertThrows(IllegalArgumentException.class, () -> load(yaml));
    }

    @Test
    void throwsWhenDefaultModeNotInModes() {
        String yaml = FULL_YAML.replace("default_mode: semi", "default_mode: auto");
        assertThrows(IllegalArgumentException.class, () -> load(yaml));
    }

    @Test
    void throwsOnUnknownModeName() {
        String yaml = FULL_YAML.replace("modes: [semi]", "modes: [bogus]");
        assertThrows(IllegalArgumentException.class, () -> load(yaml));
    }
}
```

- [ ] **Step 2: Update WeaponLoader to parse and validate the new fields**

Replace the entire contents of `WeaponLoader.java` with:

```java
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
```

- [ ] **Step 3: Run the tests**

Run: `./gradlew :plugin:test --tests "com.fooweapons.weapon.WeaponLoaderTest"`
Expected: 8 tests passing.

- [ ] **Step 4: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/weapon/Weapon.java plugin/src/main/java/com/fooweapons/weapon/WeaponLoader.java plugin/src/test/java/com/fooweapons/weapon/WeaponLoaderTest.java
git commit -m "feat: parse and validate fire modes from weapon YAML"
```

---

## Task 5: Backfill pistol_01.yml with modes

**Files:**
- Modify: `plugin/src/main/resources/weapons/pistol_01.yml`

- [ ] **Step 1: Add the two new fields under `fire:`**

Insert these two lines as the first two children of `fire:`:

```yaml
fire:
  modes: [semi]
  default_mode: semi
  damage: 4.0
  ...
```

The final file should look like:

```yaml
id: pistol_01
display_name: "Pistol"
custom_model_data: 1001
fire:
  modes: [semi]
  default_mode: semi
  damage: 4.0
  headshot_multiplier: 2.0
  range: 50.0
  falloff_start: 25.0
  falloff_end: 50.0
  rate_per_second: 4
  base_spread_degrees: 1.0
  moving_spread_penalty_degrees: 1.5
mag:
  size: 12
  reload_time_ticks: 36
sounds:
  fire: "minecraft:entity.firework_rocket.blast"
  reload: "minecraft:item.crossbow.loading_middle"
  dry_fire: "minecraft:block.dispenser.fail"
```

- [ ] **Step 2: Run all tests to confirm the YAML still loads**

Run: `./gradlew :plugin:test`
Expected: all tests pass.

- [ ] **Step 3: Commit**

```bash
git add plugin/src/main/resources/weapons/pistol_01.yml
git commit -m "feat: backfill pistol_01.yml with modes: [semi]"
```

---

## Task 6: PdcKeys gains a fire_mode key

**Files:**
- Modify: `plugin/src/main/java/com/fooweapons/item/PdcKeys.java`

- [ ] **Step 1: Add the new key**

Replace the file contents with:

```java
package com.fooweapons.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.persistence.PersistentDataType;

public final class PdcKeys {
    public final NamespacedKey weaponId;
    public final NamespacedKey ammo;
    public final NamespacedKey lastFiredMs;
    public final NamespacedKey fireMode;

    public PdcKeys(Plugin plugin) {
        this.weaponId = new NamespacedKey(plugin, "weapon_id");
        this.ammo = new NamespacedKey(plugin, "ammo");
        this.lastFiredMs = new NamespacedKey(plugin, "last_fired_ms");
        this.fireMode = new NamespacedKey(plugin, "fire_mode");
    }

    public PersistentDataType<String, String> stringType() {
        return PersistentDataType.STRING;
    }

    public PersistentDataType<Integer, Integer> intType() {
        return PersistentDataType.INTEGER;
    }

    public PersistentDataType<Long, Long> longType() {
        return PersistentDataType.LONG;
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :plugin:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/item/PdcKeys.java
git commit -m "feat: add fire_mode PDC key"
```

---

## Task 7: ItemState gets fire-mode read/write (TDD via MockBukkit)

**Files:**
- Create: `plugin/src/test/java/com/fooweapons/item/ItemStateTest.java`
- Modify: `plugin/src/main/java/com/fooweapons/item/ItemState.java`

MockBukkit-v1.21 3.133.2 is already on the test classpath ([plugin/build.gradle.kts:17](../../plugin/build.gradle.kts#L17)) and `MockBukkit.createMockPlugin()` is the standard way to mint a `Plugin` instance for tests that need `NamespacedKey`.

- [ ] **Step 1: Write the failing tests**

Create `plugin/src/test/java/com/fooweapons/item/ItemStateTest.java`:

```java
package com.fooweapons.item;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import com.fooweapons.fire.mode.FireMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemStateTest {
    private MockPlugin plugin;
    private PdcKeys keys;
    private ItemState state;
    private ItemStack stack;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        keys = new PdcKeys(plugin);
        state = new ItemState(keys);
        stack = new ItemStack(Material.CROSSBOW);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void roundTripsAmmoAndLastFired() {
        state.setAmmo(stack, 7);
        state.setLastFiredMs(stack, 12345L);
        assertEquals(7, state.getAmmo(stack));
        assertEquals(12345L, state.getLastFiredMs(stack));
    }

    @Test
    void roundTripsFireMode() {
        state.setFireMode(stack, FireMode.AUTO);
        assertEquals(FireMode.AUTO, state.getFireMode(stack, FireMode.SEMI));
    }

    @Test
    void missingTagReturnsFallbackAndPersistsIt() {
        // First call reads from a stack with no PDC tag — should return fallback.
        assertEquals(FireMode.SEMI, state.getFireMode(stack, FireMode.SEMI));
        // Second call with a DIFFERENT fallback should still see SEMI, proving the first
        // call wrote SEMI back to the PDC (lazy migration of legacy v0.1.0 items).
        assertEquals(FireMode.SEMI, state.getFireMode(stack, FireMode.AUTO));
    }

    @Test
    void invalidTagCoercesToFallbackAndPersistsIt() {
        // Plant a bogus value directly into the PDC.
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(keys.fireMode, keys.stringType(), "bogus_mode");
        stack.setItemMeta(meta);

        assertEquals(FireMode.SEMI, state.getFireMode(stack, FireMode.SEMI));
        // The bogus value should have been overwritten with SEMI.
        assertEquals(FireMode.SEMI, state.getFireMode(stack, FireMode.AUTO));
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `./gradlew :plugin:test --tests "com.fooweapons.item.ItemStateTest"`
Expected: FAIL — `getFireMode` / `setFireMode` methods do not exist on `ItemState`.

- [ ] **Step 3: Add `getFireMode` and `setFireMode` to ItemState**

Replace the file contents of `plugin/src/main/java/com/fooweapons/item/ItemState.java` with:

```java
package com.fooweapons.item;

import com.fooweapons.fire.mode.FireMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

public final class ItemState {
    private final PdcKeys keys;

    public ItemState(PdcKeys keys) {
        this.keys = keys;
    }

    public int getAmmo(ItemStack stack) {
        return read(stack, pdc -> pdc.getOrDefault(keys.ammo, keys.intType(), 0));
    }

    public void setAmmo(ItemStack stack, int ammo) {
        write(stack, pdc -> pdc.set(keys.ammo, keys.intType(), ammo));
    }

    public long getLastFiredMs(ItemStack stack) {
        return read(stack, pdc -> pdc.getOrDefault(keys.lastFiredMs, keys.longType(), 0L));
    }

    public void setLastFiredMs(ItemStack stack, long ms) {
        write(stack, pdc -> pdc.set(keys.lastFiredMs, keys.longType(), ms));
    }

    /**
     * Reads the fire mode from PDC. If the tag is missing or its value is not a known
     * FireMode, returns {@code fallback} and writes that value back to the PDC (lazy migration).
     */
    public FireMode getFireMode(ItemStack stack, FireMode fallback) {
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String raw = pdc.get(keys.fireMode, keys.stringType());
        if (raw == null) {
            pdc.set(keys.fireMode, keys.stringType(), fallback.yamlName());
            stack.setItemMeta(meta);
            return fallback;
        }
        try {
            return FireMode.fromYamlName(raw);
        } catch (IllegalArgumentException unknown) {
            pdc.set(keys.fireMode, keys.stringType(), fallback.yamlName());
            stack.setItemMeta(meta);
            return fallback;
        }
    }

    public void setFireMode(ItemStack stack, FireMode mode) {
        write(stack, pdc -> pdc.set(keys.fireMode, keys.stringType(), mode.yamlName()));
    }

    private <T> T read(ItemStack stack, java.util.function.Function<PersistentDataContainer, T> fn) {
        ItemMeta meta = stack.getItemMeta();
        return fn.apply(meta.getPersistentDataContainer());
    }

    private void write(ItemStack stack, java.util.function.Consumer<PersistentDataContainer> fn) {
        ItemMeta meta = stack.getItemMeta();
        fn.accept(meta.getPersistentDataContainer());
        stack.setItemMeta(meta);
    }
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `./gradlew :plugin:test --tests "com.fooweapons.item.ItemStateTest"`
Expected: 4 tests passing.

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/item/ItemState.java plugin/src/test/java/com/fooweapons/item/ItemStateTest.java
git commit -m "feat: ItemState read/write fire_mode with lazy fallback (TDD via MockBukkit)"
```

---

## Task 8: WeaponItemFactory writes initial fire_mode

**Files:**
- Modify: `plugin/src/main/java/com/fooweapons/item/WeaponItemFactory.java`

- [ ] **Step 1: Set fire_mode PDC tag when creating a weapon**

Replace the file contents with:

```java
package com.fooweapons.item;

import com.fooweapons.weapon.Weapon;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

public final class WeaponItemFactory {
    private final PdcKeys keys;

    public WeaponItemFactory(PdcKeys keys) {
        this.keys = keys;
    }

    public ItemStack create(Weapon weapon) {
        ItemStack stack = new ItemStack(Material.CROSSBOW);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(weapon.displayName()));
        meta.setCustomModelData(weapon.customModelData());
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.weaponId, keys.stringType(), weapon.id());
        pdc.set(keys.ammo, keys.intType(), weapon.magSize());
        pdc.set(keys.lastFiredMs, keys.longType(), 0L);
        pdc.set(keys.fireMode, keys.stringType(), weapon.defaultMode().yamlName());
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isWeapon(ItemStack stack) {
        if (stack == null || stack.getType() != Material.CROSSBOW) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(keys.weaponId, keys.stringType());
    }

    public String getWeaponId(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(keys.weaponId, keys.stringType());
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :plugin:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/item/WeaponItemFactory.java
git commit -m "feat: factory writes initial fire_mode from weapon defaultMode"
```

---

## Task 9: HudService reads mode from PDC

**Files:**
- Modify: `plugin/src/main/java/com/fooweapons/hud/HudService.java`

- [ ] **Step 1: Replace the hardcoded "SEMI" label**

Find this line (it's inside `tick()`, near the end of the Component construction):

```java
                .append(Component.text("SEMI", NamedTextColor.AQUA));
```

Replace with:

```java
                .append(Component.text(
                    state.getFireMode(stack, w.defaultMode()).label(),
                    NamedTextColor.AQUA));
```

- [ ] **Step 2: Build**

Run: `./gradlew :plugin:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run all tests**

Run: `./gradlew :plugin:test`
Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/hud/HudService.java
git commit -m "feat: HUD reads fire mode from PDC instead of hardcoded SEMI"
```

---

## Task 10: FireListener skips click-fire for AUTO mode

**Files:**
- Modify: `plugin/src/main/java/com/fooweapons/fire/FireListener.java`

- [ ] **Step 1: Skip when mode is AUTO**

Replace the file contents with:

```java
package com.fooweapons.fire;

import com.fooweapons.fire.mode.FireMode;
import com.fooweapons.item.ItemState;
import com.fooweapons.item.WeaponItemFactory;
import com.fooweapons.reload.ReloadListener;
import com.fooweapons.weapon.Weapon;
import com.fooweapons.weapon.WeaponRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import java.util.Optional;

public final class FireListener implements Listener {
    private final WeaponRegistry registry;
    private final WeaponItemFactory factory;
    private final FireService fireService;
    private final ReloadListener reloadListener;
    private final ItemState state;

    public FireListener(WeaponRegistry registry, WeaponItemFactory factory,
                        FireService fireService, ReloadListener reloadListener,
                        ItemState state) {
        this.registry = registry;
        this.factory = factory;
        this.fireService = fireService;
        this.reloadListener = reloadListener;
        this.state = state;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != null && event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (reloadListener.isReloading(player.getUniqueId())) return;
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!factory.isWeapon(stack)) return;
        event.setCancelled(true);
        String id = factory.getWeaponId(stack);
        Optional<Weapon> weapon = registry.get(id);
        if (weapon.isEmpty()) return;
        // AUTO-mode weapons fire from the arm-swing tracker, not from click events.
        if (state.getFireMode(stack, weapon.get().defaultMode()) == FireMode.AUTO) return;
        fireService.tryFire(player, stack, weapon.get());
    }
}
```

- [ ] **Step 2: Update FooWeaponsPlugin to pass ItemState into FireListener**

The current constructor call at [FooWeaponsPlugin.java:44](../../plugin/src/main/java/com/fooweapons/FooWeaponsPlugin.java#L44) is:

```java
            new FireListener(weapons, itemFactory, fireService, reloadListener), this);
```

Replace with:

```java
            new FireListener(weapons, itemFactory, fireService, reloadListener, itemState), this);
```

- [ ] **Step 3: Build**

Run: `./gradlew :plugin:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/fire/FireListener.java plugin/src/main/java/com/fooweapons/FooWeaponsPlugin.java
git commit -m "feat: FireListener skips click-fire when weapon is in AUTO mode"
```

---

## Task 11: SoundService gains click + denied-click

**Files:**
- Modify: `plugin/src/main/java/com/fooweapons/feedback/SoundService.java`

- [ ] **Step 1: Add the two new methods**

Replace the file contents with:

```java
package com.fooweapons.feedback;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

public final class SoundService {
    public void playFire(Player player, String soundId) {
        play(player, soundId, 1.0f, 1.0f);
    }

    public void playReload(Player player, String soundId) {
        play(player, soundId, 0.8f, 1.0f);
    }

    public void playDryFire(Player player, String soundId) {
        play(player, soundId, 0.5f, 1.2f);
    }

    /** Short positive click for a successful fire-mode toggle. */
    public void playClick(Player player) {
        play(player, "minecraft:ui.button.click", 0.6f, 1.4f);
    }

    /** Short negative click for an attempted toggle on a single-mode weapon. */
    public void playDeniedClick(Player player) {
        play(player, "minecraft:ui.button.click", 0.6f, 0.7f);
    }

    private void play(Player player, String soundId, float volume, float pitch) {
        Location loc = player.getLocation();
        player.getWorld().playSound(loc, soundId, SoundCategory.PLAYERS, volume, pitch);
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :plugin:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/feedback/SoundService.java
git commit -m "feat: add click + denied-click sounds for fire-mode toggle"
```

---

## Task 12: FireModeListener — Sneak+Q toggle handler

**Files:**
- Create: `plugin/src/main/java/com/fooweapons/fire/mode/FireModeListener.java`

- [ ] **Step 1: Create the listener**

```java
package com.fooweapons.fire.mode;

import com.fooweapons.feedback.SoundService;
import com.fooweapons.item.ItemState;
import com.fooweapons.item.WeaponItemFactory;
import com.fooweapons.weapon.Weapon;
import com.fooweapons.weapon.WeaponRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import java.util.Optional;

public final class FireModeListener implements Listener {
    private final WeaponRegistry registry;
    private final WeaponItemFactory factory;
    private final ItemState state;
    private final SoundService sounds;

    public FireModeListener(WeaponRegistry registry, WeaponItemFactory factory,
                            ItemState state, SoundService sounds) {
        this.registry = registry;
        this.factory = factory;
        this.state = state;
        this.sounds = sounds;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!factory.isWeapon(stack)) return;
        Optional<Weapon> w = registry.get(factory.getWeaponId(stack));
        if (w.isEmpty()) return;
        event.setCancelled(true);

        Weapon weapon = w.get();
        if (weapon.modes().size() <= 1) {
            sounds.playDeniedClick(player);
            return;
        }
        FireMode current = state.getFireMode(stack, weapon.defaultMode());
        FireMode next = FireModeCycle.next(current, weapon.modes());
        state.setFireMode(stack, next);
        sounds.playClick(player);
    }
}
```

- [ ] **Step 2: Register the listener in FooWeaponsPlugin**

In [FooWeaponsPlugin.java:42-44](../../plugin/src/main/java/com/fooweapons/FooWeaponsPlugin.java#L42-L44), find the block that creates `FireService` and registers `FireListener`, and add a `FireModeListener` registration right after it:

```java
        FireService fireService = new FireService(itemState, soundService);
        getServer().getPluginManager().registerEvents(
            new FireListener(weapons, itemFactory, fireService, reloadListener, itemState), this);
        getServer().getPluginManager().registerEvents(
            new com.fooweapons.fire.mode.FireModeListener(weapons, itemFactory, itemState, soundService), this);
```

- [ ] **Step 3: Build**

Run: `./gradlew :plugin:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/fire/mode/FireModeListener.java plugin/src/main/java/com/fooweapons/FooWeaponsPlugin.java
git commit -m "feat: Sneak+Q cycles fire mode on multi-mode weapons"
```

---

## Task 13: AutoFireTracker — per-tick scheduler

**Files:**
- Create: `plugin/src/main/java/com/fooweapons/fire/auto/AutoFireTracker.java`

- [ ] **Step 1: Create the tracker**

```java
package com.fooweapons.fire.auto;

import com.fooweapons.fire.FireRateCooldown;
import com.fooweapons.fire.FireService;
import com.fooweapons.fire.mode.FireMode;
import com.fooweapons.item.ItemState;
import com.fooweapons.item.WeaponItemFactory;
import com.fooweapons.reload.ReloadListener;
import com.fooweapons.weapon.Weapon;
import com.fooweapons.weapon.WeaponRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class AutoFireTracker {
    private static final long SWING_TIMEOUT_MS = 250L;

    private final Plugin plugin;
    private final WeaponRegistry registry;
    private final WeaponItemFactory factory;
    private final ItemState state;
    private final FireService fireService;
    private final ReloadListener reloadListener;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private BukkitTask task;

    public AutoFireTracker(Plugin plugin, WeaponRegistry registry, WeaponItemFactory factory,
                           ItemState state, FireService fireService, ReloadListener reloadListener) {
        this.plugin = plugin;
        this.registry = registry;
        this.factory = factory;
        this.state = state;
        this.fireService = fireService;
        this.reloadListener = reloadListener;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        entries.clear();
    }

    /** Called by ArmSwingListener whenever the player swings while holding an AUTO-mode weapon. */
    public void markSwing(UUID playerId, String weaponId, int slot) {
        entries.put(playerId, new Entry(weaponId, slot, System.currentTimeMillis()));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Entry> mapEntry = it.next();
            Entry entry = mapEntry.getValue();
            Player player = Bukkit.getPlayer(mapEntry.getKey());
            if (player == null) { it.remove(); continue; }
            if (now - entry.lastSwingMs > SWING_TIMEOUT_MS) { it.remove(); continue; }

            ItemStack stack = player.getInventory().getItem(entry.slot);
            if (!factory.isWeapon(stack) || !entry.weaponId.equals(factory.getWeaponId(stack))) {
                it.remove();
                continue;
            }
            if (player.getInventory().getHeldItemSlot() != entry.slot) {
                it.remove();
                continue;
            }
            Weapon weapon = registry.get(entry.weaponId).orElse(null);
            if (weapon == null) { it.remove(); continue; }
            if (state.getFireMode(stack, weapon.defaultMode()) != FireMode.AUTO) {
                it.remove();
                continue;
            }
            if (reloadListener.isReloading(player.getUniqueId())) continue;
            if (!FireRateCooldown.canFire(state.getLastFiredMs(stack), now, weapon.fireCooldownMillis())) continue;

            fireService.tryFire(player, stack, weapon);
        }
    }

    private record Entry(String weaponId, int slot, long lastSwingMs) {}
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :plugin:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/fire/auto/AutoFireTracker.java
git commit -m "feat: AutoFireTracker per-tick scheduler for held-LMB continuous fire"
```

---

## Task 14: ArmSwingListener feeds the tracker

**Files:**
- Create: `plugin/src/main/java/com/fooweapons/fire/auto/ArmSwingListener.java`

- [ ] **Step 1: Create the listener**

```java
package com.fooweapons.fire.auto;

import com.fooweapons.fire.mode.FireMode;
import com.fooweapons.item.ItemState;
import com.fooweapons.item.WeaponItemFactory;
import com.fooweapons.weapon.Weapon;
import com.fooweapons.weapon.WeaponRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.inventory.ItemStack;
import java.util.Optional;

public final class ArmSwingListener implements Listener {
    private final WeaponRegistry registry;
    private final WeaponItemFactory factory;
    private final ItemState state;
    private final AutoFireTracker tracker;

    public ArmSwingListener(WeaponRegistry registry, WeaponItemFactory factory,
                            ItemState state, AutoFireTracker tracker) {
        this.registry = registry;
        this.factory = factory;
        this.state = state;
        this.tracker = tracker;
    }

    @EventHandler
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!factory.isWeapon(stack)) return;
        Optional<Weapon> w = registry.get(factory.getWeaponId(stack));
        if (w.isEmpty()) return;
        if (state.getFireMode(stack, w.get().defaultMode()) != FireMode.AUTO) return;
        tracker.markSwing(
            player.getUniqueId(),
            w.get().id(),
            player.getInventory().getHeldItemSlot()
        );
    }
}
```

- [ ] **Step 2: Wire the tracker + listener into FooWeaponsPlugin**

Add a field near the other service fields at the top of [FooWeaponsPlugin.java](../../plugin/src/main/java/com/fooweapons/FooWeaponsPlugin.java):

```java
    private com.fooweapons.fire.auto.AutoFireTracker autoFireTracker;
```

In `onEnable()`, after the `FireService fireService = ...` line, add:

```java
        this.autoFireTracker = new com.fooweapons.fire.auto.AutoFireTracker(
            this, weapons, itemFactory, itemState, fireService, reloadListener);
        autoFireTracker.start();
        getServer().getPluginManager().registerEvents(
            new com.fooweapons.fire.auto.ArmSwingListener(weapons, itemFactory, itemState, autoFireTracker), this);
```

In `onDisable()`, add:

```java
        if (autoFireTracker != null) autoFireTracker.stop();
```

- [ ] **Step 3: Build**

Run: `./gradlew :plugin:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run all tests**

Run: `./gradlew :plugin:test`
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/fire/auto/ArmSwingListener.java plugin/src/main/java/com/fooweapons/FooWeaponsPlugin.java
git commit -m "feat: ArmSwingListener feeds AutoFireTracker; wired in plugin lifecycle"
```

---

## Task 15: rifle_01.yml + load it on plugin enable

**Files:**
- Create: `plugin/src/main/resources/weapons/rifle_01.yml`
- Modify: `plugin/src/main/java/com/fooweapons/FooWeaponsPlugin.java`

- [ ] **Step 1: Create the rifle YAML**

Create `plugin/src/main/resources/weapons/rifle_01.yml`:

```yaml
id: rifle_01
display_name: "Assault Rifle"
custom_model_data: 1003
fire:
  modes: [semi, auto]
  default_mode: semi
  damage: 5.5
  headshot_multiplier: 2.0
  range: 80.0
  falloff_start: 40.0
  falloff_end: 80.0
  rate_per_second: 10
  base_spread_degrees: 1.5
  moving_spread_penalty_degrees: 2.0
mag:
  size: 30
  reload_time_ticks: 50
sounds:
  fire: "minecraft:entity.firework_rocket.blast"
  reload: "minecraft:item.crossbow.loading_middle"
  dry_fire: "minecraft:block.dispenser.fail"
```

- [ ] **Step 2: Load it on plugin enable**

In [FooWeaponsPlugin.java:30](../../plugin/src/main/java/com/fooweapons/FooWeaponsPlugin.java#L30), change:

```java
            weapons.loadFromClasspath(getClassLoader(), "weapons/pistol_01.yml");
```

to:

```java
            weapons.loadFromClasspath(getClassLoader(),
                "weapons/pistol_01.yml",
                "weapons/rifle_01.yml");
```

- [ ] **Step 3: Build the jar and confirm both weapons load**

Run: `./gradlew :plugin:jar`
Expected: BUILD SUCCESSFUL. (Runtime weapon-count check happens on the server in the final smoke test.)

- [ ] **Step 4: Commit**

```bash
git add plugin/src/main/resources/weapons/rifle_01.yml plugin/src/main/java/com/fooweapons/FooWeaponsPlugin.java
git commit -m "feat: add rifle_01 weapon definition (semi/auto, 30-round mag)"
```

---

## Task 16: rifle_01 model JSON

**Files:**
- Create: `resourcepack/assets/fooweapons/models/item/rifle_01.json`

- [ ] **Step 1: Hand-write the cuboid model**

Create the file with a longer-silhouette rifle: grip + lower frame + magazine + upper receiver + barrel + iron-sight nub. Same modeling discipline as pistol_01.

```json
{
  "credit": "fooWeapons original model",
  "texture_size": [16, 16],
  "textures": {
    "0": "fooweapons:item/rifle_01",
    "particle": "fooweapons:item/rifle_01"
  },
  "elements": [
    {
      "name": "grip",
      "from": [6, 0, 7.5],
      "to": [8, 5, 8.5],
      "faces": {
        "north": {"uv": [0, 0, 2, 5], "texture": "#0"},
        "east":  {"uv": [0, 0, 1, 5], "texture": "#0"},
        "south": {"uv": [0, 0, 2, 5], "texture": "#0"},
        "west":  {"uv": [0, 0, 1, 5], "texture": "#0"},
        "up":    {"uv": [0, 0, 2, 1], "texture": "#0"},
        "down":  {"uv": [0, 0, 2, 1], "texture": "#0"}
      }
    },
    {
      "name": "magazine",
      "from": [7, 1, 7.5],
      "to": [9, 5, 8.5],
      "faces": {
        "north": {"uv": [0, 0, 2, 4], "texture": "#0"},
        "east":  {"uv": [0, 0, 1, 4], "texture": "#0"},
        "south": {"uv": [0, 0, 2, 4], "texture": "#0"},
        "west":  {"uv": [0, 0, 1, 4], "texture": "#0"},
        "up":    {"uv": [0, 0, 2, 1], "texture": "#0"},
        "down":  {"uv": [0, 0, 2, 1], "texture": "#0"}
      }
    },
    {
      "name": "lower_frame",
      "from": [5, 5, 7.5],
      "to": [12, 7, 8.5],
      "faces": {
        "north": {"uv": [0, 0, 7, 2], "texture": "#0"},
        "east":  {"uv": [0, 0, 1, 2], "texture": "#0"},
        "south": {"uv": [0, 0, 7, 2], "texture": "#0"},
        "west":  {"uv": [0, 0, 1, 2], "texture": "#0"},
        "up":    {"uv": [0, 0, 7, 1], "texture": "#0"},
        "down":  {"uv": [0, 0, 7, 1], "texture": "#0"}
      }
    },
    {
      "name": "upper_receiver",
      "from": [5, 7, 7.75],
      "to": [13, 8.5, 8.25],
      "faces": {
        "north": {"uv": [0, 0, 8, 2], "texture": "#0"},
        "east":  {"uv": [0, 0, 1, 2], "texture": "#0"},
        "south": {"uv": [0, 0, 8, 2], "texture": "#0"},
        "west":  {"uv": [0, 0, 1, 2], "texture": "#0"},
        "up":    {"uv": [0, 0, 8, 1], "texture": "#0"},
        "down":  {"uv": [0, 0, 8, 1], "texture": "#0"}
      }
    },
    {
      "name": "barrel",
      "from": [13, 7.25, 7.875],
      "to": [16, 8.25, 8.125],
      "faces": {
        "north": {"uv": [0, 0, 3, 1], "texture": "#0"},
        "east":  {"uv": [0, 0, 1, 1], "texture": "#0"},
        "south": {"uv": [0, 0, 3, 1], "texture": "#0"},
        "west":  {"uv": [0, 0, 1, 1], "texture": "#0"},
        "up":    {"uv": [0, 0, 3, 1], "texture": "#0"},
        "down":  {"uv": [0, 0, 3, 1], "texture": "#0"}
      }
    },
    {
      "name": "front_sight",
      "from": [13, 8.5, 7.875],
      "to": [13.5, 9.5, 8.125],
      "faces": {
        "north": {"uv": [0, 0, 1, 1], "texture": "#0"},
        "east":  {"uv": [0, 0, 1, 1], "texture": "#0"},
        "south": {"uv": [0, 0, 1, 1], "texture": "#0"},
        "west":  {"uv": [0, 0, 1, 1], "texture": "#0"},
        "up":    {"uv": [0, 0, 1, 1], "texture": "#0"},
        "down":  {"uv": [0, 0, 1, 1], "texture": "#0"}
      }
    },
    {
      "name": "trigger_guard",
      "from": [6.5, 4, 7.75],
      "to": [8, 5, 8.25],
      "faces": {
        "north": {"uv": [0, 0, 2, 1], "texture": "#0"},
        "east":  {"uv": [0, 0, 1, 1], "texture": "#0"},
        "south": {"uv": [0, 0, 2, 1], "texture": "#0"},
        "west":  {"uv": [0, 0, 1, 1], "texture": "#0"},
        "up":    {"uv": [0, 0, 2, 1], "texture": "#0"},
        "down":  {"uv": [0, 0, 2, 1], "texture": "#0"}
      }
    }
  ],
  "display": {
    "thirdperson_righthand": {
      "rotation": [0, 90, 0],
      "translation": [0, 2, 0],
      "scale": [0.75, 0.75, 0.75]
    },
    "thirdperson_lefthand": {
      "rotation": [0, 270, 0],
      "translation": [0, 2, 0],
      "scale": [0.75, 0.75, 0.75]
    },
    "firstperson_righthand": {
      "rotation": [0, 90, 0],
      "translation": [1, 3, 1.5],
      "scale": [1.0, 1.0, 1.0]
    },
    "firstperson_lefthand": {
      "rotation": [0, 270, 0],
      "translation": [1, 3, 1.5],
      "scale": [1.0, 1.0, 1.0]
    },
    "gui": {
      "rotation": [30, 45, 0],
      "translation": [-1, 0, 0],
      "scale": [1.0, 1.0, 1.0]
    },
    "ground": {
      "rotation": [0, 0, 0],
      "translation": [0, 4, 0],
      "scale": [0.5, 0.5, 0.5]
    },
    "fixed": {
      "rotation": [0, 0, 0],
      "translation": [0, 0, 0],
      "scale": [1.0, 1.0, 1.0]
    }
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add resourcepack/assets/fooweapons/models/item/rifle_01.json
git commit -m "feat: hand-written cuboid model for rifle_01"
```

---

## Task 17: rifle_01 texture (extend the generator)

**Files:**
- Modify: `tools/generate_textures.py`
- Create: `resourcepack/assets/fooweapons/textures/item/rifle_01.png` (run-time output)

- [ ] **Step 1: Extend the generator to emit rifle_01**

Replace the file contents with:

```python
# tools/generate_textures.py
# Generates 16x16 procedural textures for fooWeapons.
# Run: python tools/generate_textures.py
from PIL import Image

DARK = (45, 45, 50, 255)
MID = (75, 75, 80, 255)
LIGHT = (110, 110, 115, 255)
HIGHLIGHT = (160, 160, 165, 255)


def pistol():
    img = Image.new("RGBA", (16, 16), DARK)
    px = img.load()
    for x in range(16):
        for y in range(16):
            if (x + y) % 4 == 0:
                px[x, y] = MID
            if y == 7 or y == 8:
                px[x, y] = LIGHT
            if (x == 0 or x == 15) and 4 <= y <= 11:
                px[x, y] = HIGHLIGHT
    return img


def rifle():
    """Slightly lighter base, longer horizontal banding to suggest a rifle silhouette."""
    img = Image.new("RGBA", (16, 16), MID)
    px = img.load()
    for x in range(16):
        for y in range(16):
            if (x * 2 + y) % 5 == 0:
                px[x, y] = DARK
            if y == 6 or y == 9:
                px[x, y] = LIGHT
            if y == 0 or y == 15:
                px[x, y] = HIGHLIGHT
    return img


if __name__ == "__main__":
    pistol().save("resourcepack/assets/fooweapons/textures/item/pistol_01.png")
    print("Wrote pistol_01.png")
    rifle().save("resourcepack/assets/fooweapons/textures/item/rifle_01.png")
    print("Wrote rifle_01.png")
```

- [ ] **Step 2: Run the generator**

Run: `python tools/generate_textures.py`
Expected output:
```
Wrote pistol_01.png
Wrote rifle_01.png
```

(Requires `pip install Pillow` — already documented in CLAUDE.md.)

- [ ] **Step 3: Confirm the file exists**

Run (PowerShell): `Test-Path resourcepack/assets/fooweapons/textures/item/rifle_01.png`
Expected: `True`.

- [ ] **Step 4: Commit**

```bash
git add tools/generate_textures.py resourcepack/assets/fooweapons/textures/item/rifle_01.png
git commit -m "feat: generate procedural texture for rifle_01"
```

---

## Task 18: Override crossbow item model to include rifle_01

**Files:**
- Modify: `resourcepack/assets/minecraft/items/crossbow.json`

- [ ] **Step 1: Add the rifle_01 case**

Replace the file contents with:

```json
{
  "model": {
    "type": "minecraft:select",
    "property": "minecraft:custom_model_data",
    "cases": [
      {
        "when": "1001",
        "model": {
          "type": "minecraft:model",
          "model": "fooweapons:item/pistol_01"
        }
      },
      {
        "when": "1003",
        "model": {
          "type": "minecraft:model",
          "model": "fooweapons:item/rifle_01"
        }
      }
    ],
    "fallback": {
      "type": "minecraft:model",
      "model": "minecraft:item/crossbow"
    }
  }
}
```

- [ ] **Step 2: Build the dist (plugin jar + resource pack zip)**

Run: `./gradlew dist`
Expected: BUILD SUCCESSFUL. Two artifacts written:
- `plugin/build/libs/nf_fooweapons-0.1.0.jar`
- `build/dist/fooWeapons-resourcepack-0.1.0.zip`

(The version is still `0.1.0` from v0.1.0. Bumping for release happens at the promotion step Greg drives, not in this slice.)

- [ ] **Step 3: Commit**

```bash
git add resourcepack/assets/minecraft/items/crossbow.json
git commit -m "feat: resource pack maps CMD 1003 to rifle_01 model"
```

---

## Task 19: Update PROGRESS.md

**Files:**
- Modify: `PROGRESS.md`

- [ ] **Step 1: Check off the slice's items under Plan 2**

In the Plan 2 section, change these three lines:
- `- [ ] Assault rifle (\`rifle_01\`) — selectable semi/auto, 30 rounds` → `[x]`
- `- [ ] Automatic fire (continuous-fire polling while LMB held)` → `[x]`
- `- [ ] Fire mode toggle on rifles (Sneak+Q) — replaces hardcoded "SEMI" label in HudService` → `[x]`

Also add a short Slice A header at the top of Plan 2 noting completion date:

After the line `## Plan 2 — Deferred (no go yet)` (and before `### New weapons`), insert:

```markdown
### Plan 2 Slice A — Auto-fire + fire-mode toggle + rifle_01 (shipped 2026-05-15)

Smoke-test verified on Greg's GPortal Paper 1.21.11 server.

```

Also update the section title — change `## Plan 2 — Deferred (no go yet)` to `## Plan 2 — In Progress`.

- [ ] **Step 2: Commit**

```bash
git add PROGRESS.md
git commit -m "docs: mark Plan 2 Slice A items complete in PROGRESS.md"
```

---

## Task 20: Final build + manual smoke test

**Files:** none modified — this task is verification only.

- [ ] **Step 1: Clean build everything**

Run: `./gradlew clean dist`
Expected: BUILD SUCCESSFUL. Confirm both artifacts exist:
- `plugin/build/libs/nf_fooweapons-0.1.0.jar`
- `build/dist/fooWeapons-resourcepack-0.1.0.zip`

- [ ] **Step 2: Run the full test suite**

Run: `./gradlew :plugin:test`
Expected: all tests pass — at minimum the WeaponLoader (8 tests), FireModeCycle (5 tests), FireRateCooldown, DamageCalculator, SpreadCalculator suites.

- [ ] **Step 3: Hand off to Greg for manual smoke test**

The manual smoke checklist from the spec §11.4 is reproduced here for the executor's reference. **The executor does not run this** — they hand the built artifacts to Greg, who runs the smoke test on the GPortal server:

- [ ] `/fooweapons give rifle_01` — receives a rifle with HUD showing `SEMI`.
- [ ] Click LMB once → single shot, ammo decrements by 1, HUD updates.
- [ ] Sneak+Q → click sound, HUD now shows `AUTO`.
- [ ] Hold LMB → continuous fire at ~10 rounds/sec until mag empty.
- [ ] Reload (`F`) → reload lock works, fire stops, HUD shows progress.
- [ ] Drop rifle, pick it back up → HUD still shows last-selected mode.
- [ ] Die holding rifle, respawn, pick it back up → mode preserved.
- [ ] `/fooweapons give pistol_01` — Sneak+Q plays denied-click; pistol stays SEMI.
- [ ] Both weapons in different hotbar slots → switching hotbar mid-burst stops the burst correctly.

If any step fails, file the regression as a new issue and do NOT promote the slice forward.

- [ ] **Step 4: Report completion to Greg**

Report: "Plan 2 Slice A implementation complete on `dev`. All Java tests green, dist build clean. Manual smoke test on GPortal pending — please run the checklist in Task 20 step 3 and report back."

Do NOT push to `beta` or `release`. Per the project's three-branch rule, promotions require Greg's explicit ask.
