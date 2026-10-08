package dev.drunkshyt.verify;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackCompatibility;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/** Opens the deliverable ZIP through the real Minecraft resource/font engine. */
public class FontPackGameTest implements FabricClientGameTest {
    private static final String ZIP = "DrunkShyt-MinecraftFive-1.1.zip";
    private static final FontDescription FIVE = new FontDescription.Resource(Identifier.parse("drunkshyt:five"));
    private static final FontDescription DEFAULT = new FontDescription.Resource(Identifier.parse("minecraft:default"));
    private static final String SAMPLE = "[10] shots | [03] left";

    private static MutableComponent five(String text) {
        return Component.literal(text).withStyle(style -> style.withFont(FIVE).withItalic(false));
    }
    private static MutableComponent name(String text) {
        return Component.literal(text).withStyle(style -> style.withFont(DEFAULT).withItalic(false));
    }
    private static MutableComponent counter() {
        return five("[10]").withColor(0xffaa00).append(five(" shots | ").withColor(0xaaaaaa))
                .append(five("[03]").withColor(0x55ffff)).append(five(" left").withColor(0xaaaaaa));
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        System.out.println("FONT VERIFY PASS: " + message);
    }
    private static int[] widths(Minecraft client) {
        int[] widths = new int[95];
        for (int code = 32; code <= 126; code++) widths[code - 32] = client.font.width(Character.toString(code));
        return widths;
    }
    private static GlyphSource source(Font font, FontDescription description) throws Exception {
        Method method = Font.class.getDeclaredMethod("getGlyphSource", FontDescription.class);
        method.setAccessible(true);
        return (GlyphSource) method.invoke(font, description);
    }
    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
    private static Class<?> glyphClass(GlyphSource source, int code) throws Exception {
        Object set = field(source, "this$0");
        Method getGlyph = set.getClass().getDeclaredMethod("getGlyph", int.class);
        getGlyph.setAccessible(true);
        Object selection = getGlyph.invoke(set, code);
        return field(field(selection, "any"), "unbaked").getClass();
    }

    @Override public void runTest(ClientGameTestContext context) {
        try {
            int[] before = context.computeOnClient(FontPackGameTest::widths);
            CompletableFuture<Void> reload = context.computeOnClient(client -> {
                Path packs = client.gameDirectory.toPath().resolve("resourcepacks");
                Files.createDirectories(packs);
                Files.copy(Path.of(System.getProperty("drunkshyt.pack")), packs.resolve(ZIP), StandardCopyOption.REPLACE_EXISTING);
                var repository = client.getResourcePackRepository();
                repository.reload();
                var pack = repository.getPack("file/" + ZIP);
                check(pack != null, "ZIP is discovered by Minecraft");
                check(pack.getCompatibility() == PackCompatibility.COMPATIBLE, "pack metadata is compatible");
                var selected = new ArrayList<>(repository.getSelectedIds());
                selected.add("file/" + ZIP);
                repository.setSelected(selected);
                return client.reloadResourcePacks();
            });
            context.waitFor(client -> reload.isDone(), 1200);
            reload.join();
            context.waitFor(client -> client.gui.overlay() == null, 1200);
            context.runOnClient(client -> {
                check(client.getResourceManager().getResource(Identifier.parse("drunkshyt:font/five.json")).isPresent(), "namespaced font definition loaded");
                check(Arrays.equals(before, widths(client)), "all 95 vanilla ASCII character widths are unchanged");
                GlyphSource custom = source(client.font, FIVE);
                GlyphSource normal = source(client.font, DEFAULT);
                for (int code = 33; code <= 126; code++) {
                    check(glyphClass(custom, code).getName().contains("BitmapProvider"),
                            "Five bitmap glyph loaded for U+" + Integer.toHexString(code));
                }
                check(client.font.width(five(" ")) == 3, "custom word spacing is three whole pixels");
                check(client.font.width(five("A")) == 6, "five-pixel letter plus one-pixel gap");
                for (String playerName : new String[]{"Example_42", "Player_01", "ABC123_xyz"}) {
                    check(client.font.width(name(playerName)) == client.font.width(playerName), "vanilla username metrics: " + playerName);
                    check(client.font.width(name(playerName).append(five("  ")).append(counter()))
                            == client.font.width(playerName) + 6 + client.font.width(counter()), "mixed fonts remain independent: " + playerName);
                }
                check(client.font.width(five(SAMPLE)) != client.font.width(SAMPLE), "custom font has distinct metrics from vanilla");
                check(glyphClass(custom, 0x4e2d).equals(glyphClass(normal, 0x4e2d))
                                && !glyphClass(custom, 0x4e2d).getName().contains("Missing"),
                        "Unicode characters outside Five use the vanilla fallback");
                check(client.getResourceManager().getResource(Identifier.parse("minecraft:font/default.json")).orElseThrow().sourcePackId().equals("vanilla"),
                        "default font definition still comes from vanilla");
            });
            for (int scale : new int[]{1, 2, 3}) {
                context.runOnClient(client -> { client.options.guiScale().set(scale); client.resizeGui(); });
                context.setScreen(() -> new ProofScreen(scale));
                context.waitTicks(5);
                Files.createDirectories(Path.of(System.getProperty("drunkshyt.proof.dir")));
                Path screenshot = context.takeScreenshot("minecraft-five-1.1-scale-" + scale);
                Files.copy(screenshot, Path.of(System.getProperty("drunkshyt.proof.dir"))
                        .resolve("minecraft-five-1.1-scale-" + scale + ".png"), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("FONT VERIFY SCREENSHOT: GUI scale " + scale + " " + screenshot);
            }
            context.setScreen(TitleScreen::new);
            System.out.println("FONT VERIFY COMPLETE: bitmap glyphs, mixed username fonts, vanilla isolation and screenshots at GUI scales 1, 2, 3.");
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static final class ProofScreen extends Screen {
        private final int scale;
        private ProofScreen(int scale) { super(Component.literal("DrunkShyt font verification")); this.scale = scale; }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(0, 0, width, height, 0xff11151a);
            int x = 12, y = 12;
            graphics.text(font, "MINECRAFT 26.2 / GUI SCALE " + scale, x, y, 0xff8d9dac);
            graphics.text(font, "Vanilla: " + SAMPLE, x, y + 18, 0xffc8cfd6);
            graphics.text(font, name("Example_42").append(five("  ")).append(counter()), x, y + 36, 0xffffffff);
            graphics.text(font, name("Player_01").append(five("  ")).append(counter()), x, y + 48, 0xffffffff);
            graphics.text(font, five("Minecraft Five 1.1 / plugin text"), x, y + 68, 0xfff0bd57);
            String[] lines = {"Shots", "Record completed", "Click: record 1 completed", "Shift-click: record 5 completed",
                    "Set remaining", "Quiet feedback: ON", "Reset your counter?", "Confirm reset   Cancel",
                    "[00] [01] [03] [10] [99] [100] [999999]", "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "abcdefghijklmnopqrstuvwxyz",
                    "0123456789 [] () / | : ; , . ! ? + - _ =", "Fallback: \u4e2d\u6587"};
            int row = y + 84;
            for (String line : lines) {
                if (x + font.width(five(line)) > width) throw new AssertionError("Clipped proof text at GUI scale " + scale);
                graphics.text(font, five(line), x, row, 0xffe5e9ed); row += 11;
            }
            graphics.text(font, five("Type /shots reset ").append(name("Player_01")).append(five(" confirm")), x, row + 3, 0xffc8cfd6);
            if (row + 12 > height) throw new AssertionError("Clipped proof rows at GUI scale " + scale);
        }
    }
}
