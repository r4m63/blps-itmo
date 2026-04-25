package blps.itmo.service;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import blps.itmo.entity.business.AttachmentPurpose;
import blps.itmo.entity.business.Claim;
import blps.itmo.entity.business.ClaimAttachment;
import blps.itmo.entity.business.ClaimMessage;
import blps.itmo.exception.BadRequestException;
import blps.itmo.exception.ResourceNotFoundException;
import blps.itmo.repository.business.ClaimAttachmentRepository;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.MinioException;

@Service
public class MinioService {

    private final MinioClient minioClient;
    private final ClaimAttachmentRepository attachmentRepository;
    private final String bucket;
    private final Duration presignTtl;
    private final TransactionTemplate txTemplate;

    public MinioService(MinioClient minioClient,
            ClaimAttachmentRepository attachmentRepository,
            @Qualifier("jtaTransactionTemplate") TransactionTemplate txTemplate,
            @Value("${minio.bucket}") String bucket,
            @Value("${minio.presign-ttl-seconds:900}") long ttlSeconds) {
        this.minioClient = minioClient;
        this.attachmentRepository = attachmentRepository;
        this.bucket = bucket;
        this.presignTtl = Duration.ofSeconds(ttlSeconds);
        this.txTemplate = txTemplate;
        ensureBucket();
    }

    public Duration getPresignTtl() {
        return presignTtl;
    }

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to ensure MinIO bucket", e);
        }
    }

    public String generateObjectKey(String fileName) {
        String clean = fileName.replace("\\", "/");
        String namePart = clean.contains("/") ? clean.substring(clean.lastIndexOf('/') + 1) : clean;
        return "claims/" + UUID.randomUUID() + "/" + namePart;
    }

    public String presignPutUrl(String objectKey, String contentType) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry((int) presignTtl.getSeconds())
                            .build());
        } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Failed to presign url", e);
        }
    }

    public StatObjectResponse stat(String objectKey) {
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to stat object: " + objectKey, e);
        }
    }

    public String presignGetUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry((int) presignTtl.getSeconds())
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to presign download url", e);
        }
    }

    public AttachmentInitResult initAttachment(String fileName,
            String contentType,
            AttachmentPurpose purpose,
            Long uploadedById) {
        return txTemplate.execute(status -> {
            String objectKey = generateObjectKey(fileName);
            OffsetDateTime now = OffsetDateTime.now();
            ClaimAttachment attachment = ClaimAttachment.builder()
                    .objectKey(objectKey)
                    .fileName(fileName)
                    .contentType(contentType)
                    .purpose(purpose == null ? AttachmentPurpose.DAMAGE_EVIDENCE : purpose)
                    .uploadedById(uploadedById)
                    .uploaded(false)
                    .createdAt(now)
                    .build();
            attachmentRepository.save(attachment);
            String url = presignPutUrl(objectKey, contentType);
            return new AttachmentInitResult(attachment.getId(), objectKey, url, now.plus(presignTtl));
        });
    }

    public ClaimAttachment confirmUpload(String objectKey) {
        return txTemplate.execute(status -> {
            ClaimAttachment attachment = attachmentRepository.findByObjectKey(objectKey)
                    .orElseThrow(() -> ResourceNotFoundException.of(ClaimAttachment.class, "objectKey", objectKey));
            StatObjectResponse stat = stat(objectKey);
            attachment.setSizeBytes(stat.size());
            attachment.setContentType(stat.contentType());
            attachment.setUploaded(true);
            attachment.setConfirmedAt(OffsetDateTime.now());
            attachmentRepository.save(attachment);
            return attachment;
        });
    }

    public void attachExistingObjectsToClaim(Claim claim, Long uploadedById, List<String> objectKeys) {
        attachExistingObjectsToClaim(claim, uploadedById, objectKeys, null);
    }

    public void attachExistingObjectsToClaim(Claim claim, Long uploadedById, List<String> objectKeys,
            ClaimMessage message) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        List<String> normalizedKeys = normalizeObjectKeys(objectKeys);
        List<ClaimAttachment> attachments = attachmentRepository.findByObjectKeyIn(normalizedKeys);
        if (attachments.size() != normalizedKeys.size()) {
            throw new BadRequestException("Some attachments not found or not initialized");
        }
        for (ClaimAttachment att : attachments) {
            if (!Boolean.TRUE.equals(att.getUploaded()) || att.getSizeBytes() == null) {
                StatObjectResponse stat = stat(att.getObjectKey());
                att.setSizeBytes(stat.size());
                att.setContentType(stat.contentType());
                att.setUploaded(true);
                att.setConfirmedAt(OffsetDateTime.now());
            }
            att.setClaim(claim);
            att.setUploadedById(uploadedById);
            att.setMessage(message);
        }
        attachmentRepository.saveAll(attachments);
    }

    public List<String> normalizeObjectKeys(List<String> keysOrUrls) {
        if (keysOrUrls == null || keysOrUrls.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return keysOrUrls.stream()
                .map(this::normalizeObjectKey)
                .toList();
    }

    public String normalizeObjectKey(String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.isBlank()) {
            return keyOrUrl;
        }
        String trimmed = keyOrUrl.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return trimmed;
        }
        try {
            java.net.URI uri = java.net.URI.create(trimmed);
            String path = uri.getPath();
            if (path == null) {
                return trimmed;
            }
            String normalized = path.startsWith("/") ? path.substring(1) : path;
            if (normalized.startsWith(bucket + "/")) {
                normalized = normalized.substring(bucket.length() + 1);
            }
            return normalized;
        } catch (Exception e) {
            return trimmed;
        }
    }

    public record AttachmentInitResult(Long attachmentId, String objectKey, String uploadUrl,
            OffsetDateTime expiresAt) {
    }
}
