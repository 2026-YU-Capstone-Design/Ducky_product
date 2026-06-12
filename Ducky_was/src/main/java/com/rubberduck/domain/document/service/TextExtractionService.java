package com.rubberduck.domain.document.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.rubberduck.global.exception.CustomException;
import com.rubberduck.global.exception.ErrorCode;

@Service
public class TextExtractionService {

    public String extract(MultipartFile file) {
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8)
                    .replace('\u0000', ' ')
                    .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (text.isBlank()) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
            return text;
        } catch (IOException exception) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }
}
