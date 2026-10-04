package com.ragforge.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits raw text into smaller overlapping chunks.
 *
 * Why overlap?
 * If a sentence spans across a chunk boundary, we don't want to lose the context.
 * For example, chunk 1 ends with "The refund policy states" and chunk 2 starts
 * with "that you have 30 days." Overlap ensures one chunk contains the full thought.
 */
@Component
public class TextChunker {

    // Target around ~500 words per chunk with ~50 words overlap
    private static final int CHUNK_SIZE = 500;
    private static final int OVERLAP = 50;

    public List<String> chunk(String text) {
        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }

        // Extremely naive splitting by whitespace (a real app might use LangChain4j/tokenizers)
        String[] words = text.split("\\s+");
        List<String> chunks = new ArrayList<>();

        int i = 0;
        while (i < words.length) {
            int end = Math.min(i + CHUNK_SIZE, words.length);
            
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < end; j++) {
                sb.append(words[j]).append(" ");
            }
            chunks.add(sb.toString().trim());

            // Advance by CHUNK_SIZE minus OVERLAP to create the sliding window
            i += (CHUNK_SIZE - OVERLAP);
        }

        return chunks;
    }
}
