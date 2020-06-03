package org.tallison.quaerite.core.features;

public class MinTermFreq extends IntFeature {
    private final static String NAME = "minTermFreq";

    public MinTermFreq(int value) {
        super(NAME, value);
    }

    @Override
    public MinTermFreq deepCopy() {
        return new MinTermFreq(getValue());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MinTermFreq)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
