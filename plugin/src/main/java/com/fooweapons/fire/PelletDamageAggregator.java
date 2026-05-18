package com.fooweapons.fire;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Bundles per-pellet damage by target so all pellets hitting the same entity
 * in one shot apply as a single damage call. Required because Bukkit's
 * no-damage-ticks invuln window absorbs subsequent same-tick damage on the
 * same target — without bundling, 8 point-blank shotgun pellets would deal
 * one pellet's worth of damage.
 *
 * Uses reference identity (IdentityHashMap), not equals(), because targets
 * are entity references and we want one bucket per distinct entity instance.
 */
public final class PelletDamageAggregator<T> {
    private final Map<T, Double> totals = new IdentityHashMap<>();

    public void add(T target, double damage) {
        totals.merge(target, damage, Double::sum);
    }

    public Map<T, Double> totals() {
        return totals;
    }
}
