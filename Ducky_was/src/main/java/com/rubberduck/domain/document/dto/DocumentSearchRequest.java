package com.rubberduck.domain.document.dto;

public record DocumentSearchRequest(
        String query,
        Integer limit
) {
}
