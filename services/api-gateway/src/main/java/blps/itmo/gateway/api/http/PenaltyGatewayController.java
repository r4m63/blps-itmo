package blps.itmo.gateway.api.http;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import blps.itmo.gateway.grpc.GatewayGrpcClients;
import blps.itmo.gateway.dto.GatewayDtoMapper;
import blps.itmo.gateway.dto.GatewayDtos.PenaltyOperationHttpResponse;
import blps.itmo.grpc.ListPenaltyOperationsRequest;
import blps.itmo.grpc.RetryPenaltyOperationRequest;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/penalties")
public class PenaltyGatewayController extends GatewayControllerSupport {

    public PenaltyGatewayController(GatewayGrpcClients grpcClients) {
        super(grpcClients);
    }

    @GetMapping("/claims/{claimId}")
    public List<PenaltyOperationHttpResponse> getPenaltyOperations(@PathVariable Long claimId,
            HttpServletRequest servletRequest) {
        requireRole(servletRequest, "ADMIN");
        return grpcClients.penalty().listOperationsByClaim(ListPenaltyOperationsRequest.newBuilder()
                        .setClaimId(claimId)
                        .build())
                .getOperationsList()
                .stream()
                .map(GatewayDtoMapper::toHttp)
                .toList();
    }

    @PostMapping("/operations/{operationId}/retry")
    public PenaltyOperationHttpResponse retryPenalty(@PathVariable Long operationId, HttpServletRequest servletRequest) {
        requireRole(servletRequest, "ADMIN");
        return GatewayDtoMapper.toHttp(grpcClients.penalty().retryOperation(RetryPenaltyOperationRequest.newBuilder()
                .setOperationId(operationId)
                .setActorRole(actorRole(servletRequest))
                .build()));
    }
}
