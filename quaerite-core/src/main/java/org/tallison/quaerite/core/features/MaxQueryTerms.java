package org.tallison.quaerite.core.features;

public class MaxQueryTerms extends IntFeature {
    private final static String NAME = "maxQueryTerms";

    public MaxQueryTerms(int value) {
        super(NAME, value);
    }

    @Override
    public MaxQueryTerms deepCopy() {
        return new MaxQueryTerms(getValue());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MaxQueryTerms)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
