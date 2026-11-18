package com.rubberduck.domain.document.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.rubberduck.domain.document.dto.DocumentResponse;
import com.rubberduck.domain.document.dto.DocumentSearchRequest;
import com.rubberduck.domain.document.dto.DocumentSearchResponse;
import com.rubberduck.domain.document.service.DocumentService;
import com.rubberduck.domain.user.entity.User;
import com.rubberduck.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentResponse> upload(
            @AuthenticationPrincipal User user,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.ok(documentService.upload(user, file));
    }

    @GetMapping
    public ApiResponse<List<DocumentResponse>> list(
            @AuthenticationPrincipal User user
    ) {
        return ApiResponse.ok(documentService.list(user));
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal User user,
            @PathVariable Long documentId
    ) {
        documentService.delete(user, documentId);
        return ApiResponse.ok();
    }

    @GetMapping("/{documentId}")
    public ApiResponse<DocumentResponse> get(@AuthenticationPrincipal User user, @PathVariable Long documentId) {
        return ApiResponse.ok(documentService.get(user, documentId));
    }

    @PostMapping("/{documentId}/retry")
    public ApiResponse<DocumentResponse> retry(@AuthenticationPrincipal User user, @PathVariable Long documentId) {
        return ApiResponse.ok(documentService.retry(user, documentId));
    }

    @PostMapping("/search")
    public ApiResponse<DocumentSearchResponse> search(
            @AuthenticationPrincipal User user,
            @RequestBody DocumentSearchRequest request
    ) {
        return ApiResponse.ok(documentService.search(user, request.query(), request.limit(), request.documentIds()));
    }
}
