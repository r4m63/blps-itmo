package blps.itmo.gateway.api.http;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import blps.itmo.gateway.grpc.GatewayGrpcClients;
import blps.itmo.gateway.dto.GatewayDtoMapper;
import blps.itmo.gateway.dto.GatewayDtos.AdditionalInfoHttpRequest;
import blps.itmo.gateway.dto.GatewayDtos.ClaimAttachmentHttpResponse;
import blps.itmo.gateway.dto.GatewayDtos.ClaimHttpResponse;
import blps.itmo.gateway.dto.GatewayDtos.ClaimProcessStatusHttpResponse;
import blps.itmo.gateway.dto.GatewayDtos.ClaimTimelineHttpResponse;
import blps.itmo.gateway.dto.GatewayDtos.CreateClaimHttpRequest;
import blps.itmo.gateway.dto.GatewayDtos.RepairHttpRequest;
import blps.itmo.gateway.dto.GatewayDtos.SupportDecisionHttpRequest;
import blps.itmo.gateway.dto.GatewayDtos.TenantResponseHttpRequest;
import blps.itmo.grpc.AdditionalInfoRequest;
import blps.itmo.grpc.CreateClaimRequest;
import blps.itmo.grpc.GetClaimRequest;
import blps.itmo.grpc.ListMyClaimsRequest;
import blps.itmo.grpc.RepairClaimRequest;
import blps.itmo.grpc.SupportDecisionRequest;
import blps.itmo.grpc.TenantResponseRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/claims")
public class ClaimGatewayController extends GatewayControllerSupport {

    public ClaimGatewayController(GatewayGrpcClients grpcClients) {
        super(grpcClients);
    }

    @PostMapping
    public ClaimHttpResponse createClaim(@Valid @RequestBody CreateClaimHttpRequest request,
            HttpServletRequest servletRequest) {
        requireRole(servletRequest, "LANDLORD");
        long actorUserId = actorUserId(servletRequest);
        CreateClaimRequest.Builder grpcRequest = CreateClaimRequest.newBuilder()
                .setActorUserId(actorUserId)
                .setLandlordUserId(actorUserId)
                .setTenantUserId(request.tenantUserId())
                .setTitle(request.title())
                .setDescription(request.description())
                .setClaimedAmount(money(request.claimedAmount()))
                .setCurrency(request.currency());
        if (request.attachmentIds() != null) {
            grpcRequest.addAllAttachmentIds(request.attachmentIds());
        }
        return GatewayDtoMapper.toHttp(grpcClients.claim().createClaim(grpcRequest.build()));
    }

    @GetMapping("/{id}")
    public ClaimHttpResponse getClaim(@PathVariable Long id) {
        return GatewayDtoMapper.toHttp(grpcClients.claim().getClaim(GetClaimRequest.newBuilder()
                .setClaimId(id)
                .build()));
    }

    @GetMapping("/{id}/timeline")
    public List<ClaimTimelineHttpResponse> getTimeline(@PathVariable Long id) {
        return grpcClients.claim().getTimeline(GetClaimRequest.newBuilder().setClaimId(id).build()).getEntriesList()
                .stream()
                .map(GatewayDtoMapper::toHttp)
                .toList();
    }

    @GetMapping("/{id}/attachments")
    public List<ClaimAttachmentHttpResponse> getClaimAttachments(@PathVariable Long id) {
        return grpcClients.claim().getAttachments(GetClaimRequest.newBuilder().setClaimId(id).build()).getAttachmentsList()
                .stream()
                .map(GatewayDtoMapper::toHttp)
                .toList();
    }

    @GetMapping("/{id}/process-status")
    public ClaimProcessStatusHttpResponse getProcessStatus(@PathVariable Long id) {
        var status = grpcClients.claim().getProcessStatus(GetClaimRequest.newBuilder().setClaimId(id).build());
        return new ClaimProcessStatusHttpResponse(
                status.getClaimId(),
                status.getStatus(),
                status.getCorrelationId(),
                status.getTerminal(),
                status.getAttachmentsList().stream().map(GatewayDtoMapper::toHttp).toList());
    }

    @PostMapping("/{id}/additional-info")
    public ClaimHttpResponse additionalInfo(@PathVariable Long id,
            @Valid @RequestBody AdditionalInfoHttpRequest request,
            HttpServletRequest servletRequest) {
        requireRole(servletRequest, "LANDLORD");
        long actorUserId = actorUserId(servletRequest);
        return GatewayDtoMapper.toHttp(grpcClients.claim().provideAdditionalInfo(AdditionalInfoRequest.newBuilder()
                .setClaimId(id)
                .setActorUserId(actorUserId)
                .setLandlordUserId(actorUserId)
                .setComment(request.comment() == null ? "" : request.comment())
                .build()));
    }

    @PostMapping("/{id}/tenant-response")
    public ClaimHttpResponse tenantResponse(@PathVariable Long id,
            @Valid @RequestBody TenantResponseHttpRequest request,
            HttpServletRequest servletRequest) {
        requireRole(servletRequest, "TENANT");
        long actorUserId = actorUserId(servletRequest);
        return GatewayDtoMapper.toHttp(grpcClients.claim().submitTenantResponse(TenantResponseRequest.newBuilder()
                .setClaimId(id)
                .setActorUserId(actorUserId)
                .setTenantUserId(actorUserId)
                .setAgree(request.agree())
                .setComment(request.comment() == null ? "" : request.comment())
                .build()));
    }

    @PostMapping("/{id}/support-decision")
    public ClaimHttpResponse supportDecision(@PathVariable Long id,
            @Valid @RequestBody SupportDecisionHttpRequest request,
            HttpServletRequest servletRequest) {
        requireRole(servletRequest, "ADMIN");
        long actorUserId = actorUserId(servletRequest);
        return GatewayDtoMapper.toHttp(grpcClients.claim().supportDecision(SupportDecisionRequest.newBuilder()
                .setClaimId(id)
                .setActorUserId(actorUserId)
                .setAdminUserId(actorUserId)
                .setApplyPenalty(request.applyPenalty())
                .setPenaltyAmount(money(request.penaltyAmount()))
                .setPenaltyCurrency(request.penaltyCurrency() == null ? "" : request.penaltyCurrency())
                .setNote(request.note() == null ? "" : request.note())
                .setSimulateFailure(request.simulateFailure())
                .build()));
    }

    @PostMapping("/{id}/repair/reassess")
    public ClaimHttpResponse repairReassess(@PathVariable Long id,
            @Valid @RequestBody(required = false) RepairHttpRequest request,
            HttpServletRequest servletRequest) {
        requireRole(servletRequest, "ADMIN");
        return GatewayDtoMapper.toHttp(grpcClients.claim().repairReassess(repairRequest(id, request, servletRequest)));
    }

    @PostMapping("/{id}/repair/close")
    public ClaimHttpResponse repairClose(@PathVariable Long id,
            @Valid @RequestBody(required = false) RepairHttpRequest request,
            HttpServletRequest servletRequest) {
        requireRole(servletRequest, "ADMIN");
        return GatewayDtoMapper.toHttp(grpcClients.claim().repairClose(repairRequest(id, request, servletRequest)));
    }

    @GetMapping("/my")
    public List<ClaimHttpResponse> listMyClaims(HttpServletRequest servletRequest) {
        return grpcClients.claim().listMyClaims(ListMyClaimsRequest.newBuilder()
                        .setActorUserId(actorUserId(servletRequest))
                        .build())
                .getClaimsList()
                .stream()
                .map(GatewayDtoMapper::toHttp)
                .toList();
    }

    private RepairClaimRequest repairRequest(Long id, RepairHttpRequest request, HttpServletRequest servletRequest) {
        return RepairClaimRequest.newBuilder()
                .setClaimId(id)
                .setActorUserId(actorUserId(servletRequest))
                .setNote(request == null || request.note() == null ? "" : request.note())
                .build();
    }
}
