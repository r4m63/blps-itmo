package blps.itmo.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

@Entity
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String initiatorId; // арендодатель

    @Column(nullable = false)
    private String respondentId; // арендатор

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(nullable = false, length = 4096)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private ClaimStatus status;

    private Boolean enoughData;
    private Boolean groundsForPenalty;
    private Boolean approvePenalty;
    private Boolean penaltyApplied;
    private Boolean respondentTimeout;

    @Column(length = 4096)
    private String respondentComment;

    @Column(length = 4096)
    private String supportComment;

    private OffsetDateTime responseDueAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Attachment> attachments = new ArrayList<>();

    public Claim() {
    }

    public Claim(String initiatorId, String respondentId, BigDecimal amount, String currency, String reason,
            OffsetDateTime createdAt) {
        this.initiatorId = initiatorId;
        this.respondentId = respondentId;
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
        this.status = ClaimStatus.DATA_REVIEW; // сразу на проверку полноты
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    public void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getInitiatorId() {
        return initiatorId;
    }

    public String getRespondentId() {
        return respondentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReason() {
        return reason;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public Boolean getEnoughData() {
        return enoughData;
    }

    public Boolean getGroundsForPenalty() {
        return groundsForPenalty;
    }

    public Boolean getApprovePenalty() {
        return approvePenalty;
    }

    public Boolean getPenaltyApplied() {
        return penaltyApplied;
    }

    public Boolean getRespondentTimeout() {
        return respondentTimeout;
    }

    public String getRespondentComment() {
        return respondentComment;
    }

    public String getSupportComment() {
        return supportComment;
    }

    public OffsetDateTime getResponseDueAt() {
        return responseDueAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    // domain transitions
    public void setEnoughData(boolean enough) {
        this.enoughData = enough;
        if (enough) {
            this.status = ClaimStatus.RISK_REVIEW;
        } else {
            this.status = ClaimStatus.NEED_ADDITIONAL_DATA;
        }
    }

    public void setGroundsForPenalty(boolean grounds, OffsetDateTime now) {
        this.groundsForPenalty = grounds;
        if (grounds) {
            this.status = ClaimStatus.WAITING_RESPONDENT;
            this.responseDueAt = now.plusDays(3);
        } else {
            this.status = ClaimStatus.CLOSED_REJECT;
            this.penaltyApplied = false;
            this.approvePenalty = false;
        }
    }

    public void addAttachment(Attachment attachment) {
        attachments.add(attachment);
    }

    public void moveToDataReview() {
        this.status = ClaimStatus.DATA_REVIEW;
    }

    public void respondentProvided(String comment) {
        this.respondentComment = comment;
        this.respondentTimeout = false;
        this.status = ClaimStatus.SUPPORT_REVIEW;
    }

    public void markTimeout() {
        this.respondentTimeout = true;
        this.status = ClaimStatus.SUPPORT_REVIEW;
    }

    public void supportDecision(boolean approve, String comment) {
        this.approvePenalty = approve;
        this.supportComment = comment;
        if (approve) {
            this.status = ClaimStatus.CLOSED_PENALTY;
            this.penaltyApplied = true;
        } else {
            this.status = ClaimStatus.CLOSED_REJECT;
            this.penaltyApplied = false;
        }
    }
}
