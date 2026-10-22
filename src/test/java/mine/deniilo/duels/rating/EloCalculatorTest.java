package mine.deniilo.duels.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EloCalculatorTest {

    private final EloSettings classic = new EloSettings(1000, 32, 0, false, 1);
    private final EloSettings conserve = new EloSettings(1000, 32, 0, true, 1);

    @Test
    void equalRatings() {
        EloCalculator.Change c = EloCalculator.winnerLoser(1000, 1000, classic);
        assertEquals(16, c.winnerDelta());
        assertEquals(-16, c.loserDelta());
        assertEquals(1016, c.winnerNew());
        assertEquals(984, c.loserNew());
    }

    @Test
    void favouriteWinsLittle() {
        EloCalculator.Change c = EloCalculator.winnerLoser(1200, 1000, classic);
        assertEquals(8, c.winnerDelta());
        assertEquals(-8, c.loserDelta());
    }

    @Test
    void underdogWinsMore() {
        EloCalculator.Change c = EloCalculator.winnerLoser(1000, 1200, classic);
        assertEquals(24, c.winnerDelta());
        assertEquals(-24, c.loserDelta());
    }

    @Test
    void floorKeepsLoserAtZeroAndWinnerGetsFullDeltaByDefault() {
        EloCalculator.Change c = EloCalculator.winnerLoser(0, 5, classic);
        assertEquals(0, c.loserNew());
        assertEquals(-5, c.loserDelta());
        assertEquals(16, c.winnerDelta(), "без conserve-sum победитель получает полный delta");
    }

    @Test
    void conserveSumLimitsWinnerGainToLoserLoss() {
        EloCalculator.Change c = EloCalculator.winnerLoser(0, 5, conserve);
        assertEquals(0, c.loserNew());
        assertEquals(-5, c.loserDelta());
        assertEquals(5, c.winnerDelta());
    }

    @Test
    void negativeRatingRejected() {
        assertThrows(IllegalArgumentException.class, () -> EloCalculator.winnerLoser(-1, 0, classic));
    }
}
