package com.aoneng.rag.infra.vector;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates a deterministic sparse BM25-style vector. Hashing keeps the vocabulary
 * bounded while preserving identical token IDs between indexing and querying.
 */
@Component
public class Bm25SparseVectorizer {
    private static final Pattern TOKEN = Pattern.compile("[\\p{IsHan}]|[A-Za-z0-9_]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final int HASH_BUCKETS = 1_000_000;
    private static final double K1 = 1.2D;

    public SortedMap<Long, Float> vectorize(String text) {
        if (text == null || text.isBlank()) return new TreeMap<>();
        Map<Long, Integer> frequencies = new TreeMap<>();
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        int length = 0;
        while (matcher.find()) {
            String token = matcher.group();
            long id = bucket(token);
            frequencies.merge(id, 1, Integer::sum);
            length++;
        }
        SortedMap<Long, Float> result = new TreeMap<>();
        if (length == 0) return result;
        double averageLength = 32D;
        double lengthNorm = (1D - 0.75D) + 0.75D * length / averageLength;
        for (Map.Entry<Long, Integer> entry : frequencies.entrySet()) {
            double tf = entry.getValue();
            double weight = ((K1 + 1D) * tf) / (K1 * lengthNorm + tf);
            result.put(entry.getKey(), (float) weight);
        }
        return result;
    }

    private long bucket(String token) {
        return Integer.toUnsignedLong(token.hashCode()) % HASH_BUCKETS;
    }
}
