package mine.deniilo.duels.rating;

public final class EloCalculator {

    private EloCalculator() {
    }

    public record Change(int winnerOld, int loserOld, int winnerNew, int loserNew, int winnerDelta, int loserDelta) {
    }

    public static Change winnerLoser(int winnerRating, int loserRating, EloSettings s) {
        double expected = 1.0 / (1.0 + Math.pow(10.0, (loserRating - winnerRating) / 400.0));
        int delta = (int) Math.round(s.kFactor() * (1.0 - expected));
        int loserDrop = delta;
        if (winnerRating < 0 || loserRating < 0) {
            throw new IllegalArgumentException("Рейтинг не бывает отрицательным");
        }
        int loserNew = Math.max(s.floor(), loserRating - delta);
        if (s.conserveSum()) {
            loserDrop = loserRating - loserNew;
        }
        int winnerNew = winnerRating + (s.conserveSum() ? loserDrop : delta);
        int winnerDelta = winnerNew - winnerRating;
        int loserDelta = loserNew - loserRating;
        return new Change(winnerRating, loserRating, winnerNew, loserNew, winnerDelta, loserDelta);
    }
}
