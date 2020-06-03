package org.tallison.quaerite.core.features;

public class MaxWordLength extends IntFeature {
    private final static String NAME = "maxWordLength";

    public MaxWordLength(int value) {
        super(NAME, value);
    }

    @Override
    public MaxWordLength deepCopy() {
        return new MaxWordLength(getValue());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MaxWordLength)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
