package org.tallison.quaerite.core.features;

public class MinDocFreq extends IntFeature {
    private final static String NAME = "minDocFreq";

    public MinDocFreq(int value) {
        super(NAME, value);
    }

    @Override
    public MinDocFreq deepCopy() {
        return new MinDocFreq(getValue());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MinDocFreq)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
