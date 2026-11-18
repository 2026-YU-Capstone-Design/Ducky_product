package com.rubberduck.domain.document.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Object keys are server generated; uploaded filenames never become paths. */
@Component
public class LocalDocumentStore {
    private final Path root;

    public LocalDocumentStore(@Value("${document.storage-root:./data/documents}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public String putSource(Long userId, Long documentId, byte[] bytes) throws IOException {
        String key = userId + "/" + documentId + "/source";
        write(key, bytes);
        return key;
    }

    public byte[] read(String key) throws IOException {
        return Files.readAllBytes(resolve(key));
    }

    public void write(String key, byte[] bytes) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "artifact-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void deleteArtifacts(String sourceKey) throws IOException {
        if (sourceKey == null) return;
        Path directory = resolve(sourceKey).getParent();
        // Delete only the fixed artifacts owned by this document, never recurse.
        for (String name : new String[]{"source", "document.md", "canonical.jsonl", "result.json"}) {
            Files.deleteIfExists(directory.resolve(name));
        }
        Files.deleteIfExists(directory);
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new IllegalArgumentException("Invalid object key");
        return path;
    }
}
