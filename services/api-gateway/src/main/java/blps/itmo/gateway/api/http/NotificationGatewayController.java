package blps.itmo.gateway.api.http;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import blps.itmo.gateway.grpc.GatewayGrpcClients;
import blps.itmo.gateway.dto.GatewayDtoMapper;
import blps.itmo.gateway.dto.GatewayDtos.NotificationHttpResponse;
import blps.itmo.grpc.ListMyNotificationsRequest;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/notifications")
public class NotificationGatewayController extends GatewayControllerSupport {

    public NotificationGatewayController(GatewayGrpcClients grpcClients) {
        super(grpcClients);
    }

    @GetMapping("/my")
    public List<NotificationHttpResponse> listMyNotifications(HttpServletRequest servletRequest) {
        return grpcClients.notification().listMyNotifications(ListMyNotificationsRequest.newBuilder()
                        .setUserId(actorUserId(servletRequest))
                        .setLimit(20)
                        .build())
                .getNotificationsList()
                .stream()
                .map(GatewayDtoMapper::toHttp)
                .toList();
    }
}
