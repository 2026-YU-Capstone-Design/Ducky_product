package com.rubberduck.domain.document.dto;

import java.util.List;

public record DocumentSearchResponse(
        List<DocumentSearchResult> results
) {
}
