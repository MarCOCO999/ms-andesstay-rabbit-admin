package com.andesstay.ms_andesstay_rabbit_admin.service;

/** La cola o el exchange indicado no existe en el broker (HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
