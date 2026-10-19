package com.andesstay.ms_andesstay_rabbit_admin.web.dto;

/** Exchange creado; type en minusculas, como lo nombra RabbitMQ (direct, topic, fanout, headers). */
public record ExchangeResponse(String name, String type, boolean durable, boolean autoDelete) {
}
