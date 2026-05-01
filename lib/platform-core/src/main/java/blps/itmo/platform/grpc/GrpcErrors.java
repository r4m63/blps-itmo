package blps.itmo.platform.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/**
 * МАППЕР ОШИБОК ДЛЯ gRPC.
 *
 * Проблема: в gRPC ошибки имеют стандартные коды (INVALID_ARGUMENT, NOT_FOUND и т.д.).
 * В вашем бизнес-коде могут быть обычные Java исключения (IllegalArgumentException,
 * IllegalStateException). Нужно конвертировать их в gRPC StatusRuntimeException,
 * чтобы клиент получил понятный gRPC-статус.
 *
 * Пример использования в gRPC сервере:
 *
 *   @GrpcService
 *   public class ClaimGrpcService extends ClaimServiceGrpc.ClaimServiceImplBase {
 *       @Override
 *       public void getClaim(GetClaimRequest request, StreamObserver<GetClaimResponse> responseObserver) {
 *           try {
 *               Claim claim = claimService.findById(request.getId());
 *               responseObserver.onNext(toProto(claim));
 *               responseObserver.onCompleted();
 *           } catch (Exception e) {
 *               // Конвертируем исключение в gRPC ошибку
 *               responseObserver.onError(GrpcErrors.toStatus(e));
 *           }
 *       }
 *   }
 */
public final class GrpcErrors {

    private GrpcErrors() {
        // Утилитный класс
    }

    /**
     * Конвертирует любое Java исключение в gRPC StatusRuntimeException.
     *
     * Маппинг правил:
     * - StatusRuntimeException → оставляем как есть (уже gRPC ошибка)
     * - IllegalArgumentException → Status.INVALID_ARGUMENT (неверный параметр)
     * - IllegalStateException → Status.FAILED_PRECONDITION (неправильное состояние)
     * - всё остальное → Status.INTERNAL (внутренняя ошибка сервера)
     *
     * Почему это важно?
     * - Клиент (обычно API Gateway) получает понятный gRPC статус
     * - gateway может транслировать его в HTTP статус (400, 500, 404)
     * - В логи попадает правильный код ошибки
     */
    public static StatusRuntimeException toStatus(Throwable throwable) {
        // Уже gRPC ошибка — просто пробрасываем
        if (throwable instanceof StatusRuntimeException statusRuntimeException) {
            return statusRuntimeException;
        }

        // Неверный аргумент (например, отрицательный ID)
        if (throwable instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT
                    .withDescription(throwable.getMessage())
                    .withCause(throwable)
                    .asRuntimeException();
        }

        // Неправильное состояние (например, попытка опубликовать уже опубликованное)
        if (throwable instanceof IllegalStateException) {
            return Status.FAILED_PRECONDITION
                    .withDescription(throwable.getMessage())
                    .withCause(throwable)
                    .asRuntimeException();
        }

        // Все остальные ошибки (NPE, SQLException, и т.д.)
        return Status.INTERNAL
                .withDescription(throwable.getMessage())
                .withCause(throwable)
                .asRuntimeException();
    }
}