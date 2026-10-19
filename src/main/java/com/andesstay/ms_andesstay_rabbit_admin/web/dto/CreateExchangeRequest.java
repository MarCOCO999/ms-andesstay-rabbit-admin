package com.andesstay.ms_andesstay_rabbit_admin.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Cuerpo de POST /api/rabbit/exchanges. durable es true y autoDelete false si no se envian. */
public record CreateExchangeRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = ResourceNames.NAME_REGEX, message = ResourceNames.NAME_MESSAGE)
        String name,

        @NotNull(message = "es obligatorio (direct, topic, fanout o headers)")
        ExchangeType type,

        Boolean durable,

        Boolean autoDelete
) {

    @JsonIgnore
    public boolean isDurable() {
        return durable == null || durable;
    }

    @JsonIgnore
    public boolean isAutoDelete() {
        return autoDelete != null && autoDelete;
    }
}
