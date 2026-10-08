package dev.drunkshyt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Configuration and trigger selection, independent of the Minecraft runtime. */
final class ShotEffects {
    static final int MAX_RULES = 64;
    private final Map<String, ShotEffectRule> rules = new LinkedHashMap<>();
    private boolean enabled;

    ShotEffects(boolean enabled, Collection<ShotEffectRule> rules) {
        this.enabled = enabled;
        rules.forEach(this::add);
    }
    boolean enabled() { return enabled; }
    void enabled(boolean enabled) { this.enabled = enabled; }
    List<ShotEffectRule> rules() { return List.copyOf(rules.values()); }
    void add(ShotEffectRule rule) {
        if (rules.containsKey(rule.id())) throw new IllegalArgumentException("Rule ID already exists. Remove it first or choose another ID.");
        if (rules.size() >= MAX_RULES) throw new IllegalArgumentException("At most " + MAX_RULES + " effect rules are allowed.");
        rules.put(rule.id(), rule);
    }
    void remove(String id) {
        if (rules.remove(id) == null) throw new IllegalArgumentException("Unknown effect rule. Use /shots effects list.");
    }
    void clear() { rules.clear(); }
    boolean active(ShotEffectRule rule) { return enabled && rules.get(rule.id()) == rule; }
    List<ShotEffectRule> triggered(ShotCount before, ShotCount after) {
        return enabled ? rules.values().stream().filter(rule -> rule.crossed(before, after)).toList() : List.of();
    }
    List<Map<String, Object>> save() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ShotEffectRule rule : rules.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rule.id()); row.put("counter", rule.metric().label()); row.put("amount", rule.threshold());
            row.put("effect", rule.effect()); row.put("seconds", rule.seconds()); row.put("level", rule.level());
            result.add(row);
        }
        return result;
    }
    static ShotEffects load(boolean enabled, List<?> data) {
        List<ShotEffectRule> rules = new ArrayList<>();
        for (Object row : data) {
            if (!(row instanceof Map<?, ?> map)) throw new IllegalArgumentException("Each effect rule must be a mapping.");
            rules.add(new ShotEffectRule(string(map, "id"), ShotEffectRule.Metric.parse(string(map, "counter")),
                    integer(map, "amount"), string(map, "effect"), integer(map, "seconds"), integer(map, "level")));
        }
        return new ShotEffects(enabled, rules);
    }
    private static String string(Map<?, ?> map, String key) {
        if (map.get(key) instanceof String value) return value;
        throw new IllegalArgumentException("Effect rule requires " + key + ".");
    }
    private static int integer(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Integer number) return number;
        if (value instanceof Long number && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) return number.intValue();
        throw new IllegalArgumentException("Effect rule requires a whole-number " + key + ".");
    }
}
