package blps.itmo.storage.grpc;

import org.springframework.stereotype.Component;

import blps.itmo.grpc.AttachmentDto;
import blps.itmo.grpc.ConfirmAttachmentRequest;
import blps.itmo.grpc.GetAttachmentRequest;
import blps.itmo.grpc.InitAttachmentRequest;
import blps.itmo.grpc.ListAttachmentsRequest;
import blps.itmo.grpc.ListAttachmentsResponse;
import blps.itmo.grpc.StorageRpcServiceGrpc;
import blps.itmo.platform.grpc.GrpcErrors;
import blps.itmo.platform.grpc.GrpcMapping;
import blps.itmo.storage.controller.AttachmentController;
import blps.itmo.storage.service.StorageWorkflowService;
import io.grpc.stub.StreamObserver;

@Component
public class StorageGrpcService extends StorageRpcServiceGrpc.StorageRpcServiceImplBase {

    private final StorageWorkflowService storageWorkflowService;

    public StorageGrpcService(StorageWorkflowService storageWorkflowService) {
        this.storageWorkflowService = storageWorkflowService;
    }

    @Override
    public void initAttachment(InitAttachmentRequest request, StreamObserver<AttachmentDto> responseObserver) {
        try {
            AttachmentController.InitAttachmentRequest dto = new AttachmentController.InitAttachmentRequest(
                    request.getOwnerUserId() == 0 ? null : request.getOwnerUserId(),
                    request.getOriginalFilename(),
                    request.getContentType());
            responseObserver.onNext(toDto(storageWorkflowService.init(
                    request.getActorUserId() == 0 ? null : request.getActorUserId(), dto)));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    @Override
    public void confirmAttachment(ConfirmAttachmentRequest request, StreamObserver<AttachmentDto> responseObserver) {
        try {
            responseObserver.onNext(toDto(storageWorkflowService.confirm(
                    request.getAttachmentId(),
                    request.getActorUserId() == 0 ? null : request.getActorUserId(),
                    request.getObjectKey())));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    @Override
    public void getAttachment(GetAttachmentRequest request, StreamObserver<AttachmentDto> responseObserver) {
        try {
            responseObserver.onNext(toDto(storageWorkflowService.get(request.getAttachmentId())));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    @Override
    public void listAttachments(ListAttachmentsRequest request, StreamObserver<ListAttachmentsResponse> responseObserver) {
        try {
            ListAttachmentsResponse.Builder response = ListAttachmentsResponse.newBuilder();
            storageWorkflowService.listByOwner(request.getOwnerUserId()).stream().map(this::toDto)
                    .forEach(response::addAttachments);
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    private AttachmentDto toDto(AttachmentController.AttachmentResponse attachment) {
        return AttachmentDto.newBuilder()
                .setId(attachment.id())
                .setOwnerUserId(attachment.ownerUserId())
                .setClaimId(attachment.claimId() == null ? 0 : attachment.claimId())
                .setBucketName(attachment.bucketName())
                .setObjectKey(attachment.objectKey())
                .setUploadUrl(attachment.uploadUrl())
                .setOriginalFilename(attachment.originalFilename())
                .setContentType(GrpcMapping.text(attachment.contentType()))
                .setStatus(attachment.status())
                .setFailureReason(GrpcMapping.text(attachment.failureReason()))
                .setCreatedAt(GrpcMapping.timestamp(attachment.createdAt()))
                .setUpdatedAt(GrpcMapping.timestamp(attachment.updatedAt()))
                .build();
    }
}
