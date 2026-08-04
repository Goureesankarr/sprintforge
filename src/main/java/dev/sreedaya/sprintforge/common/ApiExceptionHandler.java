package dev.sreedaya.sprintforge.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record Problem(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path,
            Map<String, String> fields) {
        public Problem {
            fields = fields == null ? null : Map.copyOf(fields);
        }

        @Override
        public Map<String, String> fields() {
            return fields == null ? null : Map.copyOf(fields);
        }
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Problem> notFound(
            NotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage(), request, null);
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<Problem> badRequest(
            BadRequestException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage(), request, null);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    ResponseEntity<Problem> serviceUnavailable(
            ServiceUnavailableException exception, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request, null);
    }

    @ExceptionHandler({
        ConflictException.class,
        org.springframework.orm.ObjectOptimisticLockingFailureException.class
    })
    ResponseEntity<Problem> conflict(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Problem> forbidden(Exception exception, HttpServletRequest request) {
        return problem(
                HttpStatus.FORBIDDEN,
                "You cannot access this resource",
                request,
                null);
    }

    @ExceptionHandler({BadCredentialsException.class, UnauthorizedException.class})
    ResponseEntity<Problem> unauthorized(Exception exception, HttpServletRequest request) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password",
                request,
                null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Problem> validation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return problem(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request,
                fields);
    }

    private ResponseEntity<Problem> problem(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> fields) {
        Problem body = new Problem(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                fields);
        return ResponseEntity.status(status).body(body);
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }

    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }
    }
}
