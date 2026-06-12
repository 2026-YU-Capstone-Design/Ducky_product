package com.rubberduck.domain.document.entity;

import java.time.Instant;

import com.rubberduck.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "learning_documents")
public class LearningDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(nullable = false, length = 40)
    private String kind;

    @Column(nullable = false, length = 40)
    private String status = "available";

    @Column(nullable = false, length = 40)
    private String source = "direct";

    @Column(name = "rag_enabled", nullable = false)
    private boolean ragEnabled = true;

    @Column(name = "indexing_status", nullable = false, length = 40)
    private String indexingStatus = "pending";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static LearningDocument create(
            User user,
            String title,
            String fileName,
            String contentType,
            long fileSize,
            String kind
    ) {
        LearningDocument document = new LearningDocument();
        document.setUser(user);
        document.setTitle(title);
        document.setFileName(fileName);
        document.setContentType(contentType);
        document.setFileSize(fileSize);
        document.setKind(kind);
        return document;
    }

    public void markIndexed() {
        indexingStatus = "indexed";
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
