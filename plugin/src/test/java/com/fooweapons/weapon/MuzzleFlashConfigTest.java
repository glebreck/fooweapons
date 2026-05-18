package com.fooweapons.weapon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MuzzleFlashConfigTest {
    @Test
    void defaultsReturnExpectedValues() {
        MuzzleFlashConfig cfg = MuzzleFlashConfig.defaults();
        assertEquals(5, cfg.flameCount());
        assertEquals(2, cfg.smokeCount());
        assertEquals(1.5, cfg.forwardOffset());
    }

    @Test
    void storesProvidedValues() {
        MuzzleFlashConfig cfg = new MuzzleFlashConfig(8, 4, 1.8);
        assertEquals(8, cfg.flameCount());
        assertEquals(4, cfg.smokeCount());
        assertEquals(1.8, cfg.forwardOffset());
    }
}
