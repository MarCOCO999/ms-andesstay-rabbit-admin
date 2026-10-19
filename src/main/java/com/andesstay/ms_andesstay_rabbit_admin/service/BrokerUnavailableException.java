package com.andesstay.ms_andesstay_rabbit_admin.service;

/** No hay conexion con RabbitMQ (HTTP 503). */
public class BrokerUnavailableException extends RuntimeException {

    public BrokerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
