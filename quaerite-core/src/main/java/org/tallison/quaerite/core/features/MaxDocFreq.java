package org.tallison.quaerite.core.features;

public class MaxDocFreq extends IntFeature {
    private final static String NAME = "maxdocfreq";

    public MaxDocFreq(int value) {
        super(NAME, value);
    }

    @Override
    public MaxDocFreq deepCopy() {
        return new MaxDocFreq(getValue());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MaxDocFreq)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
