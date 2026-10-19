package com.andesstay.ms_andesstay_rabbit_admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Colas y exchanges que la API no permite eliminar (andesstay.rabbit-admin en application.yml). */
@ConfigurationProperties(prefix = "andesstay.rabbit-admin")
public record RabbitAdminProperties(List<String> protectedQueues, List<String> protectedExchanges) {

    public RabbitAdminProperties {
        protectedQueues = protectedQueues == null ? List.of() : List.copyOf(protectedQueues);
        protectedExchanges = protectedExchanges == null ? List.of() : List.copyOf(protectedExchanges);
    }

    public boolean isProtectedQueue(String name) {
        return protectedQueues.contains(name);
    }

    public boolean isProtectedExchange(String name) {
        return protectedExchanges.contains(name);
    }
}
