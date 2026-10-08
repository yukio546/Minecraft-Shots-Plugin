package dev.drunkshyt;

import org.bukkit.World;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PvpControlTest {
    private static final class FakeWorld {
        final UUID id = UUID.randomUUID();
        boolean enabled;
        int writes;
        final World api;
        FakeWorld(boolean enabled) {
            this.enabled = enabled;
            api = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "getUID" -> id;
                    case "getPVP" -> this.enabled;
                    case "setPVP" -> { this.enabled = (boolean) args[0]; writes++; yield null; }
                    default -> throw new AssertionError("Unexpected world mutation/access: " + method.getName());
                };
            });
        }
    }
    @Test void installPreservesExistingServerDefaults() {
        FakeWorld on = new FakeWorld(true), off = new FakeWorld(false);
        PvpControl pvp = new PvpControl(null);
        pvp.apply(on.api); pvp.apply(off.api);
        assertTrue(on.enabled); assertFalse(off.enabled); assertEquals(0, on.writes + off.writes);
    }
    @Test void commandsApplyToEveryWorldAndRestoreOriginalSettingsOnDisable() {
        FakeWorld on = new FakeWorld(true), off = new FakeWorld(false);
        PvpControl pvp = new PvpControl(null);
        pvp.set(false, List.of(on.api, off.api)); assertFalse(on.enabled); assertFalse(off.enabled);
        pvp.set(true, List.of(on.api, off.api)); assertTrue(on.enabled); assertTrue(off.enabled);
        pvp.restore(); assertTrue(on.enabled); assertFalse(off.enabled);
    }
    @Test void persistedOverrideAppliesToLaterWorlds() {
        FakeWorld world = new FakeWorld(true);
        PvpControl pvp = new PvpControl(false);
        pvp.apply(world.api); assertFalse(world.enabled);
        assertEquals(Boolean.FALSE, pvp.configured());
        pvp.restore(); assertTrue(world.enabled);
    }
    @Test void unloadedWorldIsNotMutatedDuringCleanup() {
        FakeWorld world = new FakeWorld(true);
        PvpControl pvp = new PvpControl(false);
        pvp.apply(world.api); pvp.unload(world.api); pvp.restore();
        assertEquals(1, world.writes);
    }
}
