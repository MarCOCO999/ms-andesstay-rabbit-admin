package com.andesstay.ms_andesstay_rabbit_admin.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Cuerpo de POST /api/rabbit/queues. Solo name es obligatorio; durable es true y autoDelete
 * false si no se envian.
 */
public record CreateQueueRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = ResourceNames.NAME_REGEX, message = ResourceNames.NAME_MESSAGE)
        String name,

        Boolean durable,

        Boolean autoDelete,

        @Pattern(regexp = ResourceNames.NAME_REGEX, message = ResourceNames.NAME_MESSAGE)
        String deadLetterExchange,

        @Pattern(regexp = ResourceNames.ROUTING_KEY_REGEX, message = ResourceNames.ROUTING_KEY_MESSAGE)
        String deadLetterRoutingKey,

        @Positive(message = "debe ser mayor que 0")
        @Max(value = 604_800_000L, message = "no puede superar 7 dias (604800000 ms)")
        Integer messageTtlMs,

        @Positive(message = "debe ser mayor que 0")
        Integer maxLength
) {

    @JsonIgnore
    public boolean isDurable() {
        return durable == null || durable;
    }

    @JsonIgnore
    public boolean isAutoDelete() {
        return autoDelete != null && autoDelete;
    }

    /** Una routing key de dead-letter sin exchange de dead-letter no tendria efecto. */
    @JsonIgnore
    @AssertTrue(message = "deadLetterRoutingKey requiere indicar deadLetterExchange")
    public boolean isDeadLetterConfigValid() {
        return deadLetterRoutingKey == null || deadLetterExchange != null;
    }
}
