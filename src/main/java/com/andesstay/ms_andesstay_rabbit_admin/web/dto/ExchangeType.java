package com.andesstay.ms_andesstay_rabbit_admin.web.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;

/** Tipos de exchange de RabbitMQ. Se aceptan en mayusculas o minusculas ("topic", "TOPIC"). */
public enum ExchangeType {
    DIRECT, TOPIC, FANOUT, HEADERS;

    @JsonCreator
    public static ExchangeType from(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "type invalido: '" + value + "'. Valores permitidos: direct, topic, fanout, headers"));
    }
}
