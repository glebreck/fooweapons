package com.fooweapons.fire.mode;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FireModeTest {
    @Test
    void pumpParsesFromYamlName() {
        assertEquals(FireMode.PUMP, FireMode.fromYamlName("pump"));
    }

    @Test
    void pumpLabelIsPump() {
        assertEquals("PUMP", FireMode.PUMP.label());
    }

    @Test
    void pumpYamlNameIsPump() {
        assertEquals("pump", FireMode.PUMP.yamlName());
    }
}
