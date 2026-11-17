package com.rubberduck.domain.document.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.rubberduck.domain.auth.service.AuthService;
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

    private final AuthService authService;
    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentResponse> upload(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestPart("file") MultipartFile file
    ) {
        User user = authService.requireUser(authorization);
        return ApiResponse.ok(documentService.upload(user, file));
    }

    @GetMapping
    public ApiResponse<List<DocumentResponse>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return ApiResponse.ok(documentService.list(authService.requireUser(authorization)));
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long documentId
    ) {
        documentService.delete(authService.requireUser(authorization), documentId);
        return ApiResponse.ok();
    }

    @PostMapping("/search")
    public ApiResponse<DocumentSearchResponse> search(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody DocumentSearchRequest request
    ) {
        User user = authService.requireUser(authorization);
        return ApiResponse.ok(documentService.search(user, request.query(), request.limit()));
    }
}
