package edu.utdallas.seers.search;

public class SearchResult<T> {

    private final T result;
    private final float score;
    private final int rank;

    public SearchResult(T result, int rank, float score) {
        this.result = result;
        this.rank = rank;
        this.score = score;
    }

    public T getResult() {
        return result;
    }

    public float getScore() {
        return score;
    }

    public int getRank() {
        return rank;
    }
}
