package blps.itmo.claim.service;

import blps.itmo.claim.api.dto.AttachmentConfirmResponse;
import blps.itmo.claim.api.dto.AttachmentInitRequest;
import blps.itmo.claim.api.dto.AttachmentInitResponse;
import blps.itmo.claim.domain.ClaimAttachment;
import blps.itmo.claim.repository.ClaimAttachmentRepository;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentService {

    private final ClaimAttachmentRepository attachmentRepository;
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Transactional
    public AttachmentInitResponse initAttachment(Integer userId, AttachmentInitRequest req) {
        ensureBucket();
        ClaimAttachment attachment = new ClaimAttachment();
        attachment.setUploadedBy(userId);
        attachment.setPurpose(req.purpose());
        attachment.setFileName(req.fileName());
        attachment.setContentType(req.contentType());
        attachment.setSizeBytes(req.sizeBytes());
        attachment.setObjectKey(UUID.randomUUID().toString());
        ClaimAttachment saved = attachmentRepository.save(attachment);

        String url;
        try {
            GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(saved.getObjectKey())
                    .expiry(60 * 30)
                    .build();
            url = minioClient.getPresignedObjectUrl(args);
        } catch (Exception e) {
            throw new IllegalStateException("failed to create presigned url", e);
        }
        return new AttachmentInitResponse(saved.getId(), saved.getObjectKey(), url);
    }

    @Transactional
    public AttachmentConfirmResponse confirmAttachment(Integer attachmentId) {
        ClaimAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "attachment not found"));
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(attachment.getObjectKey())
                    .build());
        } catch (Exception e) {
            throw new ResponseStatusException(NOT_FOUND, "object not found in storage");
        }
        attachment.setUploaded(true);
        attachment.setConfirmedAt(Instant.now());
        ClaimAttachment saved = attachmentRepository.save(attachment);
        return new AttachmentConfirmResponse(saved.getId(), saved.isUploaded(), saved.getConfirmedAt());
    }

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to ensure bucket", e);
        }
    }
}
