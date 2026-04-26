package blps.itmo.audit.grpc;

import org.springframework.stereotype.Component;

import blps.itmo.audit.domain.AuditRecord;
import blps.itmo.audit.repository.AuditRecordRepository;
import blps.itmo.grpc.AuditRecordDto;
import blps.itmo.grpc.AuditRpcServiceGrpc;
import blps.itmo.grpc.GetClaimEventsRequest;
import blps.itmo.grpc.GetClaimEventsResponse;
import blps.itmo.platform.grpc.GrpcErrors;
import blps.itmo.platform.grpc.GrpcMapping;
import io.grpc.stub.StreamObserver;

@Component
public class AuditGrpcService extends AuditRpcServiceGrpc.AuditRpcServiceImplBase {

    private final AuditRecordRepository auditRecordRepository;

    public AuditGrpcService(AuditRecordRepository auditRecordRepository) {
        this.auditRecordRepository = auditRecordRepository;
    }

    @Override
    public void getClaimEvents(GetClaimEventsRequest request, StreamObserver<GetClaimEventsResponse> responseObserver) {
        try {
            GetClaimEventsResponse.Builder response = GetClaimEventsResponse.newBuilder();
            auditRecordRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                            "CLAIM", String.valueOf(request.getClaimId()))
                    .stream()
                    .map(this::toDto)
                    .forEach(response::addRecords);
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    private AuditRecordDto toDto(AuditRecord record) {
        return AuditRecordDto.newBuilder()
                .setId(record.getId())
                .setEventId(record.getEventId())
                .setEventType(record.getEventType())
                .setAggregateType(record.getAggregateType())
                .setAggregateId(record.getAggregateId())
                .setCorrelationId(record.getCorrelationId())
                .setSagaId(record.getSagaId())
                .setPayloadJson(record.getPayloadJson())
                .setCreatedAt(GrpcMapping.timestamp(record.getCreatedAt()))
                .build();
    }
}
