package com.andesstay.ms_andesstay_rabbit_admin.service;

import com.andesstay.ms_andesstay_rabbit_admin.config.RabbitAdminProperties;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.BindingRequest;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.CreateExchangeRequest;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.CreateQueueRequest;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.ExchangeType;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.QueueResponse;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.AMQImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitAdminServiceTest {

    private AmqpAdmin amqpAdmin;
    private RabbitTemplate rabbitTemplate;
    private RabbitAdminService service;

    @BeforeEach
    void setUp() {
        amqpAdmin = mock(AmqpAdmin.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        RabbitAdminProperties properties = new RabbitAdminProperties(List.of("q.cmd.email"), List.of("cmd.direct"));
        service = new RabbitAdminService(amqpAdmin, rabbitTemplate, properties);
    }

    @Test
    void creaLaColaConSusArgumentosDeDeadLetterYTtl() {
        when(amqpAdmin.getQueueInfo("q.pruebas")).thenReturn(null, new QueueInformation("q.pruebas", 0, 0));

        QueueResponse created = service.createQueue(new CreateQueueRequest(
                "q.pruebas", null, null, "cmd.dead.dlx", "pruebas.dead", 10_000, null));

        ArgumentCaptor<Queue> declared = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(declared.capture());
        assertThat(declared.getValue().isDurable()).isTrue();
        assertThat(declared.getValue().getArguments())
                .containsEntry("x-dead-letter-exchange", "cmd.dead.dlx")
                .containsEntry("x-dead-letter-routing-key", "pruebas.dead")
                .containsEntry("x-message-ttl", 10_000);
        assertThat(created.name()).isEqualTo("q.pruebas");
    }

    @Test
    void noCreaUnaColaQueYaExiste() {
        when(amqpAdmin.getQueueInfo("q.pruebas")).thenReturn(new QueueInformation("q.pruebas", 0, 0));

        assertThatThrownBy(() -> service.createQueue(new CreateQueueRequest("q.pruebas", null, null, null, null, null, null)))
                .isInstanceOf(ResourceConflictException.class);
        verify(amqpAdmin, never()).declareQueue(any(Queue.class));
    }

    @Test
    void noCreaUnaColaSiElExchangeDeDeadLetterNoExiste() {
        exchangeIsMissing();

        assertThatThrownBy(() -> service.createQueue(new CreateQueueRequest("q.pruebas", null, null, "no.existe", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no.existe");
    }

    @Test
    void noEliminaUnaColaProtegidaDelSistema() {
        assertThatThrownBy(() -> service.deleteQueue("q.cmd.email", false, false))
                .isInstanceOf(ResourceConflictException.class);
        verify(amqpAdmin, never()).deleteQueue(anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    void eliminarUnaColaInexistenteEsNotFound() {
        assertThatThrownBy(() -> service.deleteQueue("q.no.existe", false, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void ifEmptyImpideEliminarUnaColaConMensajes() {
        when(amqpAdmin.getQueueInfo("q.pruebas")).thenReturn(new QueueInformation("q.pruebas", 5, 0));

        assertThatThrownBy(() -> service.deleteQueue("q.pruebas", false, true))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("5 mensaje");
    }

    @Test
    void creaElExchangeDelTipoPedido() {
        exchangeIsMissing();

        service.createExchange(new CreateExchangeRequest("ex.pruebas", ExchangeType.TOPIC, null, null));

        ArgumentCaptor<Exchange> declared = ArgumentCaptor.forClass(Exchange.class);
        verify(amqpAdmin).declareExchange(declared.capture());
        assertThat(declared.getValue().getType()).isEqualTo("topic");
        assertThat(declared.getValue().isDurable()).isTrue();
    }

    @Test
    void noEliminaUnExchangeProtegidoDelSistema() {
        assertThatThrownBy(() -> service.deleteExchange("cmd.direct"))
                .isInstanceOf(ResourceConflictException.class);
        verify(amqpAdmin, never()).deleteExchange(anyString());
    }

    @Test
    void unBindingHaciaUnExchangeInexistenteEsNotFound() {
        exchangeIsMissing();

        assertThatThrownBy(() -> service.createBinding(new BindingRequest("no.existe", "q.pruebas", "x")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void sinConexionAlBrokerSeInformaComoNoDisponible() {
        when(amqpAdmin.getQueueInfo("q.pruebas")).thenThrow(new AmqpConnectException(new IOException("Connection refused")));

        assertThatThrownBy(() -> service.getQueue("q.pruebas"))
                .isInstanceOf(BrokerUnavailableException.class);
    }

    /** RabbitMQ responde a un declare pasivo de un exchange inexistente cerrando el canal con 404. */
    @SuppressWarnings("unchecked")
    private void exchangeIsMissing() {
        ShutdownSignalException notFound = new ShutdownSignalException(false, false,
                new AMQImpl.Channel.Close(404, "NOT_FOUND - no exchange", 40, 10), null);
        when(rabbitTemplate.execute(any(ChannelCallback.class)))
                .thenThrow(new AmqpIOException(new IOException(notFound)));
    }
}
