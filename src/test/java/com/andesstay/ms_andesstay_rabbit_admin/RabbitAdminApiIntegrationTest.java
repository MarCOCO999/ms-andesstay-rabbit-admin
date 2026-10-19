package com.andesstay.ms_andesstay_rabbit_admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de punta a punta contra un RabbitMQ real (infra/mq/compose.yml en localhost:5672):
 * seguridad por rol, creacion y eliminacion de exchange, cola y binding, y proteccion de la
 * topologia del sistema. Se omite si no hay broker disponible. El JWT se simula con
 * spring-security-test, sin llamar a Azure AD.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RabbitAdminApiIntegrationTest {

    private static final String EXCHANGE = "it.rabbit-admin.ex";
    private static final String QUEUE = "it.rabbit-admin.q";

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    static void requireBroker() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 5672), 1000);
        } catch (IOException ex) {
            assumeTrue(false, "RabbitMQ no esta disponible en localhost:5672");
        }
    }

    @AfterEach
    void cleanUp() throws Exception {
        mvc.perform(delete("/api/rabbit/queues/" + QUEUE).with(admin()));
        mvc.perform(delete("/api/rabbit/exchanges/" + EXCHANGE).with(admin()));
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/rabbit/queues/" + QUEUE)).andExpect(status().isUnauthorized());
    }

    @Test
    void unRolDistintoDeAdminResponde403() throws Exception {
        mvc.perform(post("/api/rabbit/queues").with(role("Operador"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + QUEUE + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cicloCompletoDeExchangeColaYBinding() throws Exception {
        mvc.perform(post("/api/rabbit/exchanges").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + EXCHANGE + "\",\"type\":\"topic\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("topic"));

        mvc.perform(post("/api/rabbit/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + QUEUE + "\",\"messageTtlMs\":60000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageCount").value(0));

        mvc.perform(post("/api/rabbit/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + QUEUE + "\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/rabbit/bindings").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchange\":\"" + EXCHANGE + "\",\"queue\":\"" + QUEUE + "\",\"routingKey\":\"pruebas.#\"}"))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/rabbit/bindings").with(admin())
                        .param("exchange", EXCHANGE).param("queue", QUEUE).param("routingKey", "pruebas.#"))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/rabbit/queues/" + QUEUE).with(admin())).andExpect(status().isNoContent());
        mvc.perform(get("/api/rabbit/queues/" + QUEUE).with(admin())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/rabbit/exchanges/" + EXCHANGE).with(admin())).andExpect(status().isNoContent());
    }

    @Test
    void bindingHaciaUnaColaInexistenteResponde404() throws Exception {
        mvc.perform(post("/api/rabbit/exchanges").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + EXCHANGE + "\",\"type\":\"direct\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/rabbit/bindings").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchange\":\"" + EXCHANGE + "\",\"queue\":\"it.no.existe\",\"routingKey\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void laTopologiaDelSistemaNoSePuedeEliminar() throws Exception {
        mvc.perform(delete("/api/rabbit/queues/q.cmd.email").with(admin())).andExpect(status().isConflict());
        mvc.perform(delete("/api/rabbit/exchanges/cmd.direct").with(admin())).andExpect(status().isConflict());
    }

    private static RequestPostProcessor admin() {
        return role("Admin");
    }

    private static RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
