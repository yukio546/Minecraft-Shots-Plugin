package dev.drunkshyt;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Keeps player identity separate from the font selected for plugin controls. */
final class ShotText {
    static final Key VANILLA = Key.key("minecraft:default");
    private final Key font;

    ShotText(Key font) { this.font = font; }

    Component text(String value, NamedTextColor color) {
        return Component.text(value, color).font(font).decoration(TextDecoration.ITALIC, false);
    }

    Component name(String value, NamedTextColor color) {
        return Component.text(value, color).font(VANILLA).decoration(TextDecoration.ITALIC, false);
    }

    Component summary(ShotCount count) {
        return text(ShotCount.bracket(count.total()), NamedTextColor.GOLD)
                .append(text(" shots | ", NamedTextColor.GRAY))
                .append(text(ShotCount.bracket(count.left()), count.left() == 0 ? NamedTextColor.GREEN : NamedTextColor.AQUA))
                .append(text(" left", NamedTextColor.GRAY));
    }

    Component namedSummary(String name, ShotCount count) {
        return name(name, NamedTextColor.WHITE).append(text("  ", NamedTextColor.WHITE)).append(summary(count));
    }

    Component adminResult(String name, ShotCount count) {
        return text("[Shots] ", NamedTextColor.GOLD).append(name(name, NamedTextColor.GRAY))
                .append(text(": " + count.display(), NamedTextColor.GRAY));
    }

    Component resetHint(String target) {
        Component line = text("Type /shots reset ", NamedTextColor.GRAY);
        if (target != null) {
            line = line.append(target.equalsIgnoreCase("all") ? text(target, NamedTextColor.GRAY)
                    : name(target, NamedTextColor.GRAY)).append(text(" ", NamedTextColor.GRAY));
        }
        return line.append(text("confirm", NamedTextColor.GRAY));
    }
}
