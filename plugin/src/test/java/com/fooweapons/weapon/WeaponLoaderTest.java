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
        assertEquals(1, w.pelletsPerShot());
        assertEquals(MuzzleFlashConfig.defaults(), w.muzzleFlash());
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
