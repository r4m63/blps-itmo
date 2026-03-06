package blps.itmo.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(nullable = false)
    private String uploaderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AttachmentType type;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public Attachment() {
    }

    public Attachment(Claim claim, String uploaderId, AttachmentType type, String url, OffsetDateTime createdAt) {
        this.claim = claim;
        this.uploaderId = uploaderId;
        this.type = type;
        this.url = url;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Claim getClaim() {
        return claim;
    }

    public String getUploaderId() {
        return uploaderId;
    }

    public AttachmentType getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
