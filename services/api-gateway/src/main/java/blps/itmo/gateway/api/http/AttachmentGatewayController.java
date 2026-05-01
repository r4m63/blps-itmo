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
import blps.itmo.gateway.dto.GatewayDtos.AttachmentHttpResponse;
import blps.itmo.gateway.dto.GatewayDtos.ConfirmAttachmentHttpRequest;
import blps.itmo.gateway.dto.GatewayDtos.InitAttachmentHttpRequest;
import blps.itmo.grpc.ConfirmAttachmentRequest;
import blps.itmo.grpc.GetAttachmentRequest;
import blps.itmo.grpc.InitAttachmentRequest;
import blps.itmo.grpc.ListAttachmentsRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentGatewayController extends GatewayControllerSupport {

    public AttachmentGatewayController(GatewayGrpcClients grpcClients) {
        super(grpcClients);
    }

    @PostMapping("/init")
    public AttachmentHttpResponse initAttachment(@Valid @RequestBody InitAttachmentHttpRequest request,
            HttpServletRequest servletRequest) {
        long actorUserId = actorUserId(servletRequest);
        return GatewayDtoMapper.toHttp(grpcClients.storage().initAttachment(InitAttachmentRequest.newBuilder()
                .setActorUserId(actorUserId)
                .setOwnerUserId(actorUserId)
                .setOriginalFilename(request.originalFilename())
                .setContentType(request.contentType() == null ? "" : request.contentType())
                .build()));
    }

    @PostMapping("/{id}/confirm")
    public AttachmentHttpResponse confirmAttachment(@PathVariable Long id,
            @Valid @RequestBody(required = false) ConfirmAttachmentHttpRequest request,
            HttpServletRequest servletRequest) {
        return GatewayDtoMapper.toHttp(grpcClients.storage().confirmAttachment(ConfirmAttachmentRequest.newBuilder()
                .setAttachmentId(id)
                .setActorUserId(actorUserId(servletRequest))
                .setObjectKey(request == null || request.objectKey() == null ? "" : request.objectKey())
                .build()));
    }

    @GetMapping("/{id}")
    public AttachmentHttpResponse getAttachment(@PathVariable Long id) {
        return GatewayDtoMapper.toHttp(grpcClients.storage().getAttachment(GetAttachmentRequest.newBuilder()
                .setAttachmentId(id)
                .build()));
    }

    @GetMapping
    public List<AttachmentHttpResponse> listAttachments(HttpServletRequest servletRequest) {
        return grpcClients.storage().listAttachments(ListAttachmentsRequest.newBuilder()
                        .setOwnerUserId(actorUserId(servletRequest))
                        .build())
                .getAttachmentsList()
                .stream()
                .map(GatewayDtoMapper::toHttp)
                .toList();
    }
}
