package com.rubberduck.domain.document.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Offline lexical vectors, NOT neural/semantic embeddings. No hash collisions. */
@Service
public class SparseEmbeddingService {
    public static final String MODEL = "lexical-ko-en-v1";
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_]+");
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Double> embed(String text) {
        Map<String, Double> vector = new HashMap<>();
        var matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            vector.merge("w:" + token, 1.0, Double::sum);
            if (token.matches("[가-힣]{2,}")) {
                for (int i = 0; i + 1 < token.length(); i++) {
                    vector.merge("k:" + token.substring(i, i + 2), 0.35, Double::sum);
                }
            }
        }
        double norm = Math.sqrt(vector.values().stream().mapToDouble(v -> v * v).sum());
        if (norm > 0) vector.replaceAll((key, value) -> value / norm);
        return vector;
    }

    public String serialize(String text) { return mapper.writeValueAsString(embed(text)); }

    public double score(Map<String, Double> query, String stored) {
        Map<String, Double> vector = mapper.readValue(stored, new TypeReference<Map<String, Double>>() {});
        return query.entrySet().stream().mapToDouble(e -> e.getValue() * vector.getOrDefault(e.getKey(), 0.0)).sum();
    }
}
