package com.andesstay.ms_andesstay_rabbit_admin.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Un binding exchange -> cola. Es el cuerpo de POST /api/rabbit/bindings y, como parametros
 * de query, identifica el binding en DELETE /api/rabbit/bindings. routingKey vacia o ausente
 * es valida (p.ej. para un exchange fanout).
 */
public record BindingRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = ResourceNames.NAME_REGEX, message = ResourceNames.NAME_MESSAGE)
        String exchange,

        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = ResourceNames.NAME_REGEX, message = ResourceNames.NAME_MESSAGE)
        String queue,

        @Pattern(regexp = ResourceNames.ROUTING_KEY_REGEX, message = ResourceNames.ROUTING_KEY_MESSAGE)
        String routingKey
) {

    @JsonIgnore
    public String routingKeyOrEmpty() {
        return routingKey == null ? "" : routingKey;
    }
}
