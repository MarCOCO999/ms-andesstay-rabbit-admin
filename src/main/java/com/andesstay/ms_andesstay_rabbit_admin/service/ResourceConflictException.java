package com.andesstay.ms_andesstay_rabbit_admin.service;

/**
 * La operacion choca con el estado del broker (HTTP 409): el recurso ya existe, esta protegido,
 * o RabbitMQ rechazo la operacion (p.ej. borrar con ifEmpty una cola que tiene mensajes).
 */
public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }
}
