package com.jitendra.ai.sessionvault.rag;

public final class EmbeddingVectorValidator {

    private EmbeddingVectorValidator() {
    }

    public static void validate(float[] vector, int expectedDimension, String description) {
        if (vector == null || vector.length != expectedDimension) {
            int actualDimension = vector == null ? 0 : vector.length;
            throw new IllegalStateException(description + " embedding dimension " + actualDimension
                    + " does not match configured database dimension " + expectedDimension);
        }
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalStateException(description + " embedding contains a non-finite value");
            }
        }
    }
}
