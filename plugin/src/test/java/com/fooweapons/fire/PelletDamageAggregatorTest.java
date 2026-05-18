package com.fooweapons.fire;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PelletDamageAggregatorTest {
    @Test
    void singleHitAggregatesToSingleEntry() {
        PelletDamageAggregator<Object> agg = new PelletDamageAggregator<>();
        Object target = new Object();
        agg.add(target, 4.0);
        Map<Object, Double> result = agg.totals();
        assertEquals(1, result.size());
        assertEquals(4.0, result.get(target));
    }

    @Test
    void multipleHitsOnSameTargetSum() {
        PelletDamageAggregator<Object> agg = new PelletDamageAggregator<>();
        Object target = new Object();
        agg.add(target, 4.0);
        agg.add(target, 4.0);
        agg.add(target, 4.0);
        assertEquals(12.0, agg.totals().get(target));
        assertEquals(1, agg.totals().size());
    }

    @Test
    void hitsOnDifferentTargetsStaySeparate() {
        PelletDamageAggregator<Object> agg = new PelletDamageAggregator<>();
        Object a = new Object();
        Object b = new Object();
        agg.add(a, 4.0);
        agg.add(b, 4.0);
        agg.add(a, 4.0);
        Map<Object, Double> result = agg.totals();
        assertEquals(2, result.size());
        assertEquals(8.0, result.get(a));
        assertEquals(4.0, result.get(b));
    }

    @Test
    void noHitsReturnsEmptyMap() {
        PelletDamageAggregator<Object> agg = new PelletDamageAggregator<>();
        assertTrue(agg.totals().isEmpty());
    }

    @Test
    void usesReferenceIdentityNotEquals() {
        // Two String references with equal content must remain distinct entries.
        // (Defensive — real callers will be LivingEntity, but identity semantics
        // are what matter for the no-damage-ticks bundling.)
        PelletDamageAggregator<String> agg = new PelletDamageAggregator<>();
        String a = new String("entity");
        String b = new String("entity");
        assertNotSame(a, b);
        agg.add(a, 4.0);
        agg.add(b, 4.0);
        assertEquals(2, agg.totals().size());
    }
}
