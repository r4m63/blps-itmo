package blps.itmo.penalty.grpc;

import org.springframework.stereotype.Component;

import blps.itmo.grpc.ListPenaltyOperationsRequest;
import blps.itmo.grpc.ListPenaltyOperationsResponse;
import blps.itmo.grpc.PenaltyOperationDto;
import blps.itmo.grpc.PenaltyRpcServiceGrpc;
import blps.itmo.grpc.RetryPenaltyOperationRequest;
import blps.itmo.penalty.domain.PenaltyOperation;
import blps.itmo.penalty.service.PenaltyWorkflowService;
import blps.itmo.platform.grpc.GrpcErrors;
import blps.itmo.platform.grpc.GrpcMapping;
import io.grpc.stub.StreamObserver;

@Component
public class PenaltyGrpcService extends PenaltyRpcServiceGrpc.PenaltyRpcServiceImplBase {

    private final PenaltyWorkflowService penaltyWorkflowService;

    public PenaltyGrpcService(PenaltyWorkflowService penaltyWorkflowService) {
        this.penaltyWorkflowService = penaltyWorkflowService;
    }

    @Override
    public void listOperationsByClaim(ListPenaltyOperationsRequest request,
            StreamObserver<ListPenaltyOperationsResponse> responseObserver) {
        try {
            ListPenaltyOperationsResponse.Builder response = ListPenaltyOperationsResponse.newBuilder();
            penaltyWorkflowService.getOperationsForClaim(request.getClaimId()).stream()
                    .map(this::toDto)
                    .forEach(response::addOperations);
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    @Override
    public void retryOperation(RetryPenaltyOperationRequest request, StreamObserver<PenaltyOperationDto> responseObserver) {
        try {
            if (!request.getActorRole().isBlank() && !"ADMIN".equals(request.getActorRole())) {
                throw new IllegalArgumentException("ADMIN role is required");
            }
            responseObserver.onNext(toDto(penaltyWorkflowService.retryFailedOperation(request.getOperationId())));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(GrpcErrors.toStatus(e));
        }
    }

    private PenaltyOperationDto toDto(PenaltyOperation operation) {
        return PenaltyOperationDto.newBuilder()
                .setId(operation.getId())
                .setClaimId(operation.getClaimId())
                .setTenantId(operation.getTenantId())
                .setCorrelationId(operation.getCorrelationId())
                .setPenaltyAmount(GrpcMapping.money(operation.getPenaltyAmount()))
                .setPenaltyCurrency(operation.getPenaltyCurrency())
                .setSimulateFailure(operation.isSimulateFailure())
                .setReason(GrpcMapping.text(operation.getReason()))
                .setStatus(operation.getStatus().name())
                .setCreatedAt(GrpcMapping.timestamp(operation.getCreatedAt()))
                .setProcessedAt(GrpcMapping.timestamp(operation.getProcessedAt()))
                .build();
    }
}
