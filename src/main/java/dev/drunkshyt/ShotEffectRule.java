package dev.drunkshyt;

import java.util.Locale;

/** A finite effect on an upward crossing, not a timer that reapplies effects. */
public record ShotEffectRule(String id, Metric metric, int threshold, String effect, int seconds, int level) {
    public enum Metric {
        DRANK, LEFT;
        public static Metric parse(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "drank", "have" -> DRANK;
                case "left", "havent" -> LEFT;
                default -> throw new IllegalArgumentException("Use drank (completed) or left (not yet completed).");
            };
        }
        public int value(ShotCount count) { return this == DRANK ? count.completed() : count.left(); }
        public String label() { return name().toLowerCase(Locale.ROOT); }
    }

    public ShotEffectRule {
        if (id == null || !id.matches("[a-z0-9_-]{1,32}")) throw new IllegalArgumentException("Rule ID: 1-32 lowercase letters, digits, underscores or hyphens.");
        if (metric == null) throw new IllegalArgumentException("Missing shot counter.");
        if (threshold < 1 || threshold > ShotCount.MAX) throw new IllegalArgumentException("Threshold must be 1-" + ShotCount.MAX + ".");
        if (effect == null || !effect.matches("minecraft:[a-z0-9_]+")) throw new IllegalArgumentException("Use a Minecraft potion effect name.");
        if (seconds < 1 || seconds > 3600) throw new IllegalArgumentException("Duration must be 1-3600 seconds.");
        if (level < 1 || level > 255) throw new IllegalArgumentException("Effect level must be 1-255 (1 means level I).");
    }

    public boolean crossed(ShotCount before, ShotCount after) {
        return metric.value(before) < threshold && metric.value(after) >= threshold;
    }

    public boolean reached(ShotCount count) { return metric.value(count) >= threshold; }
}
