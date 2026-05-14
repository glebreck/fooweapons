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
    }

    @Test
    void throwsOnMissingId() {
        String yaml = "display_name: \"Nameless\"\n";
        WeaponLoader loader = new WeaponLoader();
        assertThrows(IllegalArgumentException.class,
            () -> loader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))));
    }
}
