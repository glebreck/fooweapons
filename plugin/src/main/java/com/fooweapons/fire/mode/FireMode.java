package com.fooweapons.fire.mode;

public enum FireMode {
    SEMI("SEMI", "semi"),
    AUTO("AUTO", "auto"),
    PUMP("PUMP", "pump");

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
