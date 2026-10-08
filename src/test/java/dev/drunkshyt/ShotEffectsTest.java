package dev.drunkshyt;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ShotEffectsTest {
    private ShotEffectRule left() { return new ShotEffectRule("owed", ShotEffectRule.Metric.LEFT, 5, "minecraft:slowness", 30, 1); }
    private ShotEffectRule drank() { return new ShotEffectRule("done", ShotEffectRule.Metric.DRANK, 3, "minecraft:nausea", 10, 2); }

    @Test void leftTriggersAtBoundaryAndOnSkippedThresholdButNotWhileAbove() {
        ShotEffectRule rule = left();
        assertFalse(rule.crossed(new ShotCount(3, 0), new ShotCount(4, 0)));
        assertTrue(rule.crossed(new ShotCount(4, 0), new ShotCount(5, 0)));
        assertTrue(rule.crossed(new ShotCount(4, 0), new ShotCount(10, 0)));
        assertFalse(rule.crossed(new ShotCount(5, 0), new ShotCount(6, 0)));
        assertFalse(rule.crossed(new ShotCount(6, 0), new ShotCount(6, 2)));
        assertTrue(rule.crossed(new ShotCount(6, 2), new ShotCount(7, 2)));
    }
    @Test void drankUsesCompletedNotTotalAndSupportsBatchCompletion() {
        ShotEffectRule rule = drank();
        assertFalse(rule.crossed(new ShotCount(5, 2), new ShotCount(10, 2)));
        assertTrue(rule.crossed(new ShotCount(5, 2), new ShotCount(5, 3)));
        assertTrue(rule.crossed(new ShotCount(5, 0), new ShotCount(5, 5)));
        assertFalse(rule.crossed(new ShotCount(5, 3), new ShotCount(5, 4)));
    }
    @Test void correctionsAndResetHavePredictableCrossings() {
        ShotCount initial = new ShotCount(10, 2);
        assertTrue(drank().crossed(initial, initial.remaining(3)));
        assertFalse(drank().crossed(initial, ShotCount.ZERO));
        assertFalse(left().crossed(initial, ShotCount.ZERO));
        assertTrue(left().crossed(ShotCount.ZERO, new ShotCount(5, 0)));
        assertFalse(left().crossed(initial, initial));
    }
    @Test void disabledRulesNeverTriggerAndReenableDoesNotReplayOldCounts() {
        ShotEffects effects = new ShotEffects(false, List.of(left()));
        assertTrue(effects.triggered(ShotCount.ZERO, new ShotCount(6, 0)).isEmpty());
        effects.enabled(true);
        assertTrue(effects.triggered(new ShotCount(6, 0), new ShotCount(7, 0)).isEmpty());
        assertEquals(1, effects.triggered(new ShotCount(4, 0), new ShotCount(5, 0)).size());
    }
    @Test void matchesAllCrossedRulesAndKeepsPlayerTransitionsIndependent() {
        ShotEffects effects = new ShotEffects(true, List.of(left(), drank()));
        assertEquals(2, effects.triggered(ShotCount.ZERO, new ShotCount(10, 4)).size());
        assertEquals(1, effects.triggered(ShotCount.ZERO, new ShotCount(6, 0)).size());
        assertEquals(1, effects.triggered(ShotCount.ZERO, new ShotCount(6, 0)).size());
    }
    @Test void removedReplacedAndDisabledRulesInvalidateQueuedEffects() {
        ShotEffectRule old = left();
        ShotEffects effects = new ShotEffects(true, List.of(old));
        assertTrue(effects.active(old));
        effects.enabled(false); assertFalse(effects.active(old));
        effects.enabled(true); effects.remove(old.id()); assertFalse(effects.active(old));
        effects.add(left()); assertFalse(effects.active(old));
        effects.clear(); assertTrue(effects.rules().isEmpty());
    }
    @Test void yamlSaveLoadRoundTripKeepsRulesAndEnabledState() throws Exception {
        ShotEffects before = new ShotEffects(false, List.of(left(), drank()));
        YamlConfiguration config = new YamlConfiguration();
        config.set("effects.enabled", before.enabled()); config.set("effects.rules", before.save());
        YamlConfiguration loaded = new YamlConfiguration(); loaded.loadFromString(config.saveToString());
        ShotEffects after = ShotEffects.load(loaded.getBoolean("effects.enabled"), loaded.getList("effects.rules"));
        assertFalse(after.enabled()); assertEquals(before.rules(), after.rules());
    }
    @Test void invalidRulesAndDuplicateIdsAreRejectedWithoutChangingSavedRules() {
        ShotEffects effects = new ShotEffects(true, List.of(left()));
        assertThrows(IllegalArgumentException.class, () -> effects.add(left()));
        assertThrows(IllegalArgumentException.class, () -> effects.remove("missing"));
        assertEquals(List.of(left()), effects.rules());
        assertThrows(IllegalArgumentException.class, () -> new ShotEffectRule("bad.id", ShotEffectRule.Metric.LEFT, 5, "minecraft:speed", 30, 1));
        assertThrows(IllegalArgumentException.class, () -> new ShotEffectRule("x", ShotEffectRule.Metric.LEFT, 0, "minecraft:speed", 30, 1));
        assertThrows(IllegalArgumentException.class, () -> new ShotEffectRule("x", ShotEffectRule.Metric.LEFT, 5, "minecraft:speed", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ShotEffectRule("x", ShotEffectRule.Metric.LEFT, 5, "minecraft:speed", 3601, 1));
        assertThrows(IllegalArgumentException.class, () -> new ShotEffectRule("x", ShotEffectRule.Metric.LEFT, 5, "minecraft:speed", 30, 0));
        assertThrows(IllegalArgumentException.class, () -> new ShotEffectRule("x", ShotEffectRule.Metric.LEFT, 5, "minecraft:speed", 30, 256));
        assertThrows(IllegalArgumentException.class, () -> ShotEffects.load(true, List.of("invalid")));
        assertThrows(IllegalArgumentException.class, () -> ShotEffects.load(true, List.of(Map.of("id", "x"))));
    }
    @Test void synonymsAreExplicitAndInvalidMetricsRejected() {
        assertEquals(ShotEffectRule.Metric.DRANK, ShotEffectRule.Metric.parse("HAVE"));
        assertEquals(ShotEffectRule.Metric.LEFT, ShotEffectRule.Metric.parse("havent"));
        assertThrows(IllegalArgumentException.class, () -> ShotEffectRule.Metric.parse("total"));
    }
}
