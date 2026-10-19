package com.andesstay.ms_andesstay_rabbit_admin.web;

import com.andesstay.ms_andesstay_rabbit_admin.service.RabbitAdminService;
import com.andesstay.ms_andesstay_rabbit_admin.service.ResourceConflictException;
import com.andesstay.ms_andesstay_rabbit_admin.service.ResourceNotFoundException;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.CreateQueueRequest;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.QueueResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Validacion de entrada y codigos HTTP de la API (la seguridad JWT se prueba aparte, en el despliegue). */
class RabbitAdminControllersTest {

    private RabbitAdminService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(RabbitAdminService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders
                .standaloneSetup(new QueueController(service), new ExchangeController(service), new BindingController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void crearColaValidaResponde201ConLocation() throws Exception {
        when(service.createQueue(any(CreateQueueRequest.class))).thenReturn(new QueueResponse("q.pruebas", 0, 0));

        mvc.perform(post("/api/rabbit/queues").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"q.pruebas\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/rabbit/queues/q.pruebas"))
                .andExpect(jsonPath("$.name").value("q.pruebas"));
    }

    @Test
    void colaConNombreVacioEsRechazada() throws Exception {
        mvc.perform(post("/api/rabbit/queues").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        verify(service, never()).createQueue(any());
    }

    @Test
    void colaConPrefijoReservadoAmqEsRechazada() throws Exception {
        mvc.perform(post("/api/rabbit/queues").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"amq.mia\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void ttlNegativoYRoutingKeyDeDeadLetterSinExchangeSonRechazados() throws Exception {
        mvc.perform(post("/api/rabbit/queues").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"q.pruebas\",\"messageTtlMs\":-5,\"deadLetterRoutingKey\":\"x.dead\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.messageTtlMs").exists())
                .andExpect(jsonPath("$.errors.deadLetterRoutingKey").exists());
    }

    @Test
    void exchangeConTipoDesconocidoEsRechazado() throws Exception {
        mvc.perform(post("/api/rabbit/exchanges").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ex.pruebas\",\"type\":\"circular\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("direct, topic, fanout, headers")));
    }

    @Test
    void exchangeSinTipoEsRechazado() throws Exception {
        mvc.perform(post("/api/rabbit/exchanges").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ex.pruebas\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.type").exists());
    }

    @Test
    void nombreDeColaInvalidoEnLaRutaEsRechazado() throws Exception {
        mvc.perform(get("/api/rabbit/queues/{name}", "nombre con espacios"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eliminarBindingSinExchangeEsRechazado() throws Exception {
        mvc.perform(delete("/api/rabbit/bindings").param("queue", "q.pruebas"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.exchange").exists());
    }

    @Test
    void colaInexistenteResponde404() throws Exception {
        when(service.getQueue("q.no.existe")).thenThrow(new ResourceNotFoundException("La cola 'q.no.existe' no existe"));

        mvc.perform(get("/api/rabbit/queues/q.no.existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("La cola 'q.no.existe' no existe"));
    }

    @Test
    void eliminarColaProtegidaResponde409() throws Exception {
        doThrow(new ResourceConflictException("protegida")).when(service).deleteQueue("q.cmd.email", false, false);

        mvc.perform(delete("/api/rabbit/queues/q.cmd.email"))
                .andExpect(status().isConflict());
    }

    @Test
    void eliminarColaResponde204() throws Exception {
        mvc.perform(delete("/api/rabbit/queues/q.pruebas").param("ifEmpty", "true"))
                .andExpect(status().isNoContent());
        verify(service).deleteQueue("q.pruebas", false, true);
    }
}
