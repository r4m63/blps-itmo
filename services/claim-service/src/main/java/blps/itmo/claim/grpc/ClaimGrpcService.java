package blps.itmo.claim.grpc;

import java.util.List;

import org.springframework.stereotype.Component;

import blps.itmo.claim.controller.ClaimController;
import blps.itmo.claim.domain.ClaimTimelineEntry;
import blps.itmo.claim.service.ClaimProcessService;
import blps.itmo.grpc.AdditionalInfoRequest;
import blps.itmo.grpc.AttachmentRefDto;
import blps.itmo.grpc.ClaimAttachmentsResponse;
import blps.itmo.grpc.ClaimDto;
import blps.itmo.grpc.ClaimProcessStatusDto;
import blps.itmo.grpc.ClaimRpcServiceGrpc;
import blps.itmo.grpc.ClaimTimelineEntryDto;
import blps.itmo.grpc.ClaimTimelineResponse;
import blps.itmo.grpc.CreateClaimRequest;
import blps.itmo.grpc.GetClaimRequest;
import blps.itmo.grpc.ListMyClaimsRequest;
import blps.itmo.grpc.ListMyClaimsResponse;
import blps.itmo.grpc.RepairClaimRequest;
import blps.itmo.grpc.SupportDecisionRequest;
import blps.itmo.grpc.TenantResponseRequest;
import blps.itmo.platform.grpc.GrpcErrors;
import blps.itmo.platform.grpc.GrpcMapping;
import io.grpc.stub.StreamObserver;

@Component
public class ClaimGrpcService extends ClaimRpcServiceGrpc.ClaimRpcServiceImplBase {

    private final ClaimProcessService claimProcessService;

    public ClaimGrpcService(ClaimProcessService claimProcessService) {
        this.claimProcessService = claimProcessService;
    }

    @Override
    public void createClaim(CreateClaimRequest request, StreamObserver<ClaimDto> responseObserver) {
        try {
            ClaimController.CreateClaimRequest dto = new ClaimController.CreateClaimRequest(
                    request.getLandlordUserId() == 0 ? null : request.getLandlordUserId(),
                    request.getTenantUserId(),
                    request.getTitle(),
                    request.getDescription(),
                    GrpcMapping.money(request.getClaimedAmount()),
                    request.getCurrency(),
                    request.getAttachmentIdsList());
            responseObserver.onNext(toDto(claimProcessService.createClaim(dto, actor(request.getActorUserId()))));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    @Override
    public void getClaim(GetClaimRequest request, StreamObserver<ClaimDto> responseObserver) {
        try {
            responseObserver.onNext(toDto(claimProcessService.getClaim(request.getClaimId())));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    @Override
    public void getTimeline(GetClaimRequest request, StreamObserver<ClaimTimelineResponse> responseObserver) {
        try {
            ClaimTimelineResponse.Builder response = ClaimTimelineResponse.newBuilder();
            claimProcessService.getTimeline(request.getClaimId()).stream().map(this::toDto).forEach(response::addEntries);
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    @Override
    public void getAttachments(GetClaimRequest request, StreamObserver<ClaimAttachmentsResponse> responseObserver) {
        try {
            ClaimAttachmentsResponse.Builder response = ClaimAttachmentsResponse.newBuilder();
            claimProcessService.getAttachments(request.getClaimId()).stream().map(this::toDto).forEach(response::addAttachments);
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    @Override
    public void getProcessStatus(GetClaimRequest request, StreamObserver<ClaimProcessStatusDto> responseObserver) {
        try {
            ClaimController.ClaimProcessStatusResponse status = claimProcessService.getProcessStatus(request.getClaimId());
            ClaimProcessStatusDto.Builder response = ClaimProcessStatusDto.newBuilder()
                    .setClaimId(status.claimId())
                    .setStatus(status.status())
                    .setCorrelationId(status.correlationId())
                    .setTerminal(status.terminal());
            status.attachments().stream().map(this::toDto).forEach(response::addAttachments);
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    @Override
    public void provideAdditionalInfo(AdditionalInfoRequest request, StreamObserver<ClaimDto> responseObserver) {
        try {
            ClaimController.AdditionalInfoRequest dto = new ClaimController.AdditionalInfoRequest(
                    request.getLandlordUserId() == 0 ? null : request.getLandlordUserId(),
                    request.getComment());
            responseObserver.onNext(toDto(claimProcessService.provideAdditionalInfo(
                    request.getClaimId(), dto, actor(request.getActorUserId()))));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    @Override
    public void submitTenantResponse(TenantResponseRequest request, StreamObserver<ClaimDto> responseObserver) {
        try {
            ClaimController.TenantResponseRequest dto = new ClaimController.TenantResponseRequest(
                    request.getTenantUserId() == 0 ? null : request.getTenantUserId(),
                    request.getAgree(),
                    request.getComment());
            responseObserver.onNext(toDto(claimProcessService.submitTenantResponse(
                    request.getClaimId(), dto, actor(request.getActorUserId()))));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    @Override
    public void supportDecision(SupportDecisionRequest request, StreamObserver<ClaimDto> responseObserver) {
        try {
            ClaimController.SupportDecisionRequest dto = new ClaimController.SupportDecisionRequest(
                    request.getAdminUserId() == 0 ? null : request.getAdminUserId(),
                    request.getApplyPenalty(),
                    GrpcMapping.money(request.getPenaltyAmount()),
                    request.getPenaltyCurrency(),
                    request.getNote(),
                    request.getSimulateFailure());
            responseObserver.onNext(toDto(claimProcessService.supportDecision(
                    request.getClaimId(), dto, actor(request.getActorUserId()))));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    @Override
    public void repairReassess(RepairClaimRequest request, StreamObserver<ClaimDto> responseObserver) {
        repair(request, responseObserver, true);
    }

    @Override
    public void repairClose(RepairClaimRequest request, StreamObserver<ClaimDto> responseObserver) {
        repair(request, responseObserver, false);
    }

    @Override
    public void listMyClaims(ListMyClaimsRequest request, StreamObserver<ListMyClaimsResponse> responseObserver) {
        try {
            ListMyClaimsResponse.Builder response = ListMyClaimsResponse.newBuilder();
            claimProcessService.listMyClaims(actor(request.getActorUserId())).stream()
                    .map(this::toDto)
                    .forEach(response::addClaims);
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    private void repair(RepairClaimRequest request, StreamObserver<ClaimDto> responseObserver, boolean reassess) {
        try {
            ClaimController.ClaimResponse response = reassess
                    ? claimProcessService.repairReassess(request.getClaimId(), actor(request.getActorUserId()), request.getNote())
                    : claimProcessService.repairClose(request.getClaimId(), actor(request.getActorUserId()), request.getNote());
            responseObserver.onNext(toDto(response));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    private Long actor(long actorUserId) {
        return actorUserId == 0 ? null : actorUserId;
    }

    private ClaimDto toDto(ClaimController.ClaimResponse claim) {
        ClaimDto.Builder builder = ClaimDto.newBuilder()
                .setId(claim.id())
                .setCorrelationId(claim.correlationId())
                .setLandlordId(claim.landlordId())
                .setTenantId(claim.tenantId())
                .setStatus(claim.status())
                .setTitle(claim.title())
                .setDescription(claim.description())
                .setClaimedAmount(GrpcMapping.money(claim.claimedAmount()))
                .setCurrency(claim.currency())
                .setAssessmentAmount(GrpcMapping.money(claim.assessmentAmount()))
                .setAssessmentNotes(GrpcMapping.text(claim.assessmentNotes()))
                .setPenaltyAmount(GrpcMapping.money(claim.penaltyAmount()))
                .setPenaltyCurrency(GrpcMapping.text(claim.penaltyCurrency()))
                .setResolutionNote(GrpcMapping.text(claim.resolutionNote()))
                .setCreatedAt(GrpcMapping.timestamp(claim.createdAt()))
                .setUpdatedAt(GrpcMapping.timestamp(claim.updatedAt()))
                .setClosedAt(GrpcMapping.timestamp(claim.closedAt()))
                .setTenantAgreedValue(claim.tenantAgreed() == null ? "" : claim.tenantAgreed().toString());
        claim.attachments().stream().map(this::toDto).forEach(builder::addAttachments);
        return builder.build();
    }

    private AttachmentRefDto toDto(ClaimController.ClaimAttachmentResponse attachment) {
        return AttachmentRefDto.newBuilder()
                .setAttachmentId(attachment.attachmentId())
                .setStatus(attachment.status())
                .setFailureReason(GrpcMapping.text(attachment.failureReason()))
                .build();
    }

    private ClaimTimelineEntryDto toDto(ClaimTimelineEntry entry) {
        return ClaimTimelineEntryDto.newBuilder()
                .setId(entry.getId())
                .setClaimId(entry.getClaimId())
                .setEventType(entry.getEventType())
                .setFromStatus(entry.getFromStatus() == null ? "" : entry.getFromStatus().name())
                .setToStatus(entry.getToStatus() == null ? "" : entry.getToStatus().name())
                .setActorId(entry.getActorId() == null ? 0 : entry.getActorId())
                .setNote(GrpcMapping.text(entry.getNote()))
                .setCreatedAt(GrpcMapping.timestamp(entry.getCreatedAt()))
                .build();
    }
}
