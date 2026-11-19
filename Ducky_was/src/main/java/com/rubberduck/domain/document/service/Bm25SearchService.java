package com.rubberduck.domain.document.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.rubberduck.domain.document.entity.DocumentChunk;
import org.springframework.stereotype.Service;

/**
 * Small-corpus, request-local BM25. The caller supplies only the owner's indexed
 * RAG-enabled chunks; document selection must not change collection statistics.
 * Rebuilding statistics avoids stale caches after uploads, retries and deletes.
 * IDF and parameters: Apache Lucene BM25Similarity (k1=1.2, b=0.75).
 * This is not a persistent inverted index or a semantic embedding model.
 */
@Service
public class Bm25SearchService {
    public static final String MODEL = "bm25-ko-en-v1";
    public static final double K1 = 1.2;
    public static final double B = 0.75;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_]+");

    public Map<Long, Double> scores(List<DocumentChunk> corpus, String query) {
        Map<String, Integer> queryTerms = terms(query);
        Map<Long, Map<String, Integer>> frequencies = new HashMap<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        long totalLength = 0;
        for (DocumentChunk chunk : corpus) {
            // Same heading + content input as the current lexical pipeline.
            String heading = chunk.getHeadingPath() == null ? "" : chunk.getHeadingPath();
            Map<String, Integer> counts = terms(heading + "\n" + chunk.getContent());
            if (counts.isEmpty()) continue;
            frequencies.put(chunk.getId(), counts);
            totalLength += counts.values().stream().mapToInt(Integer::intValue).sum();
            counts.keySet().forEach(term -> documentFrequency.merge(term, 1, Integer::sum));
        }
        Map<Long, Double> scores = new HashMap<>();
        if (frequencies.isEmpty() || queryTerms.isEmpty()) return scores;
        int count = frequencies.size();
        double averageLength = (double) totalLength / count;
        frequencies.forEach((id, counts) -> {
            int length = counts.values().stream().mapToInt(Integer::intValue).sum();
            double lengthNorm = K1 * (1 - B + B * length / averageLength);
            double score = 0;
            for (String term : queryTerms.keySet()) {
                int frequency = counts.getOrDefault(term, 0);
                if (frequency == 0) continue;
                int df = documentFrequency.get(term);
                double idf = Math.log1p((count - df + 0.5) / (df + 0.5));
                double weight = term.startsWith("k:") ? 0.35 : 1.0;
                score += weight * idf * frequency * (K1 + 1) / (frequency + lengthNorm);
            }
            scores.put(id, score);
        });
        return scores;
    }

    private Map<String, Integer> terms(String text) {
        Map<String, Integer> result = new HashMap<>();
        var matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            result.merge("w:" + token, 1, Integer::sum);
            if (token.matches("[가-힣]{2,}")) {
                for (int i = 0; i + 1 < token.length(); i++) {
                    result.merge("k:" + token.substring(i, i + 2), 1, Integer::sum);
                }
            }
        }
        return result;
    }
}
