package com.andesstay.ms_andesstay_rabbit_admin.web;

import com.andesstay.ms_andesstay_rabbit_admin.service.BrokerUnavailableException;
import com.andesstay.ms_andesstay_rabbit_admin.service.ResourceConflictException;
import com.andesstay.ms_andesstay_rabbit_admin.service.ResourceNotFoundException;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Traduce los errores a un JSON uniforme ({@link ErrorResponse}) con el codigo HTTP que corresponde. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID = "La solicitud tiene parametros invalidos";

    /** @Valid sobre un @RequestBody o @ModelAttribute (MethodArgumentNotValidException es una BindException). */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(BindException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldName(error.getField()), error.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors().forEach(error -> errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        return badRequest(errors);
    }

    /** Restricciones sobre @PathVariable / @RequestParam. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParameterValidation(HandlerMethodValidationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String parameter = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> errors.putIfAbsent(String.valueOf(parameter), error.getDefaultMessage()));
        });
        return badRequest(errors);
    }

    /** JSON mal formado o valor que no se puede convertir (p.ej. un type de exchange desconocido). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        String detail = rootMessage(ex);
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Cuerpo de la solicitud invalido: " + detail));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(404, ex.getMessage()));
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ResourceConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, ex.getMessage()));
    }

    @ExceptionHandler(BrokerUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleBrokerDown(BrokerUnavailableException ex) {
        log.error("RabbitMQ no disponible", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.of(503, ex.getMessage()));
    }

    private static ResponseEntity<ErrorResponse> badRequest(Map<String, String> errors) {
        return ResponseEntity.badRequest().body(new ErrorResponse(Instant.now(), 400, INVALID, errors));
    }

    /** "deadLetterConfigValid" es la validacion cruzada de CreateQueueRequest; se reporta con un nombre legible. */
    private static String fieldName(String field) {
        return "deadLetterConfigValid".equals(field) ? "deadLetterRoutingKey" : field;
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
