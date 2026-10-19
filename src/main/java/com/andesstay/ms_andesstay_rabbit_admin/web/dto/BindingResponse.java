package com.andesstay.ms_andesstay_rabbit_admin.web.dto;

/** Binding exchange -> cola creado. */
public record BindingResponse(String exchange, String queue, String routingKey) {
}
