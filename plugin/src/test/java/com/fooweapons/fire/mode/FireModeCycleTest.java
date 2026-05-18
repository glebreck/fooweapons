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
