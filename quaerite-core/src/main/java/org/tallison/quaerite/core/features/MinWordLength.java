package org.tallison.quaerite.core.features;

public class MinWordLength extends IntFeature {
    private final static String NAME = "minWordLength";

    public MinWordLength(int value) {
        super(NAME, value);
    }

    @Override
    public MinWordLength deepCopy() {
        return new MinWordLength(getValue());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MinWordLength)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
