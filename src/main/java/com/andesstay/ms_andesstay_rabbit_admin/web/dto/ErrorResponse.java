package com.andesstay.ms_andesstay_rabbit_admin.web.dto;

import java.time.Instant;
import java.util.Map;

/** Formato unico de error de la API; errors detalla campo -> problema en los 400 de validacion. */
public record ErrorResponse(Instant timestamp, int status, String message, Map<String, String> errors) {

    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(Instant.now(), status, message, Map.of());
    }
}
