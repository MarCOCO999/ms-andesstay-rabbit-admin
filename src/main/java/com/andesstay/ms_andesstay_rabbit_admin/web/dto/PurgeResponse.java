package com.andesstay.ms_andesstay_rabbit_admin.web.dto;

/** Resultado de vaciar una cola. */
public record PurgeResponse(String queue, int purgedMessages) {
}
