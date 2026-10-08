package dev.drunkshyt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CountingRuleTest {
    private final CountingRule damage = new CountingRule(CountingRule.Mode.DAMAGE, false, Set.of());

    @Test void actualDamageQualifiesRegardlessOfAmountAndCause() {
        assertTrue(damage.damage(false, 0.01, true, "FALL"));
        assertTrue(damage.damage(false, 15, true, "ENTITY_ATTACK"));
        assertTrue(damage.damage(false, 1, true, "FIRE_TICK"));
    }

    @Test void cancelledDamageAndCreativeOrSpectatorDoNotCount() {
        assertFalse(damage.damage(true, 1, true, "FALL"));
        assertFalse(damage.damage(false, 1, false, "FALL"));
    }

    @ParameterizedTest @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
    void blockedAbsorbedOrInvalidDamageDoesNotCount(double amount) {
        assertFalse(damage.damage(false, amount, true, "ENTITY_ATTACK"));
    }

    @Test void ignoredCauseAndPausedRoundDoNotCount() {
        assertFalse(new CountingRule(CountingRule.Mode.DAMAGE, false, Set.of("FALL"))
                .damage(false, 1, true, "FALL"));
        assertFalse(new CountingRule(CountingRule.Mode.DAMAGE, true, Set.of())
                .damage(false, 1, true, "FALL"));
    }

    @Test void deathModeDoesNotDoubleCountFatalDamage() {
        CountingRule death = new CountingRule(CountingRule.Mode.DEATH, false, Set.of());
        assertFalse(death.damage(false, 1000, true, "VOID"));
        assertTrue(death.death(false, true));
        assertFalse(death.death(false, false));
        assertFalse(damage.death(false, true));
    }

    @Test void cancelledDeathDoesNotCountWhenAnotherPluginRevivesPlayer() {
        CountingRule death = new CountingRule(CountingRule.Mode.DEATH, false, Set.of());
        assertFalse(death.death(true, true));
        assertTrue(death.death(false, true));
    }

    @Test void manualAndPausedModesIgnoreAutomaticEvents() {
        CountingRule manual = new CountingRule(CountingRule.Mode.MANUAL, false, Set.of());
        assertFalse(manual.damage(false, 1, true, "FALL"));
        assertFalse(manual.death(false, true));
        assertFalse(new CountingRule(CountingRule.Mode.DEATH, true, Set.of()).death(false, true));
    }
}
