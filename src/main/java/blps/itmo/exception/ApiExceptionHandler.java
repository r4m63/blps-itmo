package blps.itmo.exception;

import blps.itmo.exception.DomainException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<?> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("not_found", ex.getMessage()));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<?> handleDomain(DomainException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("invalid_state", ex.getMessage()));
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of(
                "code", code,
                "message", message,
                "timestamp", OffsetDateTime.now()
        );
    }
}
