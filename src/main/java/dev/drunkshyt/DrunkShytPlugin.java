package dev.drunkshyt;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class DrunkShytPlugin extends JavaPlugin implements Listener, TabExecutor {
    private static final String TEAM_PREFIX = "dsht_";
    private final Map<UUID, PlayerRecord> records = new HashMap<>();
    private final Map<UUID, Component> originalTabNames = new HashMap<>();
    private final Map<UUID, Team> teams = new HashMap<>();
    private final ConcurrentHashMap<UUID, PlayerRecord> pending = new ConcurrentHashMap<>();
    private CounterStore store;
    private ScheduledExecutorService writer;
    private CountingRule rule;
    private PvpControl pvp;
    private ShotEffects effects;
    private ShotText messages = new ShotText(ShotText.VANILLA);
    private boolean reportedWriteError;

    @Override public void onEnable() {
        saveDefaultConfig();
        try {
            loadSettings();
            store = new CounterStore(getDataFolder().toPath().resolve("players"));
            records.putAll(store.load());
        } catch (IOException | IllegalArgumentException e) {
            getLogger().severe("Could not load configuration or player counters. Existing files were preserved; plugin disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        writer = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("DrunkShyt-save").daemon(true).factory());
        writer.scheduleWithFixedDelay(this::flushPending, 0, 250, TimeUnit.MILLISECONDS);
        // Remove our leftover teams after an interrupted shutdown.
        Bukkit.getScoreboardManager().getMainScoreboard().getTeams().stream()
                .filter(t -> t.getName().startsWith(TEAM_PREFIX)).toList().forEach(Team::unregister);
        var command = Objects.requireNonNull(getCommand("shots"));
        command.setExecutor(this);
        command.setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getWorlds().forEach(pvp::apply);
        Bukkit.getOnlinePlayers().forEach(this::setup);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (getConfig().getBoolean("action-bar", true)) Bukkit.getOnlinePlayers().forEach(this::hud);
        }, 20, 30);
        getLogger().info("Ready: mode=" + rule.mode() + ", paused=" + rule.paused()
                + ", font=" + getConfig().getString("font", "minecraft:default")
                + ", automatic join setup, quiet feedback. Loaded " + records.size() + " counters.");
    }

    @Override public void onDisable() {
        if (pvp != null) pvp.restore();
        if (writer != null) {
            writer.shutdown();
            try {
                if (writer.awaitTermination(5, TimeUnit.SECONDS)) flushPending();
                else getLogger().severe("Counter save worker did not finish in time.");
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (!pending.isEmpty()) getLogger().severe("Some counter changes could not be saved; check disk access.");
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof ShotMenu) player.closeInventory();
            if (getConfig().getBoolean("action-bar", true)) player.sendActionBar(Component.empty());
            if (originalTabNames.containsKey(player.getUniqueId())) player.playerListName(originalTabNames.get(player.getUniqueId()));
        }
        teams.values().forEach(Team::unregister);
        teams.clear();
    }

    private void loadSettings() {
        var mode = CountingRule.Mode.valueOf(getConfig().getString("mode", "DAMAGE").toUpperCase(Locale.ROOT));
        var ignored = getConfig().getStringList("ignored-damage-causes").stream()
                .map(s -> s.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        rule = new CountingRule(mode, getConfig().getBoolean("paused"), ignored);
        messages = new ShotText(Key.key(getConfig().getString("font", "minecraft:default")));
        Object configuredPvp = getConfig().get("pvp-enabled");
        if (configuredPvp != null && !(configuredPvp instanceof Boolean)) {
            throw new IllegalArgumentException("pvp-enabled must be true, false, or omitted.");
        }
        pvp = new PvpControl((Boolean) configuredPvp);
        Object enabledEffects = getConfig().get("effects.enabled", false);
        if (!(enabledEffects instanceof Boolean enabled)) throw new IllegalArgumentException("effects.enabled must be true or false.");
        Object configuredEffects = getConfig().get("effects.rules", List.of());
        if (!(configuredEffects instanceof List<?> rules)) throw new IllegalArgumentException("effects.rules must be a list.");
        effects = ShotEffects.load(enabled, rules);
        effects.rules().forEach(effect -> effectType(effect.effect()));
    }

    private void flushPending() {
        for (var entry : pending.entrySet()) {
            try {
                store.save(entry.getKey(), entry.getValue());
                pending.remove(entry.getKey(), entry.getValue()); // A newer update must stay queued.
                reportedWriteError = false;
            } catch (IOException e) {
                if (!reportedWriteError) getLogger().severe("Counter save failed. Keeping changes queued for retry; check disk access.");
                reportedWriteError = true;
            }
        }
    }

    private Component text(String value, NamedTextColor color) {
        return messages.text(value, color);
    }

    private Component summary(ShotCount count) {
        return messages.summary(count);
    }

    private void say(CommandSender sender, String value) {
        sender.sendMessage(text("[Shots] ", NamedTextColor.GOLD).append(text(value, NamedTextColor.GRAY)));
    }

    private PlayerRecord record(Player player) {
        return records.computeIfAbsent(player.getUniqueId(), ignored ->
                new PlayerRecord(player.getName(), ShotCount.ZERO, getConfig().getBoolean("feedback-default", true)));
    }

    private void setup(Player player) {
        PlayerRecord next = record(player).withName(player.getName());
        records.put(player.getUniqueId(), next);
        pending.put(player.getUniqueId(), next);
        originalTabNames.putIfAbsent(player.getUniqueId(), player.playerListName());
        refresh(player);
    }

    private void update(UUID id, PlayerRecord next, boolean positiveFeedback) {
        PlayerRecord previous = records.put(id, next);
        pending.put(id, next);
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            refresh(player);
            if (previous != null) scheduleEffects(player, effects.triggered(previous.count(), next.count()));
            if (next.feedback()) {
                player.playSound(player.getLocation(), positiveFeedback ? Sound.BLOCK_NOTE_BLOCK_PLING
                        : Sound.BLOCK_NOTE_BLOCK_HAT, 0.18f, positiveFeedback ? 1.5f : 1.2f);
                if (getConfig().getBoolean("action-bar", true)) {
                    player.sendActionBar(summary(next.count()).append(text(positiveFeedback ? "  updated" : "  +1", NamedTextColor.WHITE)));
                }
            }
        }
    }

    private void scheduleEffects(Player player, List<ShotEffectRule> triggered) {
        if (triggered.isEmpty() || !eligible(player) || player.isDead()) return;
        // Let the damage event finish before an effect can change health.
        Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline() || player.isDead() || !eligible(player)) return;
            for (ShotEffectRule effect : triggered) {
                if (!effects.active(effect) || !effect.reached(record(player).count())) continue;
                PotionEffectType type = effectType(effect.effect());
                boolean applied = player.addPotionEffect(new PotionEffect(type, type.isInstant() ? 1 : effect.seconds() * 20,
                        effect.level() - 1, false, true, true));
                if (applied) say(player, "Effect: " + effect.effect().substring("minecraft:".length()) + " " + effect.level()
                        + (type.isInstant() ? " (instant)" : " for " + effect.seconds() + "s")
                        + " at " + effect.threshold() + " " + effect.metric().label() + " shots.");
            }
        });
    }

    private void refresh(Player player) {
        PlayerRecord state = record(player);
        if (getConfig().getBoolean("name-tags", true)) {
            var board = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = teams.get(player.getUniqueId());
            Team current = board.getEntryTeam(player.getName());
            // Leave teams owned by other plugins or the server alone.
            // Paper returns a fresh wrapper when looking up an existing team.
            if (current == null || (team != null && current.getName().equals(team.getName()))) {
                if (team == null) {
                    String name = TEAM_PREFIX + player.getUniqueId().toString().replace("-", "").substring(0, 11);
                    team = board.registerNewTeam(name);
                    teams.put(player.getUniqueId(), team);
                }
                team.addEntry(player.getName());
                team.suffix(text("  ", NamedTextColor.GRAY).append(summary(state.count())));
            }
        }
        if (getConfig().getBoolean("tab-list", true)) {
            player.playerListName(messages.namedSummary(player.getName(), state.count()));
        }
        if (getConfig().getBoolean("action-bar", true)) hud(player);
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof ShotMenu menu && !menu.confirm) {
            renderMenu(player, menu);
        }
    }

    private void hud(Player player) {
        Component line = summary(record(player).count());
        if (rule.paused()) line = line.append(text("  (paused)", NamedTextColor.DARK_GRAY));
        player.sendActionBar(line);
    }

    private boolean eligible(Player player) {
        return (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                && player.hasPermission("drunkshyt.use");
    }

    private void increment(Player player) {
        PlayerRecord state = record(player);
        if (state.count().total() < ShotCount.MAX) update(player.getUniqueId(), state.withCount(state.count().add(1)), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && rule.damage(event.isCancelled(), event.getFinalDamage(),
                eligible(player), event.getCause().name())) increment(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (rule.death(event.isCancelled(), eligible(event.getEntity()))) increment(event.getEntity());
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        setup(player);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            refresh(player);
            player.sendMessage(text("[Shots] Ready. ", NamedTextColor.GOLD).append(summary(record(player).count()))
                    .append(text("  [Open controls]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/shots"))));
            say(player, "Counting: " + rule.mode().name().toLowerCase(Locale.ROOT)
                    + (rule.paused() ? " (paused)" : "") + ". /shots drank 1 records one; /shots left 3 sets remaining.");
        }, 20);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Team team = teams.remove(id);
        if (team != null) team.unregister();
        originalTabNames.remove(id);
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(this, () -> { if (event.getPlayer().isOnline()) refresh(event.getPlayer()); });
    }

    @EventHandler public void onWorldLoad(WorldLoadEvent event) { pvp.apply(event.getWorld()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onWorldUnload(WorldUnloadEvent event) { pvp.unload(event.getWorld()); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (!sender.hasPermission("drunkshyt.use")) throw new IllegalArgumentException("You do not have permission to use this counter.");
            if (args.length == 0) {
                if (sender instanceof Player player) openMenu(player, false);
                else help(sender);
                return true;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "pvp" -> pvpCommand(sender, args);
                case "effects" -> effectsCommand(sender, args);
                case "help" -> help(sender);
                case "status" -> {
                    if (args.length > 2) throw usage("/shots status [player]");
                    UUID id = args.length == 2 ? target(args[1]) : self(sender).getUniqueId();
                    PlayerRecord state = records.get(id);
                    sender.sendMessage(messages.namedSummary(state.name(), state.count()));
                }
                case "drank", "drink", "done", "left" -> {
                    boolean left = sub.equals("left");
                    if (args.length > 2 || (left && args.length != 2)) throw usage("/shots " + sub + " <amount>");
                    Player player = self(sender);
                    int amount = args.length == 2 ? number(args[1]) : 1;
                    ShotCount next = left ? record(player).count().remaining(amount) : record(player).count().drink(amount);
                    update(player.getUniqueId(), record(player).withCount(next), true);
                    sender.sendMessage(summary(next));
                }
                case "feedback" -> {
                    if (args.length > 2) throw usage("/shots feedback [on|off]");
                    Player player = self(sender);
                    boolean enabled = !record(player).feedback();
                    if (args.length == 2) {
                        if (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off")) throw usage("/shots feedback [on|off]");
                        enabled = args[1].equalsIgnoreCase("on");
                    }
                    update(player.getUniqueId(), record(player).withFeedback(enabled), true);
                    say(sender, "Quiet feedback " + (enabled ? "on." : "off."));
                }
                case "reset" -> reset(sender, args);
                case "add", "set" -> {
                    admin(sender);
                    if ((sub.equals("add") && args.length != 3) || (sub.equals("set") && args.length != 3 && args.length != 4)) {
                        throw usage("/shots add <player> <amount> or /shots set <player> <total> [left]");
                    }
                    UUID id = target(args[1]);
                    int amount = number(args[2]);
                    PlayerRecord state = records.get(id);
                    ShotCount next = sub.equals("add") ? state.count().add(amount) : new ShotCount(amount, 0)
                            .remaining(args.length == 4 ? number(args[3]) : amount);
                    update(id, state.withCount(next), true);
                    sender.sendMessage(messages.adminResult(state.name(), next));
                }
                case "mode" -> {
                    admin(sender);
                    if (args.length != 2) throw usage("/shots mode <damage|death|manual>");
                    CountingRule.Mode mode;
                    try { mode = CountingRule.Mode.valueOf(args[1].toUpperCase(Locale.ROOT)); }
                    catch (IllegalArgumentException e) { throw usage("/shots mode <damage|death|manual>"); }
                    rule = new CountingRule(mode, rule.paused(), rule.ignoredCauses());
                    getConfig().set("mode", mode.name());
                    saveConfig();
                    say(sender, "Counting mode: " + mode.name().toLowerCase(Locale.ROOT) + ".");
                    Bukkit.getOnlinePlayers().forEach(this::refresh);
                }
                case "pause", "resume" -> {
                    admin(sender);
                    if (args.length != 1) throw usage("/shots " + sub);
                    rule = new CountingRule(rule.mode(), sub.equals("pause"), rule.ignoredCauses());
                    getConfig().set("paused", rule.paused());
                    saveConfig();
                    say(sender, rule.paused() ? "Automatic counting paused." : "Automatic counting resumed.");
                    Bukkit.getOnlinePlayers().forEach(this::refresh);
                }
                default -> throw usage("Unknown command. Use /shots help.");
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(text("[Shots] " + e.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private void reset(CommandSender sender, String[] args) {
        boolean own = args.length == 1 || (args.length == 2 && args[1].equalsIgnoreCase("confirm"));
        UUID id = null;
        boolean all = false;
        boolean confirmed;
        String confirmCommand;
        if (own) {
            Player player = self(sender);
            if (!player.hasPermission("drunkshyt.reset")) throw usage("You do not have permission to reset your counter.");
            id = player.getUniqueId();
            confirmed = args.length == 2;
            confirmCommand = "/shots reset confirm";
        } else {
            admin(sender);
            if (args.length > 3 || (args.length == 3 && !args[2].equalsIgnoreCase("confirm"))) {
                throw usage("/shots reset <player|all> [confirm]");
            }
            all = args[1].equalsIgnoreCase("all");
            if (!all) id = target(args[1]);
            confirmed = args.length == 3;
            confirmCommand = "/shots reset " + args[1] + " confirm";
        }
        if (!confirmed) {
            sender.sendMessage(text("Reset " + (all ? "every saved counter" : "this counter") + " to zero? ", NamedTextColor.YELLOW)
                    .append(text("[Confirm] ", NamedTextColor.RED).clickEvent(ClickEvent.runCommand(confirmCommand)))
                    .append(messages.resetHint(own ? null : args[1])));
            return;
        }
        if (all) {
            for (UUID playerId : List.copyOf(records.keySet())) update(playerId, records.get(playerId).withCount(ShotCount.ZERO), true);
        } else update(id, records.get(id).withCount(ShotCount.ZERO), true);
        say(sender, all ? "All saved counters reset for a new round." : "Counter reset.");
    }

    private void help(CommandSender sender) {
        say(sender, "/shots opens controls. /shots status [player] shows counts.");
        say(sender, "/shots drank [amount] records completed shots; /shots left <amount> corrects remaining.");
        say(sender, "/shots feedback [on|off] controls quiet feedback; /shots reset asks for confirmation.");
        if (sender.hasPermission("drunkshyt.admin")) {
            say(sender, "Admin: /shots add <player> <amount>; /shots set <player> <total> [left].");
            say(sender, "Admin: /shots mode <damage|death|manual>; /shots pause; /shots resume.");
            say(sender, "Admin: /shots reset <player|all> [confirm]. Offline saved players are supported.");
            say(sender, "Admin: /shots pvp <on|off|status> controls combat between players in every world.");
            say(sender, "Admin: /shots effects help configures potion effects at drank/left shot thresholds.");
        }
    }

    private void pvpCommand(CommandSender sender, String[] args) {
        admin(sender);
        if (args.length > 2) throw usage("/shots pvp <on|off|status>");
        String value = args.length == 1 ? "status" : args[1].toLowerCase(Locale.ROOT);
        if (value.equals("status")) {
            say(sender, "PvP setting: " + (pvp.configured() == null ? "server default" : pvp.configured() ? "on" : "off")
                    + ". " + Bukkit.getWorlds().stream().map(world -> world.getName() + ": " + (world.getPVP() ? "on" : "off"))
                    .collect(Collectors.joining(", ")));
        } else if (value.equals("on") || value.equals("off")) {
            boolean enabled = value.equals("on");
            getConfig().set("pvp-enabled", enabled);
            saveConfig();
            pvp.set(enabled, Bukkit.getWorlds());
            say(sender, "PvP " + (enabled ? "enabled" : "disabled") + " in all worlds. Saved for restarts.");
        } else throw usage("/shots pvp <on|off|status>");
    }

    private PotionEffectType effectType(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(normalized.contains(":") ? normalized : "minecraft:" + normalized);
        PotionEffectType type = key == null || !key.getNamespace().equals("minecraft") ? null : Registry.MOB_EFFECT.get(key);
        if (type == null) throw usage("Unknown Minecraft effect. Use /shots effects types or Tab completion.");
        return type;
    }

    private List<String> effectNames() {
        return Registry.MOB_EFFECT.stream().map(type -> type.getKey().getKey()).sorted().toList();
    }

    private void saveEffects() {
        getConfig().set("effects.enabled", effects.enabled());
        getConfig().set("effects.rules", effects.save());
        saveConfig();
    }

    private void effectsCommand(CommandSender sender, String[] args) {
        admin(sender);
        String action = args.length < 2 ? "list" : args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "help" -> {
                if (args.length != 2) throw usage("/shots effects help");
                say(sender, "/shots effects add <id> <drank|left> <amount> <effect> [seconds=30] [level=1]");
                say(sender, "/shots effects list; /shots effects types; /shots effects remove <id>; /shots effects clear confirm");
                say(sender, "/shots effects on|off. Rules apply to each player's own counts, on upward crossings only.");
                say(sender, "drank/have = completed; left/havent = still owed. Rejoin and restart do not replay effects.");
            }
            case "list", "status" -> {
                if (args.length > 2) throw usage("/shots effects list");
                say(sender, "Shot effects: " + (effects.enabled() ? "on" : "off") + "; " + effects.rules().size() + " rules.");
                for (ShotEffectRule effect : effects.rules()) {
                    say(sender, effect.id() + ": " + effect.threshold() + " " + effect.metric().label() + " -> "
                            + effect.effect().substring("minecraft:".length()) + " " + effect.level()
                            + (effectType(effect.effect()).isInstant() ? " (instant)" : " for " + effect.seconds() + "s"));
                }
                if (effects.rules().isEmpty()) say(sender, "No effects configured. Use /shots effects help.");
            }
            case "types" -> {
                if (args.length != 2) throw usage("/shots effects types");
                say(sender, "Effects: " + String.join(", ", effectNames()));
            }
            case "add" -> {
                if (args.length < 6 || args.length > 8) throw usage("/shots effects add <id> <drank|left> <amount> <effect> [seconds] [level]");
                PotionEffectType type = effectType(args[5]);
                ShotEffectRule effect = new ShotEffectRule(args[2].toLowerCase(Locale.ROOT), ShotEffectRule.Metric.parse(args[3]),
                        number(args[4]), type.getKey().toString(), args.length > 6 ? number(args[6]) : 30,
                        args.length > 7 ? number(args[7]) : 1);
                effects.add(effect);
                saveEffects();
                say(sender, "Saved effect rule " + effect.id() + ". Triggers on the next upward crossing; existing counts are not replayed.");
            }
            case "remove" -> {
                if (args.length != 3) throw usage("/shots effects remove <id>");
                effects.remove(args[2].toLowerCase(Locale.ROOT));
                saveEffects();
                say(sender, "Effect rule removed. Effects already applied expire normally.");
            }
            case "clear" -> {
                if (args.length == 2) {
                    sender.sendMessage(text("Clear all effect rules? ", NamedTextColor.YELLOW)
                            .append(text("[Confirm] /shots effects clear confirm", NamedTextColor.RED)
                                    .clickEvent(ClickEvent.runCommand("/shots effects clear confirm"))));
                } else if (args.length == 3 && args[2].equalsIgnoreCase("confirm")) {
                    effects.clear(); saveEffects(); say(sender, "All effect rules cleared. Active effects expire normally.");
                } else throw usage("/shots effects clear confirm");
            }
            case "on", "off" -> {
                if (args.length != 2) throw usage("/shots effects <on|off>");
                effects.enabled(action.equals("on")); saveEffects();
                say(sender, "Shot effects " + action + ". Existing effects expire normally; counts are not replayed.");
            }
            default -> throw usage("Use /shots effects help.");
        }
    }

    private Player self(CommandSender sender) {
        if (!(sender instanceof Player player)) throw usage("Specify a player. Console can use /shots status <player>.");
        return player;
    }

    private void admin(CommandSender sender) {
        if (!sender.hasPermission("drunkshyt.admin")) throw usage("This command requires admin permission.");
    }

    private UUID target(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) { record(online); return online.getUniqueId(); }
        return records.entrySet().stream().filter(e -> e.getValue().name().equalsIgnoreCase(name))
                .map(Map.Entry::getKey).findFirst().orElseThrow(() -> usage("Unknown player. They must join once first."));
    }

    private int number(String value) {
        try {
            int number = Integer.parseInt(value);
            if (number < 0 || number > ShotCount.MAX) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException e) { throw usage("Enter a whole number between 0 and " + ShotCount.MAX + "."); }
    }

    private IllegalArgumentException usage(String message) { return new IllegalArgumentException(message); }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> choices = new ArrayList<>();
        boolean admin = sender.hasPermission("drunkshyt.admin");
        if (args.length == 1) {
            choices.addAll(List.of("status", "drank", "left", "feedback", "reset", "help"));
            if (admin) choices.addAll(List.of("add", "set", "mode", "pause", "resume", "pvp", "effects"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("feedback")) choices.addAll(List.of("on", "off"));
            if (sub.equals("mode") && admin) choices.addAll(List.of("damage", "death", "manual"));
            if (sub.equals("pvp") && admin) choices.addAll(List.of("on", "off", "status"));
            if (sub.equals("effects") && admin) choices.addAll(List.of("help", "add", "remove", "list", "types", "clear", "on", "off"));
            if (sub.equals("reset")) choices.add("confirm");
            if (sub.equals("status") || (admin && List.of("reset", "add", "set").contains(sub))) {
                records.values().forEach(r -> choices.add(r.name()));
                if (sub.equals("reset")) choices.add("all");
            }
            if (List.of("drank", "left").contains(sub)) choices.addAll(List.of("1", "3", "5"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("reset") && admin) choices.add("confirm");
        if (args.length >= 3 && args[0].equalsIgnoreCase("effects") && admin) {
            if (args.length == 3 && args[1].equalsIgnoreCase("remove")) effects.rules().forEach(effect -> choices.add(effect.id()));
            if (args.length == 3 && args[1].equalsIgnoreCase("clear")) choices.add("confirm");
            if (args[1].equalsIgnoreCase("add")) {
                if (args.length == 3) choices.addAll(List.of("drank_rule", "left_rule"));
                if (args.length == 4) choices.addAll(List.of("drank", "left"));
                if (args.length == 5) choices.addAll(List.of("1", "3", "5", "10"));
                if (args.length == 6) choices.addAll(effectNames());
                if (args.length == 7) choices.addAll(List.of("10", "30", "60"));
                if (args.length == 8) choices.addAll(List.of("1", "2", "3"));
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).distinct().sorted().toList();
    }

    private static final class ShotMenu implements InventoryHolder {
        private final UUID owner;
        private final boolean confirm;
        private Inventory inventory;
        private ShotMenu(UUID owner, boolean confirm) { this.owner = owner; this.confirm = confirm; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private ItemStack item(Material material, String name, NamedTextColor color, String... lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(text(name, color));
        meta.lore(java.util.Arrays.stream(lore).map(line -> text(line, NamedTextColor.GRAY)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private void openMenu(Player player, boolean confirm) {
        ShotMenu menu = new ShotMenu(player.getUniqueId(), confirm);
        menu.inventory = Bukkit.createInventory(menu, 27, text(confirm ? "Reset your counter?" : "Shots", NamedTextColor.GOLD));
        renderMenu(player, menu);
        player.openInventory(menu.inventory);
    }

    private void renderMenu(Player player, ShotMenu menu) {
        PlayerRecord state = record(player);
        menu.inventory.setItem(4, item(Material.PAPER, state.count().display(), NamedTextColor.GOLD,
                ShotCount.bracket(state.count().completed()) + " completed", "Counting: " + rule.mode().name().toLowerCase(Locale.ROOT)
                        + (rule.paused() ? " (paused)" : "")));
        if (menu.confirm) {
            menu.inventory.setItem(11, item(Material.LIME_DYE, "Confirm reset", NamedTextColor.GREEN, "Set your total and remaining to zero."));
            menu.inventory.setItem(15, item(Material.RED_DYE, "Cancel", NamedTextColor.RED, "Keep your current counter."));
            return;
        }
        menu.inventory.setItem(10, item(Material.HONEY_BOTTLE, "Record completed", NamedTextColor.GREEN,
                "Click: record 1 completed", "Shift-click: record 5 completed", "You can also use /shots drank <amount>"));
        menu.inventory.setItem(12, item(Material.NAME_TAG, "Set remaining", NamedTextColor.AQUA,
                "Enter your remaining count in chat.", "Example: /shots left 3", "Total stays unchanged."));
        menu.inventory.setItem(14, item(Material.NOTE_BLOCK, "Quiet feedback: " + (state.feedback() ? "ON" : "OFF"), NamedTextColor.YELLOW,
                "Click to toggle sound and brief HUD feedback.", "Your preference is saved."));
        menu.inventory.setItem(16, item(Material.REDSTONE, "Reset your counter", NamedTextColor.RED, "Opens confirmation before resetting."));
        menu.inventory.setItem(22, item(Material.BOOK, "Commands", NamedTextColor.WHITE, "/shots help", "Counts survive reconnects and restarts."));
    }

    @EventHandler public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShotMenu menu)) return;
        event.setCancelled(true); // Also blocks shift-transfer, hotbar swapping and double-click collection.
        if (!(event.getWhoClicked() instanceof Player player) || !menu.owner.equals(player.getUniqueId())
                || event.getRawSlot() < 0 || event.getRawSlot() >= menu.inventory.getSize()) return;
        int slot = event.getRawSlot();
        boolean shift = event.isShiftClick();
        Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != menu.inventory) return;
            if (menu.confirm) {
                if (slot == 11) { player.performCommand("shots reset confirm"); openMenu(player, false); }
                if (slot == 15) openMenu(player, false);
            } else {
                switch (slot) {
                    case 10 -> player.performCommand("shots drank " + (shift ? 5 : 1));
                    case 12 -> {
                        player.closeInventory();
                        player.sendMessage(text("[Shots] Click here to enter remaining: /shots left ", NamedTextColor.AQUA)
                                .clickEvent(ClickEvent.suggestCommand("/shots left ")));
                    }
                    case 14 -> player.performCommand("shots feedback");
                    case 16 -> openMenu(player, true);
                    case 22 -> { player.closeInventory(); player.performCommand("shots help"); }
                    default -> { }
                }
            }
        });
    }

    @EventHandler public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShotMenu) event.setCancelled(true);
    }
}
