package com.rubberduck.domain.document.service;

import java.util.Locale;
import java.util.StringJoiner;

import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private static final int DIMENSIONS = 64;

    public double[] embed(String text) {
        double[] vector = new double[DIMENSIONS];
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        String[] tokens = normalized.split("[^\\p{L}\\p{N}_]+");

        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            int index = Math.floorMod(token.hashCode(), DIMENSIONS);
            vector[index] += 1.0d + Math.min(token.length(), 12) * 0.03d;
        }

        normalize(vector);
        return vector;
    }

    public String toJson(double[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (double value : vector) {
            joiner.add(Double.toString(value));
        }
        return joiner.toString();
    }

    public double[] fromJson(String json) {
        double[] vector = new double[DIMENSIONS];
        if (json == null || json.isBlank()) {
            return vector;
        }

        String normalized = json.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            return vector;
        }

        String[] values = normalized.split(",");
        for (int i = 0; i < Math.min(values.length, DIMENSIONS); i++) {
            vector[i] = Double.parseDouble(values[i].trim());
        }
        return vector;
    }

    public double cosine(double[] left, double[] right) {
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;

        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }

        if (leftNorm == 0.0d || rightNorm == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private void normalize(double[] vector) {
        double norm = 0.0d;
        for (double value : vector) {
            norm += value * value;
        }
        if (norm == 0.0d) {
            return;
        }

        double denominator = Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / denominator;
        }
    }
}
