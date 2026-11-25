package com.rubberduck.domain.document.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.rubberduck.domain.document.entity.DocumentChunk;

/** Scores the current user's eligible chunks using their stored text. */
@Service
public class Bm25SearchService {
    private static final double K1 = 1.2d;
    private static final double B = 0.75d;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_]+");
    private static final Pattern HANGUL_WORD = Pattern.compile("[가-힣]{2,}");
    private static final Pattern ENGLISH_WORD = Pattern.compile("[a-zA-Z]{2,}");

    private record ChunkTerms(Long id, int length, Map<String, Integer> matches) {}

    public Map<Long, Double> scores(List<DocumentChunk> chunks, String query) {
        var queryTerms = terms(query).keySet();
        if (queryTerms.isEmpty()) return Map.of();

        var corpus = new ArrayList<ChunkTerms>(chunks.size());
        var documentFrequency = new HashMap<String, Integer>();
        long totalLength = 0;
        for (DocumentChunk chunk : chunks) {
            String heading = chunk.getHeadingPath() == null ? "" : chunk.getHeadingPath();
            var frequencies = terms(heading + "\n" + chunk.getContent());
            int length = frequencies.values().stream().mapToInt(Integer::intValue).sum();
            if (length == 0) continue;
            totalLength += length;
            frequencies.keySet().retainAll(queryTerms);
            frequencies.keySet().forEach(term -> documentFrequency.merge(term, 1, Integer::sum));
            corpus.add(new ChunkTerms(chunk.getId(), length, frequencies));
        }
        if (documentFrequency.isEmpty()) return Map.of();

        double averageLength = (double) totalLength / corpus.size();
        var inverseFrequency = new HashMap<String, Double>();
        documentFrequency.forEach((term, frequency) -> inverseFrequency.put(term,
                Math.log1p((corpus.size() - frequency + 0.5d) / (frequency + 0.5d))
                        * (term.startsWith("k:") ? 0.35d : 1.0d)));
        var result = new HashMap<Long, Double>();
        for (ChunkTerms chunk : corpus) {
            if (chunk.matches().isEmpty()) continue;
            double lengthNorm = K1 * (1.0d - B + B * chunk.length() / averageLength);
            double score = chunk.matches().entrySet().stream().mapToDouble(match -> {
                int frequency = match.getValue();
                return inverseFrequency.get(match.getKey()) * frequency * (K1 + 1.0d) / (frequency + lengthNorm);
            }).sum();
            // Keep API scores within the established zero-to-one range.
            result.put(chunk.id(), score / (1.0d + score));
        }
        return result;
    }

    boolean needsEnglishQuery(List<DocumentChunk> chunks, String query, Map<Long, Double> originalScores) {
        Set<String> koreanTerms = HANGUL_WORD.matcher(query).results()
                .map(match -> match.group()).collect(Collectors.toSet());
        if (koreanTerms.isEmpty() || chunks.stream().noneMatch(chunk -> englishDominant(chunk.getContent()))) {
            return false;
        }

        Set<String> englishTerms = ENGLISH_WORD.matcher(query.toLowerCase(Locale.ROOT)).results()
                .map(match -> match.group()).collect(Collectors.toSet());
        return chunks.stream().filter(chunk -> originalScores.containsKey(chunk.getId()))
                .max(Comparator.comparingDouble((DocumentChunk chunk) -> originalScores.get(chunk.getId()))
                        .thenComparing(DocumentChunk::getId, Comparator.reverseOrder()))
                .map(chunk -> {
                    String text = chunk.getContent().toLowerCase(Locale.ROOT);
                    boolean matchesKorean = !englishDominant(text) && HANGUL_WORD.matcher(text).results()
                            .anyMatch(match -> koreanTerms.contains(match.group()));
                    long matchingEnglishTerms = ENGLISH_WORD.matcher(text).results().map(match -> match.group())
                            .filter(englishTerms::contains).distinct().limit(2).count();
                    return !matchesKorean && matchingEnglishTerms < 2;
                }).orElse(true);
    }

    private boolean englishDominant(String text) {
        long english = ENGLISH_WORD.matcher(text).results().count();
        return english > HANGUL_WORD.matcher(text).results().count();
    }

    private Map<String, Integer> terms(String text) {
        var terms = new HashMap<String, Integer>();
        var words = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (words.find()) {
            String token = words.group();
            terms.merge("w:" + token, 1, Integer::sum);
            if (HANGUL_WORD.matcher(token).matches()) {
                for (int i = 0; i < token.length() - 1; i++) {
                    terms.merge("k:" + token.substring(i, i + 2), 1, Integer::sum);
                }
            }
        }
        return terms;
    }
}
