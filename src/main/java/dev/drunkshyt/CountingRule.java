package dev.drunkshyt;

import java.util.Set;

public record CountingRule(Mode mode, boolean paused, Set<String> ignoredCauses) {
    public enum Mode { DAMAGE, DEATH, MANUAL }

    public CountingRule { ignoredCauses = Set.copyOf(ignoredCauses); }

    public boolean damage(boolean cancelled, double finalDamage, boolean survivalOrAdventure, String cause) {
        return mode == Mode.DAMAGE && !paused && !cancelled && survivalOrAdventure
                && Double.isFinite(finalDamage) && finalDamage > 0 && !ignoredCauses.contains(cause);
    }

    public boolean death(boolean cancelled, boolean survivalOrAdventure) {
        return mode == Mode.DEATH && !paused && !cancelled && survivalOrAdventure;
    }
}
