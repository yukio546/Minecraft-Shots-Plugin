package dev.drunkshyt;

/** Immutable counter: completed never exceeds assigned; neither may be negative. */
public record ShotCount(int total, int completed) {
    public static final int MAX = 999_999;
    public static final ShotCount ZERO = new ShotCount(0, 0);

    public ShotCount {
        if (total < 0 || total > MAX || completed < 0 || completed > total) {
            throw new IllegalArgumentException("Counts must satisfy 0 <= completed <= total <= " + MAX + ".");
        }
    }

    public int left() { return total - completed; }

    public ShotCount add(int amount) {
        if (amount < 1 || amount > MAX - total) {
            throw new IllegalArgumentException("Amount must be positive and total cannot exceed " + MAX + ".");
        }
        return new ShotCount(total + amount, completed);
    }

    public ShotCount drink(int amount) {
        if (amount < 1 || amount > left()) {
            throw new IllegalArgumentException("Enter an amount from 1 to " + left() + ".");
        }
        return new ShotCount(total, completed + amount);
    }

    public ShotCount remaining(int amount) {
        if (amount < 0 || amount > total) {
            throw new IllegalArgumentException("Remaining must be between 0 and " + total + ".");
        }
        return new ShotCount(total, total - amount);
    }

    public String display() { return bracket(total) + " shots | " + bracket(left()) + " left"; }
    public static String bracket(int value) { return "[" + (value < 10 ? "0" : "") + value + "]"; }
}
