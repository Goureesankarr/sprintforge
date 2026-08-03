package dev.sreedaya.sprintforge.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record Problem(Instant timestamp, int status, String error, String message, String path, Map<String,String> fields) {}

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Problem> notFound(NotFoundException ex, HttpServletRequest req) { return problem(HttpStatus.NOT_FOUND, ex.getMessage(), req, null); }
    @ExceptionHandler({ConflictException.class, org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    ResponseEntity<Problem> conflict(Exception ex, HttpServletRequest req) { return problem(HttpStatus.CONFLICT, ex.getMessage(), req, null); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Problem> forbidden(Exception ex, HttpServletRequest req) { return problem(HttpStatus.FORBIDDEN, "You cannot access this resource", req, null); }
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Problem> unauthorized(Exception ex, HttpServletRequest req) { return problem(HttpStatus.UNAUTHORIZED, "Invalid email or password", req, null); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Problem> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        var fields = new LinkedHashMap<String,String>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return problem(HttpStatus.BAD_REQUEST, "Request validation failed", req, fields);
    }
    private ResponseEntity<Problem> problem(HttpStatus status, String message, HttpServletRequest req, Map<String,String> fields) {
        return ResponseEntity.status(status).body(new Problem(Instant.now(), status.value(), status.getReasonPhrase(), message, req.getRequestURI(), fields));
    }

    public static class NotFoundException extends RuntimeException { public NotFoundException(String m) { super(m); } }
    public static class ConflictException extends RuntimeException { public ConflictException(String m) { super(m); } }
}
