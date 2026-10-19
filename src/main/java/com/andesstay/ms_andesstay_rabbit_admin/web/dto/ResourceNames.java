package com.andesstay.ms_andesstay_rabbit_admin.web.dto;

/** Reglas de nombres compartidas por los DTOs y los parametros de ruta. */
public final class ResourceNames {

    /**
     * Letras, digitos, '.', '_' o '-', de 1 a 200 caracteres. El prefijo "amq." lo reserva
     * RabbitMQ para sus recursos internos y el broker rechaza declararlo.
     */
    public static final String NAME_REGEX = "^(?!amq\\.)[A-Za-z0-9._-]{1,200}$";
    public static final String NAME_MESSAGE =
            "solo puede tener letras, numeros, '.', '_' o '-' (1 a 200 caracteres) y no puede empezar con 'amq.'";

    /** Routing key o patron de topic ('*' una palabra, '#' cero o mas). Vacia vale para fanout. */
    public static final String ROUTING_KEY_REGEX = "^[A-Za-z0-9._*#-]{0,255}$";
    public static final String ROUTING_KEY_MESSAGE =
            "solo puede tener letras, numeros, '.', '_', '-', '*' o '#' (maximo 255 caracteres)";

    private ResourceNames() {
    }
}
