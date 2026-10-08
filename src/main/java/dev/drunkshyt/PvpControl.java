package dev.drunkshyt;

import org.bukkit.World;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Uses Minecraft's world PvP switch, leaving environmental damage alone. */
final class PvpControl {
    private record Original(World world, boolean enabled) {}
    private final Map<UUID, Original> originals = new HashMap<>();
    private Boolean enabled;

    PvpControl(Boolean enabled) { this.enabled = enabled; }
    Boolean configured() { return enabled; }

    void apply(World world) {
        if (enabled == null) return;
        originals.putIfAbsent(world.getUID(), new Original(world, world.getPVP()));
        world.setPVP(enabled);
    }

    void set(boolean enabled, Iterable<World> worlds) {
        this.enabled = enabled;
        worlds.forEach(this::apply);
    }

    void unload(World world) { originals.remove(world.getUID()); }

    void restore() {
        originals.values().forEach(original -> original.world().setPVP(original.enabled()));
        originals.clear();
    }
}
