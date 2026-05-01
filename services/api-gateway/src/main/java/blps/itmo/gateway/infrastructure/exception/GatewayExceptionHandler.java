package blps.itmo.gateway.infrastructure.exception;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/**
 * ГЛОБАЛЬНЫЙ ОБРАБОТЧИК ОШИБОК ДЛЯ GATEWAY
 *
 * Транслирует gRPC ошибки в HTTP статусы, понятные REST клиентам.
 *
 * Маппинг gRPC статусов → HTTP статусы:
 * ======================================
 * gRPC статус              | HTTP статус           | Когда возникает
 * -------------------------|-----------------------|-----------------
 * INVALID_ARGUMENT         | 400 Bad Request       | Неверные параметры
 * FAILED_PRECONDITION      | 409 Conflict          | Неправильное состояние (например, нельзя отменить уже завершенную заявку)
 * NOT_FOUND                | 404 Not Found         | Ресурс не найден
 * UNAVAILABLE              | 502 Bad Gateway       | Бекенд-сервис недоступен
 * DEADLINE_EXCEEDED        | 502 Bad Gateway       | Таймаут при вызове
 * INTERNAL                 | 500 Internal Error    | Внутренняя ошибка сервера
 *
 * Важно: клиент (браузер) получает человеко-понятный HTTP статус, а не gRPC специфичный код
 */
@RestControllerAdvice
public class GatewayExceptionHandler {

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<Map<String, String>> handleGrpc(StatusRuntimeException exception) {
        HttpStatus status = switch (exception.getStatus().getCode()) {
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case FAILED_PRECONDITION -> HttpStatus.CONFLICT;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case UNAVAILABLE, DEADLINE_EXCEEDED -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(Map.of(
                "error", exception.getStatus().getDescription() == null
                        ? exception.getStatus().getCode().name()
                        : exception.getStatus().getDescription()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        List<Map<String, String>> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation failed",
                "fields", fields));
    }

    @ExceptionHandler(GatewayForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(GatewayForbiddenException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", exception.getMessage()));
    }
}
