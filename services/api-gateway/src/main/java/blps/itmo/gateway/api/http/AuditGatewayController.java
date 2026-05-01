package blps.itmo.gateway.api.http;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import blps.itmo.gateway.grpc.GatewayGrpcClients;
import blps.itmo.gateway.dto.GatewayDtoMapper;
import blps.itmo.gateway.dto.GatewayDtos.AuditRecordHttpResponse;
import blps.itmo.grpc.GetClaimEventsRequest;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/audit")
public class AuditGatewayController extends GatewayControllerSupport {

    public AuditGatewayController(GatewayGrpcClients grpcClients) {
        super(grpcClients);
    }

    @GetMapping("/claims/{claimId}/events")
    public List<AuditRecordHttpResponse> getClaimEvents(@PathVariable Long claimId,
            HttpServletRequest servletRequest) {
        requireRole(servletRequest, "ADMIN");
        return grpcClients.audit().getClaimEvents(GetClaimEventsRequest.newBuilder().setClaimId(claimId).build())
                .getRecordsList()
                .stream()
                .map(GatewayDtoMapper::toHttp)
                .toList();
    }
}
