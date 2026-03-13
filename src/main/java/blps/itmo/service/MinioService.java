package blps.itmo.service;

import blps.itmo.entity.AttachmentPurpose;
import blps.itmo.entity.Claim;
import blps.itmo.entity.ClaimAttachment;
import blps.itmo.entity.User;
import blps.itmo.repository.ClaimAttachmentRepository;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.MakeBucketArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.MinioException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MinioService {

    private final MinioClient minioClient;
    private final ClaimAttachmentRepository attachmentRepository;
    private final String bucket;
    private final Duration presignTtl;

    public MinioService(MinioClient minioClient,
                        ClaimAttachmentRepository attachmentRepository,
                        @Value("${minio.bucket}") String bucket,
                        @Value("${minio.presign-ttl-seconds:900}") long ttlSeconds) {
        this.minioClient = minioClient;
        this.attachmentRepository = attachmentRepository;
        this.bucket = bucket;
        this.presignTtl = Duration.ofSeconds(ttlSeconds);
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
                            .build()
            );
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

    public void attachObjectsToClaim(blps.itmo.entity.Claim claim,
                                     User uploader,
                                     List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        for (String key : objectKeys) {
            StatObjectResponse stat = stat(key);
            ClaimAttachment attachment = ClaimAttachment.builder()
                    .claim(claim)
                    .message(null)
                    .uploadedBy(uploader)
                    .purpose(AttachmentPurpose.DAMAGE_EVIDENCE)
                    .objectKey(key)
                    .fileName(stat.object())
                    .contentType(stat.contentType())
                    .sizeBytes(stat.size())
                    .createdAt(OffsetDateTime.now())
                    .build();
            attachmentRepository.save(attachment);
        }
    }

    public AttachmentInitResult initAttachment(String fileName,
                                               String contentType,
                                               AttachmentPurpose purpose,
                                               User uploader) {
        String objectKey = generateObjectKey(fileName);
        OffsetDateTime now = OffsetDateTime.now();
        ClaimAttachment attachment = ClaimAttachment.builder()
                .objectKey(objectKey)
                .fileName(fileName)
                .contentType(contentType)
                .purpose(purpose == null ? AttachmentPurpose.DAMAGE_EVIDENCE : purpose)
                .uploadedBy(uploader)
                .uploaded(false)
                .createdAt(now)
                .build();
        attachmentRepository.save(attachment);
        String url = presignPutUrl(objectKey, contentType);
        return new AttachmentInitResult(attachment.getId(), objectKey, url, now.plus(presignTtl));
    }

    public ClaimAttachment confirmUpload(String objectKey) {
        ClaimAttachment attachment = attachmentRepository.findByObjectKey(objectKey)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found for objectKey " + objectKey));
        StatObjectResponse stat = stat(objectKey);
        attachment.setSizeBytes(stat.size());
        attachment.setContentType(stat.contentType());
        attachment.setUploaded(true);
        attachment.setConfirmedAt(OffsetDateTime.now());
        attachmentRepository.save(attachment);
        return attachment;
    }

    public void attachExistingObjectsToClaim(Claim claim,
                                             User uploader,
                                             List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        List<ClaimAttachment> attachments = attachmentRepository.findByObjectKeyIn(objectKeys);
        if (attachments.size() != objectKeys.size()) {
            throw new IllegalArgumentException("Some attachments not found or not initialized");
        }
        for (ClaimAttachment att : attachments) {
            if (!Boolean.TRUE.equals(att.getUploaded())) {
                throw new IllegalStateException("Attachment " + att.getObjectKey() + " not confirmed/uploaded");
            }
            att.setClaim(claim);
            att.setUploadedBy(uploader);
            attachmentRepository.save(att);
        }
    }

    public record AttachmentInitResult(Long attachmentId, String objectKey, String uploadUrl, OffsetDateTime expiresAt) {}
}
