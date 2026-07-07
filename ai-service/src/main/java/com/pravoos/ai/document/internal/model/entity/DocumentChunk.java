package com.pravoos.ai.document.internal.model.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    public static final int EMBEDDING_DIMENSIONS = 1536;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private int chunkIndex;

    @Column(name = "embedding", columnDefinition = "vector(1536)")
    @Type(PgVectorType.class)
    private float[] embedding;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }

    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }

    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
