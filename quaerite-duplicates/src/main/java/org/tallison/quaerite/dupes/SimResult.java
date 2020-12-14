package org.tallison.quaerite.dupes;

public class SimResult {

    private final int tokensA;
    private final int tokensB;
    private final double sim;

    public SimResult(int tokensA, int tokensB, double sim) {
        this.tokensA = tokensA;
        this.tokensB = tokensB;
        this.sim = sim;
    }

    public int getTokensA() {
        return tokensA;
    }

    public int getTokensB() {
        return tokensB;
    }

    public double getSim() {
        return sim;
    }
}
