package dev.drunkshyt;

public record PlayerRecord(String name, ShotCount count, boolean feedback) {
    public PlayerRecord withCount(ShotCount next) { return new PlayerRecord(name, next, feedback); }
    public PlayerRecord withName(String next) { return new PlayerRecord(next, count, feedback); }
    public PlayerRecord withFeedback(boolean next) { return new PlayerRecord(name, count, next); }
}
