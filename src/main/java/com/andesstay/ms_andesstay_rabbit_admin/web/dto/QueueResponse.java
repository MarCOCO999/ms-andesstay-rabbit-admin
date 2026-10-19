package com.andesstay.ms_andesstay_rabbit_admin.web.dto;

/** Estado de una cola: mensajes listos y consumidores conectados. */
public record QueueResponse(String name, long messageCount, int consumerCount) {
}
