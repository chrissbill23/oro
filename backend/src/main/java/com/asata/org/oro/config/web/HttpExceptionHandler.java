package com.asata.org.oro.config.web;

import com.asata.org.oro.dto.ErrorDTO;
import com.asata.org.oro.exception.Conflict;
import com.asata.org.oro.exception.PayloadTooLarge;
import com.asata.org.oro.exception.Unprocessable;
import com.asata.org.oro.exception.ServiceUnavailable;
import com.asata.org.oro.exception.TooManyRequests;
import com.asata.org.oro.exception.Forbidden;
import com.asata.org.oro.exception.NotFound;
import com.asata.org.oro.exception.Unauthorized;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The errors as JSON (ErrorDTO). Never a detail of the database nor a stack trace in a response: the logs have them,
 * under the request id.
 */
@Slf4j
@RestControllerAdvice
public class HttpExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<ErrorDTO> badRequest(Exception e, HttpServletRequest request) {
        // a body cut at its limit while it was read (RequestSizeFilter): too large, not malformed
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof PayloadTooLarge tooLarge) {
                return payloadTooLarge(tooLarge, request);
            }
        }
        String message = e instanceof IllegalArgumentException ? e.getMessage() : "The request is malformed";
        return error(request, HttpStatus.BAD_REQUEST, message, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDTO> notValid(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<String> messages = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).sorted().toList();
        return error(request, HttpStatus.BAD_REQUEST, "The request is not valid", messages);
    }

    @ExceptionHandler({Unauthorized.class, AuthenticationException.class})
    public ResponseEntity<ErrorDTO> unauthorized(Exception e, HttpServletRequest request) {
        log.info("unauthorized request on {}", request.getRequestURI());
        // Oro's own refusals say what to do (sign in again, wrong password...); the messages of Spring Security may
        // describe the token: never shown
        String message = e instanceof Unauthorized ? e.getMessage() : "Authentication required";
        return error(request, HttpStatus.UNAUTHORIZED, message, null);
    }

    @ExceptionHandler({Forbidden.class, AccessDeniedException.class})
    public ResponseEntity<ErrorDTO> forbidden(Exception e, HttpServletRequest request) {
        String message = e instanceof Forbidden ? e.getMessage() : "You are not allowed to do this operation";
        return error(request, HttpStatus.FORBIDDEN, message, null);
    }

    @ExceptionHandler(NotFound.class)
    public ResponseEntity<ErrorDTO> notFound(NotFound e, HttpServletRequest request) {
        return error(request, HttpStatus.NOT_FOUND, e.getMessage(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorDTO> noRoute(NoResourceFoundException e, HttpServletRequest request) {
        return error(request, HttpStatus.NOT_FOUND, "No such route", null);
    }

    @ExceptionHandler(TooManyRequests.class)
    public ResponseEntity<ErrorDTO> tooManyRequests(TooManyRequests e, HttpServletRequest request) {
        ResponseEntity<ErrorDTO> error = error(request, HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), null);
        return ResponseEntity.status(error.getStatusCode()).header(HttpHeaders.RETRY_AFTER, Long.toString(e.getRetryAfterSeconds()))
                .body(error.getBody());
    }

    @ExceptionHandler(ServiceUnavailable.class)
    public ResponseEntity<ErrorDTO> serviceUnavailable(ServiceUnavailable e, HttpServletRequest request) {
        ResponseEntity<ErrorDTO> error = error(request, HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), null);
        return ResponseEntity.status(error.getStatusCode()).header(HttpHeaders.RETRY_AFTER, Long.toString(e.getRetryAfterSeconds()))
                .body(error.getBody());
    }

    @ExceptionHandler(Unprocessable.class)
    public ResponseEntity<ErrorDTO> unprocessable(Unprocessable e, HttpServletRequest request) {
        return error(request, HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage(), null);
    }

    @ExceptionHandler(PayloadTooLarge.class)
    public ResponseEntity<ErrorDTO> payloadTooLarge(PayloadTooLarge e, HttpServletRequest request) {
        return error(request, HttpStatus.CONTENT_TOO_LARGE, e.getMessage(), null);
    }

    @ExceptionHandler(Conflict.class)
    public ResponseEntity<ErrorDTO> conflict(Conflict e, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, e.getMessage(), null);
    }

    /**
     * a constraint or a trigger of the database refused the change (through a repository: Spring's exception; through
     * an EntityManager flush: Hibernate's)
     */
    @ExceptionHandler({DataIntegrityViolationException.class, org.hibernate.exception.ConstraintViolationException.class})
    public ResponseEntity<ErrorDTO> dataIntegrity(Exception e, HttpServletRequest request) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        log.warn("refused by the database on {}: {}", request.getRequestURI(), cause.getMessage());
        return error(request, HttpStatus.CONFLICT, "The change breaks a rule of the messaging service", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorDTO> methodNotAllowed(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return error(request, HttpStatus.METHOD_NOT_ALLOWED, e.getMessage(), null);
    }

    /** the client left (a stream closed by the browser): nothing to answer, nothing wrong */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void clientGone(AsyncRequestNotUsableException e, HttpServletRequest request) {
        log.debug("client gone on {}", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDTO> internal(Exception e, HttpServletRequest request) {
        // no connection free within its timeout (Oro saturated): busy, not broken; the client retries
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLTransientConnectionException) {
                log.warn("no database connection free for {}: 503", request.getRequestURI());
                return serviceUnavailable(new ServiceUnavailable("Oro is busy, try again in a few seconds", 2), request);
            }
        }
        log.error("error on {}", request.getRequestURI(), e);
        return error(request, HttpStatus.INTERNAL_SERVER_ERROR, "An internal error occurred, please try again later", null);
    }

    private static ResponseEntity<ErrorDTO> error(HttpServletRequest request, HttpStatus status, String message,
                                                  List<String> messages) {
        Object requestId = request.getAttribute(RequestIdFilter.HEADER);
        return ResponseEntity.status(status).body(new ErrorDTO(OffsetDateTime.now(),
                requestId == null ? "" : requestId.toString(), request.getRequestURI(), status.value(),
                status.getReasonPhrase(), message, messages));
    }
}
