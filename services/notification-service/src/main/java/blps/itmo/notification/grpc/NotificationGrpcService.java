package blps.itmo.notification.grpc;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import blps.itmo.grpc.ListMyNotificationsRequest;
import blps.itmo.grpc.ListMyNotificationsResponse;
import blps.itmo.grpc.NotificationRecordDto;
import blps.itmo.grpc.NotificationRpcServiceGrpc;
import blps.itmo.notification.domain.NotificationLog;
import blps.itmo.notification.repository.NotificationLogRepository;
import blps.itmo.platform.grpc.GrpcErrors;
import blps.itmo.platform.grpc.GrpcMapping;
import io.grpc.stub.StreamObserver;

@Component
public class NotificationGrpcService extends NotificationRpcServiceGrpc.NotificationRpcServiceImplBase {

    private final NotificationLogRepository notificationLogRepository;

    public NotificationGrpcService(NotificationLogRepository notificationLogRepository) {
        this.notificationLogRepository = notificationLogRepository;
    }

    @Override
    public void listMyNotifications(ListMyNotificationsRequest request,
            StreamObserver<ListMyNotificationsResponse> responseObserver) {
        try {
            int limit = request.getLimit() <= 0 ? 20 : Math.min(request.getLimit(), 100);
            ListMyNotificationsResponse.Builder response = ListMyNotificationsResponse.newBuilder();
            notificationLogRepository.findByRecipientUserIdOrderByCreatedAtDesc(
                    request.getUserId(), PageRequest.of(0, limit))
                    .stream()
                    .map(this::toDto)
                    .forEach(response::addNotifications);
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    private NotificationRecordDto toDto(NotificationLog log) {
        NotificationRecordDto.Builder builder = NotificationRecordDto.newBuilder()
                .setId(log.getId())
                .setEventId(log.getEventId())
                .setEventType(log.getEventType())
                .setAggregateId(log.getAggregateId())
                .setChannel(log.getChannel() == null ? "" : log.getChannel())
                .setTemplateKey(log.getTemplateKey() == null ? "" : log.getTemplateKey())
                .setSubject(GrpcMapping.text(log.getSubject()))
                .setMessage(log.getMessage())
                .setCreatedAt(GrpcMapping.timestamp(log.getCreatedAt()));
        if (log.getRecipientUserId() != null) {
            builder.setRecipientUserId(log.getRecipientUserId());
        }
        return builder.build();
    }
}
