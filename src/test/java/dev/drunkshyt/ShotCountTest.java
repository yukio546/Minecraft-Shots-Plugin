package dev.drunkshyt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ShotCountTest {
    @Test void damageAfterDrinkingAddsOneToBothTotalAndRemaining() {
        ShotCount count = new ShotCount(10, 7).add(1);
        assertEquals(11, count.total());
        assertEquals(7, count.completed());
        assertEquals(4, count.left());
    }

    @Test void completedInputNeverChangesAssignedTotal() {
        assertEquals(new ShotCount(10, 7), new ShotCount(10, 0).drink(7));
    }

    @Test void remainingInputCanCorrectAnAccidentalCompletedInput() {
        assertEquals(new ShotCount(10, 7), new ShotCount(10, 9).remaining(3));
    }

    @Test void completingLastShotAndResetBothReachZeroRemaining() {
        assertEquals(0, new ShotCount(10, 9).drink(1).left());
        assertEquals("[00] shots | [00] left", ShotCount.ZERO.display());
    }

    @Test void exampleAndLargeCountsAreNotTruncated() {
        assertEquals("[10] shots | [03] left", new ShotCount(10, 7).display());
        assertEquals("[123] shots | [105] left", new ShotCount(123, 18).display());
    }

    @ParameterizedTest @CsvSource({"-1,0", "1,-1", "1,2", "1000000,0"})
    void invalidSavedCountsAreRejected(int total, int completed) {
        assertThrows(IllegalArgumentException.class, () -> new ShotCount(total, completed));
    }

    @ParameterizedTest @CsvSource({"-1", "0", "4", "2147483647"})
    void completedCannotExceedRemaining(int amount) {
        assertThrows(IllegalArgumentException.class, () -> new ShotCount(10, 7).drink(amount));
    }

    @Test void remainingMustFitInsideTotal() {
        assertThrows(IllegalArgumentException.class, () -> new ShotCount(10, 7).remaining(-1));
        assertThrows(IllegalArgumentException.class, () -> new ShotCount(10, 7).remaining(11));
    }

    @Test void addRejectsOverflowAndNonPositiveAmounts() {
        assertThrows(IllegalArgumentException.class, () -> ShotCount.ZERO.add(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> ShotCount.ZERO.add(0));
        assertThrows(IllegalArgumentException.class, () -> ShotCount.ZERO.add(-1));
        assertThrows(IllegalArgumentException.class, () -> new ShotCount(ShotCount.MAX, 0).add(1));
    }
}
