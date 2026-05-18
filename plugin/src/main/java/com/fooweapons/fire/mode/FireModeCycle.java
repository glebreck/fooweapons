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
