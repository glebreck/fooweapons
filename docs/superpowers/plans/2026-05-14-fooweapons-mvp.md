# fooWeapons MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a working Paper 1.21.11 plugin + resource pack that lets a player on a vanilla Java 1.21.11 client be given a custom "Pistol" item, fire it (semi-auto, hitscan damage), reload it via the `F` key, hear sounds, and see ammo on the action bar — all delivered to a GPortal-hosted server.

**Architecture:** Two artifacts in one Gradle project. The `plugin/` subproject builds a Paper jar that owns all behavior. The `resourcepack/` directory is a static asset bundle zipped at build time. Plugin and pack share weapon IDs and `CustomModelData` integers as the only contract between them. Crossbow is the carrier item; `CustomModelData` selects the custom model on 1.21.4+'s `items/` definition system.

**Tech Stack:**
- Java 21 (required by Paper 1.21.11)
- Paper API `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`
- Gradle Kotlin DSL
- JUnit 5 for pure-logic tests
- MockBukkit 1.21 for Bukkit-touching tests where feasible (skip if version incompatibility, fall back to manual)
- SnakeYAML (bundled by Paper, no extra dep needed)

**Note on environment:** Working directory is `d:\MyStuff\Minecraft\fooWeapons`. Shell is PowerShell on Windows; Gradle wrapper invocation is `.\gradlew.bat <task>` in PowerShell or `./gradlew <task>` via Bash. Examples below use the Bash form for brevity — translate as needed.

---

## File Structure

```
fooWeapons/
├── settings.gradle.kts                  # Gradle multi-project root
├── build.gradle.kts                     # Root build (mostly empty, conventions)
├── gradle.properties                    # Project-wide props
├── gradlew / gradlew.bat / gradle/      # Gradle wrapper (generated)
├── plugin/
│   ├── build.gradle.kts                 # Plugin build config
│   ├── src/main/java/com/fooweapons/
│   │   ├── FooWeaponsPlugin.java        # JavaPlugin entry point
│   │   ├── weapon/
│   │   │   ├── Weapon.java              # Immutable weapon record
│   │   │   ├── WeaponLoader.java        # YAML → Weapon
│   │   │   └── WeaponRegistry.java      # ID → Weapon lookup
│   │   ├── item/
│   │   │   ├── PdcKeys.java             # NamespacedKey constants
│   │   │   ├── WeaponItemFactory.java   # Weapon → ItemStack, recognition
│   │   │   └── ItemState.java           # PDC ammo/last-fired read/write
│   │   ├── fire/
│   │   │   ├── FireListener.java        # PlayerInteractEvent handler
│   │   │   ├── FireService.java         # Fire orchestration
│   │   │   ├── SpreadCalculator.java    # Pure math: spread degrees → direction
│   │   │   ├── DamageCalculator.java    # Pure math: distance falloff
│   │   │   ├── FireRateCooldown.java    # Pure logic: cooldown check
│   │   │   └── Hitscan.java             # Raycast wrapper around world.rayTrace
│   │   ├── reload/
│   │   │   └── ReloadListener.java      # PlayerSwapHandItemsEvent handler
│   │   ├── hud/
│   │   │   └── HudService.java          # Action bar updates
│   │   ├── feedback/
│   │   │   └── SoundService.java        # Plays gun sounds
│   │   └── command/
│   │       └── GiveCommand.java         # /fooweapons give <id>
│   ├── src/main/resources/
│   │   ├── paper-plugin.yml             # Plugin descriptor
│   │   ├── plugin.yml                   # Legacy descriptor (for /command registration)
│   │   └── weapons/
│   │       └── pistol_01.yml            # Default pistol definition
│   └── src/test/java/com/fooweapons/
│       ├── fire/
│       │   ├── DamageCalculatorTest.java
│       │   ├── SpreadCalculatorTest.java
│       │   └── FireRateCooldownTest.java
│       └── weapon/
│           └── WeaponLoaderTest.java
└── resourcepack/
    ├── pack.mcmeta
    └── assets/
        ├── minecraft/items/crossbow.json   # Override: select model by CMD
        └── fooweapons/
            ├── models/item/pistol_01.json   # Cuboid pistol geometry
            ├── textures/item/pistol_01.png  # Procedural texture
            └── sounds.json                  # Custom sound event registrations
```

---

## Phase 1: Project Scaffolding

### Task 1: Initialize Gradle project structure

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `plugin/build.gradle.kts`

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
rootProject.name = "fooWeapons"
include("plugin")
```

- [ ] **Step 2: Create root `build.gradle.kts`**

```kotlin
plugins {
    java
}

allprojects {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
```

- [ ] **Step 3: Create `gradle.properties`**

```
group=com.fooweapons
version=0.1.0
```

- [ ] **Step 4: Create `plugin/build.gradle.kts`**

```kotlin
plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.21:4.0.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version)
    }
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
```

> If MockBukkit version `4.0.0` is unavailable for 1.21.11, search Maven Central for the latest `MockBukkit-v1.21:*` and substitute. If no compatible version exists, remove MockBukkit and skip Bukkit-touching unit tests — they become manual playtest items.

- [ ] **Step 5: Generate Gradle wrapper**

Run:
```bash
gradle wrapper --gradle-version 8.10
```

Expected: `gradlew`, `gradlew.bat`, and `gradle/wrapper/` directory created.

If `gradle` is not on PATH, install Gradle 8.10+ first or use an existing wrapper from another project as a template.

- [ ] **Step 6: Verify the empty project builds**

Run:
```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. No source code yet, so it just confirms Gradle config is valid.

- [ ] **Step 7: Commit**

```bash
git init
git add settings.gradle.kts build.gradle.kts gradle.properties plugin/build.gradle.kts gradlew gradlew.bat gradle/
echo -e "build/\n.gradle/\n.idea/\n*.iml\n.vscode/" > .gitignore
git add .gitignore
git commit -m "chore: initialize Gradle project for Paper 1.21.11 plugin"
```

### Task 2: Plugin descriptor and main class

**Files:**
- Create: `plugin/src/main/resources/paper-plugin.yml`
- Create: `plugin/src/main/resources/plugin.yml`
- Create: `plugin/src/main/java/com/fooweapons/FooWeaponsPlugin.java`

- [ ] **Step 1: Create `paper-plugin.yml`**

```yaml
name: fooWeapons
version: '${version}'
main: com.fooweapons.FooWeaponsPlugin
api-version: '1.21'
authors: [fooWeapons-dev]
description: Modern firearms plugin for Paper 1.21.11
```

- [ ] **Step 2: Create `plugin.yml` (registers commands)**

```yaml
name: fooWeapons
version: '${version}'
main: com.fooweapons.FooWeaponsPlugin
api-version: '1.21'
commands:
  fooweapons:
    description: fooWeapons admin commands
    usage: /<command> give <weapon_id>
    aliases: [foo]
    permission: fooweapons.admin
```

- [ ] **Step 3: Create main class**

```java
package com.fooweapons;

import org.bukkit.plugin.java.JavaPlugin;

public final class FooWeaponsPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("fooWeapons enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("fooWeapons disabled.");
    }
}
```

- [ ] **Step 4: Build and verify jar is produced**

Run:
```bash
./gradlew :plugin:jar
```

Expected: jar at `plugin/build/libs/plugin-0.1.0.jar`. Open it (it's a zip) and confirm `paper-plugin.yml`, `plugin.yml`, and `com/fooweapons/FooWeaponsPlugin.class` are inside.

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/resources/ plugin/src/main/java/
git commit -m "feat: scaffold Paper plugin entry point with descriptors"
```

---

## Phase 2: Weapon Definitions

### Task 3: `Weapon` record

**Files:**
- Create: `plugin/src/main/java/com/fooweapons/weapon/Weapon.java`

- [ ] **Step 1: Write the Weapon record**

```java
package com.fooweapons.weapon;

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
    String dryFireSoundId
) {
    public long fireCooldownMillis() {
        return 1000L / Math.max(1, fireRatePerSecond);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/weapon/Weapon.java
git commit -m "feat: add Weapon record"
```

### Task 4: Default `pistol_01.yml`

**Files:**
- Create: `plugin/src/main/resources/weapons/pistol_01.yml`

- [ ] **Step 1: Write the YAML**

```yaml
id: pistol_01
display_name: "Pistol"
custom_model_data: 1001
fire:
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

> Sounds use vanilla event IDs initially. Custom sounds get registered later in Phase 10.

- [ ] **Step 2: Commit**

```bash
git add plugin/src/main/resources/weapons/pistol_01.yml
git commit -m "feat: add pistol_01 default weapon definition"
```

### Task 5: `WeaponLoader` (test-first)

**Files:**
- Test: `plugin/src/test/java/com/fooweapons/weapon/WeaponLoaderTest.java`
- Create: `plugin/src/main/java/com/fooweapons/weapon/WeaponLoader.java`

- [ ] **Step 1: Write the failing test**

```java
package com.fooweapons.weapon;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class WeaponLoaderTest {
    @Test
    void loadsAllFieldsFromYaml() {
        String yaml = """
            id: pistol_01
            display_name: "Pistol"
            custom_model_data: 1001
            fire:
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
        Weapon w = new WeaponLoader().load(
            new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        assertEquals("pistol_01", w.id());
        assertEquals("Pistol", w.displayName());
        assertEquals(1001, w.customModelData());
        assertEquals(4.0, w.damage());
        assertEquals(12, w.magSize());
        assertEquals(36, w.reloadTimeTicks());
        assertEquals("minecraft:entity.firework_rocket.blast", w.fireSoundId());
    }

    @Test
    void throwsOnMissingId() {
        String yaml = "display_name: \"Nameless\"\n";
        WeaponLoader loader = new WeaponLoader();
        assertThrows(IllegalArgumentException.class,
            () -> loader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :plugin:test --tests com.fooweapons.weapon.WeaponLoaderTest
```

Expected: FAIL (compile error — `WeaponLoader` doesn't exist).

- [ ] **Step 3: Implement `WeaponLoader`**

```java
package com.fooweapons.weapon;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

public final class WeaponLoader {
    private final Yaml yaml = new Yaml();

    public Weapon load(InputStream input) {
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
        return Objects.requireNonNull((Number) map.get(key),
            () -> "Missing numeric field: " + key);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :plugin:test --tests com.fooweapons.weapon.WeaponLoaderTest
```

Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/weapon/WeaponLoader.java \
        plugin/src/test/java/com/fooweapons/weapon/WeaponLoaderTest.java
git commit -m "feat: load Weapon from YAML"
```

### Task 6: `WeaponRegistry`

**Files:**
- Create: `plugin/src/main/java/com/fooweapons/weapon/WeaponRegistry.java`
- Modify: `plugin/src/main/java/com/fooweapons/FooWeaponsPlugin.java`

- [ ] **Step 1: Implement registry**

```java
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
        for (String path : resourcePaths) {
            try (InputStream in = cl.getResourceAsStream(path)) {
                if (in == null) throw new IOException("Resource not found: " + path);
                Weapon w = loader.load(in);
                byId.put(w.id(), w);
            }
        }
    }

    public Optional<Weapon> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public int size() {
        return byId.size();
    }
}
```

- [ ] **Step 2: Wire into plugin lifecycle**

Replace `FooWeaponsPlugin.java` contents:

```java
package com.fooweapons;

import com.fooweapons.weapon.WeaponRegistry;
import org.bukkit.plugin.java.JavaPlugin;

public final class FooWeaponsPlugin extends JavaPlugin {
    private final WeaponRegistry weapons = new WeaponRegistry();

    @Override
    public void onEnable() {
        try {
            weapons.loadFromClasspath(getClassLoader(), "weapons/pistol_01.yml");
            getLogger().info("Loaded " + weapons.size() + " weapon(s).");
        } catch (Exception e) {
            getLogger().severe("Failed to load weapons: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("fooWeapons enabled.");
    }

    public WeaponRegistry weapons() {
        return weapons;
    }
}
```

- [ ] **Step 3: Build to verify compilation**

```bash
./gradlew :plugin:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/
git commit -m "feat: weapon registry loaded on plugin enable"
```

---

## Phase 3: Item Creation and Recognition

### Task 7: `PdcKeys` constants

**Files:**
- Create: `plugin/src/main/java/com/fooweapons/item/PdcKeys.java`

- [ ] **Step 1: Write the constants**

```java
package com.fooweapons.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.persistence.PersistentDataType;

public final class PdcKeys {
    public final NamespacedKey weaponId;
    public final NamespacedKey ammo;
    public final NamespacedKey lastFiredMs;

    public PdcKeys(Plugin plugin) {
        this.weaponId = new NamespacedKey(plugin, "weapon_id");
        this.ammo = new NamespacedKey(plugin, "ammo");
        this.lastFiredMs = new NamespacedKey(plugin, "last_fired_ms");
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

- [ ] **Step 2: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/item/PdcKeys.java
git commit -m "feat: define PDC keys for weapon items"
```

### Task 8: `WeaponItemFactory`

**Files:**
- Create: `plugin/src/main/java/com/fooweapons/item/WeaponItemFactory.java`

- [ ] **Step 1: Implement factory**

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

> Tests for this require MockBukkit or a running server. If MockBukkit is available, write integration tests; otherwise, this is validated via the manual smoke test in Phase 11.

- [ ] **Step 2: Build to verify compilation**

```bash
./gradlew :plugin:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/item/WeaponItemFactory.java
git commit -m "feat: create and recognize weapon items via crossbow + PDC"
```

### Task 9: `ItemState` for runtime fields

**Files:**
- Create: `plugin/src/main/java/com/fooweapons/item/ItemState.java`

- [ ] **Step 1: Implement state accessor**

```java
package com.fooweapons.item;

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

- [ ] **Step 2: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/item/ItemState.java
git commit -m "feat: read/write per-item ammo and cooldown state via PDC"
```

---

## Phase 4: Pure Fire Logic (TDD)

### Task 10: `FireRateCooldown`

**Files:**
- Test: `plugin/src/test/java/com/fooweapons/fire/FireRateCooldownTest.java`
- Create: `plugin/src/main/java/com/fooweapons/fire/FireRateCooldown.java`

- [ ] **Step 1: Write failing test**

```java
package com.fooweapons.fire;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FireRateCooldownTest {
    @Test
    void canFireWhenNeverFired() {
        assertTrue(FireRateCooldown.canFire(0L, 1000L, 250L));
    }

    @Test
    void cannotFireDuringCooldown() {
        assertFalse(FireRateCooldown.canFire(1000L, 1100L, 250L));
    }

    @Test
    void canFireAfterCooldown() {
        assertTrue(FireRateCooldown.canFire(1000L, 1300L, 250L));
    }

    @Test
    void canFireAtExactCooldownBoundary() {
        assertTrue(FireRateCooldown.canFire(1000L, 1250L, 250L));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :plugin:test --tests com.fooweapons.fire.FireRateCooldownTest
```

Expected: FAIL (compile error).

- [ ] **Step 3: Implement**

```java
package com.fooweapons.fire;

public final class FireRateCooldown {
    private FireRateCooldown() {}

    public static boolean canFire(long lastFiredMs, long nowMs, long cooldownMs) {
        return (nowMs - lastFiredMs) >= cooldownMs;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :plugin:test --tests com.fooweapons.fire.FireRateCooldownTest
```

Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/fire/FireRateCooldown.java \
        plugin/src/test/java/com/fooweapons/fire/FireRateCooldownTest.java
git commit -m "feat: fire rate cooldown check"
```

### Task 11: `DamageCalculator`

**Files:**
- Test: `plugin/src/test/java/com/fooweapons/fire/DamageCalculatorTest.java`
- Create: `plugin/src/main/java/com/fooweapons/fire/DamageCalculator.java`

- [ ] **Step 1: Write failing test**

```java
package com.fooweapons.fire;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DamageCalculatorTest {
    @Test
    void fullDamageWithinFalloffStart() {
        double d = DamageCalculator.compute(10.0, 5.0, 25.0, 50.0, 10.0, 2.0, false);
        assertEquals(10.0, d, 0.0001);
    }

    @Test
    void halfDamageAtMidpointOfFalloff() {
        double d = DamageCalculator.compute(10.0, 30.0, 20.0, 40.0, 0.0, 2.0, false);
        assertEquals(5.0, d, 0.0001);
    }

    @Test
    void minDamageBeyondFalloffEnd() {
        double d = DamageCalculator.compute(10.0, 60.0, 20.0, 40.0, 1.0, 2.0, false);
        assertEquals(1.0, d, 0.0001);
    }

    @Test
    void headshotMultiplierApplies() {
        double d = DamageCalculator.compute(10.0, 5.0, 25.0, 50.0, 10.0, 2.5, true);
        assertEquals(25.0, d, 0.0001);
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

```bash
./gradlew :plugin:test --tests com.fooweapons.fire.DamageCalculatorTest
```

Expected: FAIL.

- [ ] **Step 3: Implement**

```java
package com.fooweapons.fire;

public final class DamageCalculator {
    private DamageCalculator() {}

    public static double compute(
        double baseDamage,
        double distance,
        double falloffStart,
        double falloffEnd,
        double minDamage,
        double headshotMultiplier,
        boolean headshot
    ) {
        double dmg;
        if (distance <= falloffStart) {
            dmg = baseDamage;
        } else if (distance >= falloffEnd) {
            dmg = minDamage;
        } else {
            double t = (distance - falloffStart) / (falloffEnd - falloffStart);
            dmg = baseDamage + (minDamage - baseDamage) * t;
        }
        return headshot ? dmg * headshotMultiplier : dmg;
    }
}
```

- [ ] **Step 4: Run test, verify pass**

```bash
./gradlew :plugin:test --tests com.fooweapons.fire.DamageCalculatorTest
```

Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/fire/DamageCalculator.java \
        plugin/src/test/java/com/fooweapons/fire/DamageCalculatorTest.java
git commit -m "feat: distance-falloff damage calculation with headshot multiplier"
```

### Task 12: `SpreadCalculator`

**Files:**
- Test: `plugin/src/test/java/com/fooweapons/fire/SpreadCalculatorTest.java`
- Create: `plugin/src/main/java/com/fooweapons/fire/SpreadCalculator.java`

- [ ] **Step 1: Write failing test**

```java
package com.fooweapons.fire;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class SpreadCalculatorTest {
    @Test
    void zeroSpreadReturnsExactDirection() {
        Vector base = new Vector(1, 0, 0);
        Vector result = SpreadCalculator.applySpread(base, 0.0, new Random(42));
        assertEquals(1.0, result.getX(), 0.0001);
        assertEquals(0.0, result.getY(), 0.0001);
        assertEquals(0.0, result.getZ(), 0.0001);
    }

    @Test
    void spreadStaysWithinExpectedConeRoughly() {
        Vector base = new Vector(1, 0, 0);
        Random r = new Random(42);
        for (int i = 0; i < 100; i++) {
            Vector result = SpreadCalculator.applySpread(base, 5.0, r);
            double dot = base.dot(result.clone().normalize());
            double angleDeg = Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, dot))));
            assertTrue(angleDeg <= 5.5, "angle " + angleDeg + " exceeded 5°");
        }
    }
}
```

> This depends on `org.bukkit.util.Vector`, which is provided by paper-api's `compileOnly` — test scope needs it too. Add `testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")` to `plugin/build.gradle.kts` dependencies block if not already present.

- [ ] **Step 2: Update `plugin/build.gradle.kts` dependencies block**

```kotlin
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.21:4.0.0")
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
./gradlew :plugin:test --tests com.fooweapons.fire.SpreadCalculatorTest
```

Expected: FAIL (compile error).

- [ ] **Step 4: Implement**

```java
package com.fooweapons.fire;

import org.bukkit.util.Vector;
import java.util.Random;

public final class SpreadCalculator {
    private SpreadCalculator() {}

    public static Vector applySpread(Vector direction, double maxDegrees, Random random) {
        if (maxDegrees <= 0.0) return direction.clone();
        Vector dir = direction.clone().normalize();
        double phi = random.nextDouble() * 2.0 * Math.PI;
        double theta = Math.toRadians(random.nextDouble() * maxDegrees);

        Vector perp1;
        if (Math.abs(dir.getY()) < 0.9) {
            perp1 = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        } else {
            perp1 = dir.clone().crossProduct(new Vector(1, 0, 0)).normalize();
        }
        Vector perp2 = dir.clone().crossProduct(perp1).normalize();

        double sinTheta = Math.sin(theta);
        double cosTheta = Math.cos(theta);
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);

        return dir.multiply(cosTheta)
            .add(perp1.multiply(sinTheta * cosPhi))
            .add(perp2.multiply(sinTheta * sinPhi));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
./gradlew :plugin:test --tests com.fooweapons.fire.SpreadCalculatorTest
```

Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add plugin/build.gradle.kts \
        plugin/src/main/java/com/fooweapons/fire/SpreadCalculator.java \
        plugin/src/test/java/com/fooweapons/fire/SpreadCalculatorTest.java
git commit -m "feat: apply uniform cone spread to fire direction"
```

---

## Phase 5: Fire Service and Listener

### Task 13: `Hitscan` raycast wrapper

**Files:**
- Create: `plugin/src/main/java/com/fooweapons/fire/Hitscan.java`

- [ ] **Step 1: Write the wrapper**

```java
package com.fooweapons.fire;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class Hitscan {
    public record Hit(LivingEntity target, double distance, boolean headshot) {}

    public static Hit fire(Player shooter, Vector direction, double range) {
        Location eye = shooter.getEyeLocation();
        RayTraceResult result = shooter.getWorld().rayTrace(
            eye, direction.normalize(), range,
            org.bukkit.FluidCollisionMode.NEVER, true, 0.0,
            e -> e instanceof LivingEntity && !e.equals(shooter)
        );
        if (result == null || result.getHitEntity() == null) return null;
        Entity hit = result.getHitEntity();
        if (!(hit instanceof LivingEntity target)) return null;
        double distance = eye.toVector().distance(result.getHitPosition());
        boolean headshot = isHeadshot(target, result.getHitPosition().getY());
        return new Hit(target, distance, headshot);
    }

    private static boolean isHeadshot(LivingEntity target, double hitY) {
        double headY = target.getEyeLocation().getY();
        return hitY >= headY - 0.15;
    }
}
```

- [ ] **Step 2: Build to verify compilation**

```bash
./gradlew :plugin:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/fire/Hitscan.java
git commit -m "feat: hitscan raycast wrapper with headshot detection"
```

### Task 14: `FireService` orchestration

**Files:**
- Create: `plugin/src/main/java/com/fooweapons/fire/FireService.java`

- [ ] **Step 1: Implement service**

```java
package com.fooweapons.fire;

import com.fooweapons.item.ItemState;
import com.fooweapons.weapon.Weapon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import java.util.Random;

public final class FireService {
    private final ItemState state;
    private final Random random = new Random();

    public FireService(ItemState state) {
        this.state = state;
    }

    public Result tryFire(Player player, ItemStack stack, Weapon weapon) {
        long now = System.currentTimeMillis();
        if (!FireRateCooldown.canFire(state.getLastFiredMs(stack), now, weapon.fireCooldownMillis())) {
            return Result.COOLDOWN;
        }
        int ammo = state.getAmmo(stack);
        if (ammo <= 0) return Result.DRY;

        state.setAmmo(stack, ammo - 1);
        state.setLastFiredMs(stack, now);

        double spread = weapon.baseSpreadDegrees();
        if (player.getVelocity().lengthSquared() > 0.01) {
            spread += weapon.movingSpreadPenaltyDegrees();
        }
        Vector dir = SpreadCalculator.applySpread(
            player.getEyeLocation().getDirection(), spread, random);

        Hitscan.Hit hit = Hitscan.fire(player, dir, weapon.range());
        if (hit != null) {
            double dmg = DamageCalculator.compute(
                weapon.damage(),
                hit.distance(),
                weapon.falloffStart(),
                weapon.falloffEnd(),
                Math.max(1.0, weapon.damage() * 0.25),
                weapon.headshotMultiplier(),
                hit.headshot()
            );
            LivingEntity target = hit.target();
            target.damage(dmg, player);
        }
        return Result.FIRED;
    }

    public enum Result { FIRED, COOLDOWN, DRY }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :plugin:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/fire/FireService.java
git commit -m "feat: orchestrate fire — cooldown, ammo, hitscan, damage"
```

### Task 15: `FireListener` for left-click

**Files:**
- Create: `plugin/src/main/java/com/fooweapons/fire/FireListener.java`
- Modify: `plugin/src/main/java/com/fooweapons/FooWeaponsPlugin.java`

- [ ] **Step 1: Write listener**

```java
package com.fooweapons.fire;

import com.fooweapons.item.WeaponItemFactory;
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

    public FireListener(WeaponRegistry registry, WeaponItemFactory factory, FireService fireService) {
        this.registry = registry;
        this.factory = factory;
        this.fireService = fireService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != null && event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!factory.isWeapon(stack)) return;
        event.setCancelled(true);
        String id = factory.getWeaponId(stack);
        Optional<Weapon> weapon = registry.get(id);
        if (weapon.isEmpty()) return;
        fireService.tryFire(player, stack, weapon.get());
    }
}
```

- [ ] **Step 2: Register listener in plugin main**

Update `FooWeaponsPlugin.java` `onEnable`:

```java
package com.fooweapons;

import com.fooweapons.fire.FireListener;
import com.fooweapons.fire.FireService;
import com.fooweapons.item.ItemState;
import com.fooweapons.item.PdcKeys;
import com.fooweapons.item.WeaponItemFactory;
import com.fooweapons.weapon.WeaponRegistry;
import org.bukkit.plugin.java.JavaPlugin;

public final class FooWeaponsPlugin extends JavaPlugin {
    private WeaponRegistry weapons;
    private PdcKeys pdcKeys;
    private WeaponItemFactory itemFactory;
    private ItemState itemState;

    @Override
    public void onEnable() {
        this.pdcKeys = new PdcKeys(this);
        this.itemFactory = new WeaponItemFactory(pdcKeys);
        this.itemState = new ItemState(pdcKeys);
        this.weapons = new WeaponRegistry();
        try {
            weapons.loadFromClasspath(getClassLoader(), "weapons/pistol_01.yml");
        } catch (Exception e) {
            getLogger().severe("Failed to load weapons: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        FireService fireService = new FireService(itemState);
        getServer().getPluginManager().registerEvents(
            new FireListener(weapons, itemFactory, fireService), this);
        getLogger().info("fooWeapons enabled with " + weapons.size() + " weapon(s).");
    }

    public WeaponRegistry weapons() { return weapons; }
    public WeaponItemFactory itemFactory() { return itemFactory; }
    public ItemState itemState() { return itemState; }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew :plugin:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/
git commit -m "feat: handle left-click fire for weapon items"
```

---

## Phase 6: Reload

### Task 16: `ReloadListener`

**Files:**
- Create: `plugin/src/main/java/com/fooweapons/reload/ReloadListener.java`
- Modify: `plugin/src/main/java/com/fooweapons/FooWeaponsPlugin.java`

- [ ] **Step 1: Write listener**

```java
package com.fooweapons.reload;

import com.fooweapons.item.ItemState;
import com.fooweapons.item.WeaponItemFactory;
import com.fooweapons.weapon.Weapon;
import com.fooweapons.weapon.WeaponRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ReloadListener implements Listener {
    private final Plugin plugin;
    private final WeaponRegistry registry;
    private final WeaponItemFactory factory;
    private final ItemState state;
    private final Set<UUID> reloading = new HashSet<>();

    public ReloadListener(Plugin plugin, WeaponRegistry registry,
                          WeaponItemFactory factory, ItemState state) {
        this.plugin = plugin;
        this.registry = registry;
        this.factory = factory;
        this.state = state;
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        if (!factory.isWeapon(main)) return;
        event.setCancelled(true);

        if (reloading.contains(player.getUniqueId())) return;
        String id = factory.getWeaponId(main);
        Weapon weapon = registry.get(id).orElse(null);
        if (weapon == null) return;

        int current = state.getAmmo(main);
        if (current >= weapon.magSize()) return;

        reloading.add(player.getUniqueId());
        player.sendActionBar(net.kyori.adventure.text.Component.text("Reloading..."));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            reloading.remove(player.getUniqueId());
            ItemStack currentHand = player.getInventory().getItemInMainHand();
            if (factory.isWeapon(currentHand) && id.equals(factory.getWeaponId(currentHand))) {
                state.setAmmo(currentHand, weapon.magSize());
            }
        }, weapon.reloadTimeTicks());
    }

    public boolean isReloading(UUID playerId) {
        return reloading.contains(playerId);
    }
}
```

> v1 reload is "free" — does not consume a separate magazine item. Magazine items are deferred to Plan 2.

- [ ] **Step 2: Register in plugin main**

Add to `FooWeaponsPlugin.onEnable()` after `registerEvents(new FireListener(...))`:

```java
ReloadListener reloadListener = new ReloadListener(this, weapons, itemFactory, itemState);
getServer().getPluginManager().registerEvents(reloadListener, this);
```

Add field and getter:
```java
private ReloadListener reloadListener;
// ... in onEnable, replace assignment line:
this.reloadListener = new ReloadListener(this, weapons, itemFactory, itemState);
getServer().getPluginManager().registerEvents(reloadListener, this);

public ReloadListener reloadListener() { return reloadListener; }
```

- [ ] **Step 3: Make `FireListener` respect reload lock**

Modify `FireListener` constructor to accept `ReloadListener` and add a check:

```java
public final class FireListener implements Listener {
    private final WeaponRegistry registry;
    private final WeaponItemFactory factory;
    private final FireService fireService;
    private final ReloadListener reloadListener;

    public FireListener(WeaponRegistry registry, WeaponItemFactory factory,
                        FireService fireService, ReloadListener reloadListener) {
        this.registry = registry;
        this.factory = factory;
        this.fireService = fireService;
        this.reloadListener = reloadListener;
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
        fireService.tryFire(player, stack, weapon.get());
    }
}
```

Add the import: `import com.fooweapons.reload.ReloadListener;`

Update `FooWeaponsPlugin.onEnable` to construct `FireListener` AFTER `ReloadListener` and pass it in:

```java
this.reloadListener = new ReloadListener(this, weapons, itemFactory, itemState);
getServer().getPluginManager().registerEvents(reloadListener, this);

FireService fireService = new FireService(itemState);
getServer().getPluginManager().registerEvents(
    new FireListener(weapons, itemFactory, fireService, reloadListener), this);
```

- [ ] **Step 4: Build**

```bash
./gradlew :plugin:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/
git commit -m "feat: reload via swap-hands key with reload-time lock"
```

---

## Phase 7: HUD and Feedback

### Task 17: `HudService` action bar

**Files:**
- Create: `plugin/src/main/java/com/fooweapons/hud/HudService.java`
- Modify: `FooWeaponsPlugin.java`, `FireService.java`

- [ ] **Step 1: Write HUD service**

```java
package com.fooweapons.hud;

import com.fooweapons.item.ItemState;
import com.fooweapons.item.WeaponItemFactory;
import com.fooweapons.weapon.Weapon;
import com.fooweapons.weapon.WeaponRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class HudService {
    private final Plugin plugin;
    private final WeaponRegistry registry;
    private final WeaponItemFactory factory;
    private final ItemState state;
    private BukkitTask task;

    public HudService(Plugin plugin, WeaponRegistry registry,
                      WeaponItemFactory factory, ItemState state) {
        this.plugin = plugin;
        this.registry = registry;
        this.factory = factory;
        this.state = state;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack stack = player.getInventory().getItemInMainHand();
            if (!factory.isWeapon(stack)) continue;
            Weapon w = registry.get(factory.getWeaponId(stack)).orElse(null);
            if (w == null) continue;
            int ammo = state.getAmmo(stack);
            player.sendActionBar(Component.text(w.displayName() + "  ", NamedTextColor.GRAY)
                .append(Component.text(ammo + "/" + w.magSize(),
                    ammo == 0 ? NamedTextColor.RED : NamedTextColor.WHITE)));
        }
    }
}
```

- [ ] **Step 2: Wire into plugin**

Add to `FooWeaponsPlugin`:

```java
private HudService hudService;

// In onEnable, after listeners:
this.hudService = new HudService(this, weapons, itemFactory, itemState);
hudService.start();

// In onDisable:
@Override
public void onDisable() {
    if (hudService != null) hudService.stop();
}
```

- [ ] **Step 3: Build**

```bash
./gradlew :plugin:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/
git commit -m "feat: action-bar HUD showing weapon name and ammo"
```

### Task 18: `SoundService`

**Files:**
- Create: `plugin/src/main/java/com/fooweapons/feedback/SoundService.java`
- Modify: `FireService.java`, `ReloadListener.java`

- [ ] **Step 1: Write sound service**

```java
package com.fooweapons.feedback;

import org.bukkit.Location;
import org.bukkit.Sound;
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

    private void play(Player player, String soundId, float volume, float pitch) {
        Location loc = player.getLocation();
        player.getWorld().playSound(loc, soundId, SoundCategory.PLAYERS, volume, pitch);
    }
}
```

- [ ] **Step 2: Inject into `FireService`**

Update `FireService`:

```java
public final class FireService {
    private final ItemState state;
    private final SoundService sounds;
    private final Random random = new Random();

    public FireService(ItemState state, SoundService sounds) {
        this.state = state;
        this.sounds = sounds;
    }

    public Result tryFire(Player player, ItemStack stack, Weapon weapon) {
        long now = System.currentTimeMillis();
        if (!FireRateCooldown.canFire(state.getLastFiredMs(stack), now, weapon.fireCooldownMillis())) {
            return Result.COOLDOWN;
        }
        int ammo = state.getAmmo(stack);
        if (ammo <= 0) {
            sounds.playDryFire(player, weapon.dryFireSoundId());
            return Result.DRY;
        }

        state.setAmmo(stack, ammo - 1);
        state.setLastFiredMs(stack, now);
        sounds.playFire(player, weapon.fireSoundId());

        // ... rest unchanged ...
```

Add `import com.fooweapons.feedback.SoundService;` and keep the rest of the method body identical.

- [ ] **Step 3: Inject into `ReloadListener`**

Update constructor and use:

```java
public ReloadListener(Plugin plugin, WeaponRegistry registry,
                      WeaponItemFactory factory, ItemState state, SoundService sounds) {
    this.plugin = plugin;
    this.registry = registry;
    this.factory = factory;
    this.state = state;
    this.sounds = sounds;
}

// At the point where reload starts:
sounds.playReload(player, weapon.reloadSoundId());
```

Add field `private final SoundService sounds;` and import.

- [ ] **Step 4: Update plugin main wiring**

```java
SoundService soundService = new SoundService();
this.reloadListener = new ReloadListener(this, weapons, itemFactory, itemState, soundService);
getServer().getPluginManager().registerEvents(reloadListener, this);

FireService fireService = new FireService(itemState, soundService);
getServer().getPluginManager().registerEvents(
    new FireListener(weapons, itemFactory, fireService, reloadListener), this);
```

- [ ] **Step 5: Build**

```bash
./gradlew :plugin:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/
git commit -m "feat: emit fire, reload, and dry-fire sounds"
```

---

## Phase 8: Admin Give Command

### Task 19: `/fooweapons give <weapon_id>`

**Files:**
- Create: `plugin/src/main/java/com/fooweapons/command/GiveCommand.java`
- Modify: `FooWeaponsPlugin.java`

- [ ] **Step 1: Write command**

```java
package com.fooweapons.command;

import com.fooweapons.item.WeaponItemFactory;
import com.fooweapons.weapon.Weapon;
import com.fooweapons.weapon.WeaponRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.util.Optional;

public final class GiveCommand implements CommandExecutor {
    private final WeaponRegistry registry;
    private final WeaponItemFactory factory;

    public GiveCommand(WeaponRegistry registry, WeaponItemFactory factory) {
        this.registry = registry;
        this.factory = factory;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text("Usage: /fooweapons give <weapon_id>", NamedTextColor.YELLOW));
            return true;
        }
        Optional<Weapon> weapon = registry.get(args[1]);
        if (weapon.isEmpty()) {
            sender.sendMessage(Component.text("Unknown weapon: " + args[1], NamedTextColor.RED));
            return true;
        }
        ItemStack stack = factory.create(weapon.get());
        player.getInventory().addItem(stack);
        sender.sendMessage(Component.text("Gave " + weapon.get().displayName(), NamedTextColor.GREEN));
        return true;
    }
}
```

- [ ] **Step 2: Register in plugin main**

In `FooWeaponsPlugin.onEnable()`:

```java
getCommand("fooweapons").setExecutor(new GiveCommand(weapons, itemFactory));
```

- [ ] **Step 3: Build**

```bash
./gradlew :plugin:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add plugin/src/main/java/com/fooweapons/command/ plugin/src/main/java/com/fooweapons/FooWeaponsPlugin.java
git commit -m "feat: /fooweapons give <id> admin command"
```

---

## Phase 9: Resource Pack — Structure and Model

### Task 20: Resource pack scaffold

**Files:**
- Create: `resourcepack/pack.mcmeta`
- Create: `resourcepack/assets/minecraft/items/crossbow.json`

- [ ] **Step 1: Write `pack.mcmeta`**

```json
{
  "pack": {
    "pack_format": 55,
    "description": "fooWeapons v1 models"
  }
}
```

> `pack_format` 55 corresponds to Minecraft 1.21.11. If the pack rejects on load, check the current pack_format table on the Minecraft Wiki under "Pack format" and substitute the right number.

- [ ] **Step 2: Write crossbow override**

`resourcepack/assets/minecraft/items/crossbow.json`:

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
      }
    ],
    "fallback": {
      "type": "minecraft:model",
      "model": "minecraft:item/crossbow"
    }
  }
}
```

> The 1.21.4+ item model definition format. The `when` value is the `CustomModelData` integer matched as a string. If 1.21.11 has format tweaks, verify against the current Minecraft Wiki "Items model definition" page and adjust.

- [ ] **Step 3: Commit**

```bash
git add resourcepack/pack.mcmeta resourcepack/assets/minecraft/items/crossbow.json
git commit -m "feat: resource pack scaffold with crossbow CMD override"
```

### Task 21: Pistol model JSON

**Files:**
- Create: `resourcepack/assets/fooweapons/models/item/pistol_01.json`

- [ ] **Step 1: Write the model**

Block-shaped pistol silhouette: grip (vertical cuboid), frame (horizontal cuboid), barrel (small extension), trigger (small cube under frame). All references to a single texture face.

```json
{
  "credit": "fooWeapons original model",
  "texture_size": [16, 16],
  "textures": {
    "0": "fooweapons:item/pistol_01",
    "particle": "fooweapons:item/pistol_01"
  },
  "elements": [
    {
      "name": "grip",
      "from": [7, 0, 7.5],
      "to": [9, 5, 8.5],
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
      "name": "frame",
      "from": [6, 5, 7.5],
      "to": [11, 7, 8.5],
      "faces": {
        "north": {"uv": [0, 0, 5, 2], "texture": "#0"},
        "east":  {"uv": [0, 0, 1, 2], "texture": "#0"},
        "south": {"uv": [0, 0, 5, 2], "texture": "#0"},
        "west":  {"uv": [0, 0, 1, 2], "texture": "#0"},
        "up":    {"uv": [0, 0, 5, 1], "texture": "#0"},
        "down":  {"uv": [0, 0, 5, 1], "texture": "#0"}
      }
    },
    {
      "name": "barrel",
      "from": [11, 5.5, 7.75],
      "to": [13, 6.5, 8.25],
      "faces": {
        "north": {"uv": [0, 0, 2, 1], "texture": "#0"},
        "east":  {"uv": [0, 0, 1, 1], "texture": "#0"},
        "south": {"uv": [0, 0, 2, 1], "texture": "#0"},
        "west":  {"uv": [0, 0, 1, 1], "texture": "#0"},
        "up":    {"uv": [0, 0, 2, 1], "texture": "#0"},
        "down":  {"uv": [0, 0, 2, 1], "texture": "#0"}
      }
    },
    {
      "name": "trigger_guard",
      "from": [7.5, 4, 7.75],
      "to": [9, 5, 8.25],
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
      "scale": [0.8, 0.8, 0.8]
    },
    "thirdperson_lefthand": {
      "rotation": [0, 270, 0],
      "translation": [0, 2, 0],
      "scale": [0.8, 0.8, 0.8]
    },
    "firstperson_righthand": {
      "rotation": [0, 90, 0],
      "translation": [1, 3.5, 1.5],
      "scale": [1.0, 1.0, 1.0]
    },
    "firstperson_lefthand": {
      "rotation": [0, 270, 0],
      "translation": [1, 3.5, 1.5],
      "scale": [1.0, 1.0, 1.0]
    },
    "gui": {
      "rotation": [30, 45, 0],
      "translation": [0, 0, 0],
      "scale": [1.2, 1.2, 1.2]
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
git add resourcepack/assets/fooweapons/models/item/pistol_01.json
git commit -m "feat: pistol_01 blocky model geometry"
```

### Task 22: Procedural pistol texture

**Files:**
- Create: `resourcepack/assets/fooweapons/textures/item/pistol_01.png`
- Create: `tools/generate_textures.py` (one-shot generator script)

- [ ] **Step 1: Write generator script**

```python
# tools/generate_textures.py
# Generates a 16x16 dark-gray pistol texture with simple banding.
# Run: python tools/generate_textures.py
from PIL import Image

DARK = (45, 45, 50, 255)
MID = (75, 75, 80, 255)
LIGHT = (110, 110, 115, 255)
HIGHLIGHT = (160, 160, 165, 255)

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

img.save("resourcepack/assets/fooweapons/textures/item/pistol_01.png")
print("Wrote pistol_01.png")
```

- [ ] **Step 2: Run the generator**

```bash
python tools/generate_textures.py
```

Expected: file created at `resourcepack/assets/fooweapons/textures/item/pistol_01.png`. If Pillow is not installed: `pip install Pillow`.

- [ ] **Step 3: Commit**

```bash
git add tools/generate_textures.py resourcepack/assets/fooweapons/textures/item/pistol_01.png
git commit -m "feat: generate procedural pistol texture"
```

### Task 23: Zip the resource pack

**Files:**
- Modify: `build.gradle.kts` (root)

- [ ] **Step 1: Add a zip task**

Append to root `build.gradle.kts`:

```kotlin
tasks.register<Zip>("packageResourcePack") {
    archiveBaseName.set("fooWeapons-resourcepack")
    archiveVersion.set(project.version.toString())
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
    from("resourcepack") {
        include("pack.mcmeta")
        include("assets/**")
    }
}

tasks.register("dist") {
    dependsOn(":plugin:jar", "packageResourcePack")
    doLast {
        println("Plugin JAR: " + project(":plugin").layout.buildDirectory.file("libs/plugin-${project.version}.jar").get())
        println("Resource pack: " + layout.buildDirectory.file("dist/fooWeapons-resourcepack-${project.version}.zip").get())
    }
}
```

- [ ] **Step 2: Run dist**

```bash
./gradlew dist
```

Expected: BUILD SUCCESSFUL, with paths to both artifacts printed.

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "build: dist task packages plugin jar + resource pack zip"
```

---

## Phase 10: End-to-End Smoke Test

### Task 24: Manual playtest checklist

This task has no code — it's a verification gate.

- [ ] **Step 1: Host the resource pack**

Upload `build/dist/fooWeapons-resourcepack-0.1.0.zip` somewhere it can be served over HTTP(S). Options:
- A free static host (e.g., put it on a GitHub release as a download asset)
- A web-accessible folder on GPortal if available
- An S3 bucket or any static host

Note the public URL and compute SHA-1 of the zip:
```bash
sha1sum build/dist/fooWeapons-resourcepack-0.1.0.zip
```

- [ ] **Step 2: Upload the plugin to GPortal**

In the GPortal panel:
1. Navigate to the file browser for the Minecraft server.
2. Upload `plugin/build/libs/plugin-0.1.0.jar` to the `plugins/` directory.
3. Edit `server.properties` and set:
   ```
   resource-pack=<your-public-url>
   resource-pack-sha1=<sha1-from-step-1>
   require-resource-pack=true
   resource-pack-prompt={"text":"fooWeapons requires this pack."}
   ```
4. Restart the server.

- [ ] **Step 3: Verify on a vanilla 1.21.11 Java client**

Join the server with vanilla Minecraft Java Edition 1.21.11. Expected:
- Resource pack prompt appears on join. Accept it.
- Run `/op <your-username>` from server console, or have an existing op give you the permission `fooweapons.admin`.
- Run `/fooweapons give pistol_01` in-game.
- A crossbow appears in your inventory displaying the pistol model.
- Move it to your hotbar. Action bar shows `Pistol  12/12`.
- Left-click while looking at a mob (or another player). Mob takes damage. Action bar shows `Pistol  11/12`. Fire sound plays.
- Fire 12 shots. The 13th click plays a dry-fire sound and does nothing. Action bar shows `0/12` in red.
- Press `F`. Reloading message appears. After ~1.8 seconds, action bar returns to `12/12` and reload sound plays.
- Drop the item, pick it up. Ammo count persists.
- Die with the item in your hotbar. Respawn, retrieve the item. Ammo count persists.

- [ ] **Step 4: Record any failures**

If any of the above fails, that is a v1 bug to fix before declaring MVP done. Common issues:
- **Resource pack doesn't load:** check `pack_format` matches 1.21.11 (look up on Minecraft Wiki "Pack format" page).
- **Crossbow appears as a crossbow (not pistol):** the `items/crossbow.json` override didn't take effect — check namespace path, `when` value matches the YAML's `custom_model_data` (1001), pack URL is correct, and player accepted the prompt.
- **Fire doesn't decrement ammo:** check `FireListener` is registered, `event.setCancelled(true)` is reached, `WeaponItemFactory.isWeapon` returns true.
- **Reload doesn't trigger:** check `PlayerSwapHandItemsEvent` is registered. Some servers disable swap-hands; verify in `paper.yml` if so.

- [ ] **Step 5: Final commit (if needed)**

If any fixes were applied to address smoke-test failures, commit them now:
```bash
git add <changed files>
git commit -m "fix: smoke-test corrections for <issue>"
```

---

## Done Criteria

MVP is complete when **all** of these are true:

1. `./gradlew dist` produces both artifacts cleanly with no warnings about API misuse.
2. All JUnit tests pass (`./gradlew :plugin:test`): WeaponLoaderTest, FireRateCooldownTest, DamageCalculatorTest, SpreadCalculatorTest.
3. The Phase 10 smoke test checklist completes end-to-end on a real GPortal Paper 1.21.11 server with a vanilla 1.21.11 Java client.
4. The repository builds reproducibly from a clean clone with only `./gradlew dist` (plus a Python+Pillow install for the texture generator, which only needs to run once and commits its output).

## Out of Scope (deferred to Plan 2)

- The other five weapons (SMG, assault rifle, battle rifle, sniper, shotgun)
- Automatic fire (continuous-fire polling)
- Aim down sights (ADS)
- Recoil
- Fire mode toggle (semi/auto)
- Magazine items consumed on reload (currently reload is "free")
- Global config (worlds_enabled, friendly_fire, damage multipliers)
- Custom sound files (currently using vanilla sound events)
- Muzzle-flash particles
- Resource pack enforcement options beyond `require-resource-pack` in `server.properties`
