package com.rubberduck.domain.document.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

@Component
public class DocumentChunker {

    private static final int CHUNK_SIZE = 900;
    private static final int OVERLAP_SIZE = 120;
    private static final int MAX_CHUNKS = 100;

    public List<String> split(String text) {
        if (text == null || text.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        String normalized = text.replaceAll("\\s+", " ").trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < normalized.length() && chunks.size() < MAX_CHUNKS) {
            int end = Math.min(start + CHUNK_SIZE, normalized.length());
            chunks.add(normalized.substring(start, end).trim());

            if (end == normalized.length()) {
                break;
            }
            start = Math.max(end - OVERLAP_SIZE, start + 1);
        }

        return chunks;
    }
}
