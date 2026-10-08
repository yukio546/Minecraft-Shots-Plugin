package dev.drunkshyt;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShotTextTest {
    private static final Key FIVE = Key.key("drunkshyt:five");
    private final ShotText text = new ShotText(FIVE);
    private final ShotCount count = new ShotCount(10, 7);
    private record Run(String text, Key font) {}

    private List<Run> runs(Component component, Key inherited) {
        Key font = component.font() == null ? inherited : component.font();
        List<Run> runs = new ArrayList<>();
        if (component instanceof TextComponent part && !part.content().isEmpty()) runs.add(new Run(part.content(), font));
        component.children().forEach(child -> runs.addAll(runs(child, font)));
        return runs;
    }

    private void assertMixed(Component component, String name, String expected) {
        List<Run> runs = runs(component, ShotText.VANILLA);
        assertEquals(expected, runs.stream().map(Run::text).reduce("", String::concat));
        int start = expected.indexOf(name), offset = 0;
        assertTrue(start >= 0);
        for (Run run : runs) {
            for (int i = 0; i < run.text().length(); i++, offset++) {
                assertEquals(offset >= start && offset < start + name.length() ? ShotText.VANILLA : FIVE,
                        run.font(), "Wrong effective font at character " + offset);
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"Example_42", "Player_01", "ABC123_xyz"})
    void tabAndStatusKeepOnlyTheUsernameVanilla(String name) {
        assertMixed(text.namedSummary(name, count), name, name + "  [10] shots | [03] left");
    }

    @ParameterizedTest @ValueSource(strings = {"Example_42", "Player_01"})
    void adminResponseKeepsUsernameVanillaInsideCustomParent(String name) {
        assertMixed(text.adminResult(name, count), name, "[Shots] " + name + ": [10] shots | [03] left");
    }

    @ParameterizedTest @ValueSource(strings = {"Example_42", "Player_01"})
    void resetCommandKeepsUsernameVanilla(String name) {
        assertMixed(text.resetHint(name), name, "Type /shots reset " + name + " confirm");
    }

    @Test void selfAndAllResetHintsRemainEntirelyCustom() {
        for (String target : new String[]{null, "all", "ALL"}) {
            assertTrue(runs(text.resetHint(target), ShotText.VANILLA).stream().allMatch(run -> run.font().equals(FIVE)));
        }
    }

    @Test void suffixIsEntirelyCustomAndNeverRestylesItsParentName() {
        Component suffix = text.text("  ", NamedTextColor.GRAY).append(text.summary(count));
        Component named = Component.text("Player_01").append(suffix);
        assertMixed(named, "Player_01", "Player_01  [10] shots | [03] left");
    }

    @Test void defaultConfigStillWorksWithoutAResourcePack() {
        assertTrue(runs(new ShotText(ShotText.VANILLA).namedSummary("Player_01", count), FIVE)
                .stream().allMatch(run -> run.font().equals(ShotText.VANILLA)));
    }
}
