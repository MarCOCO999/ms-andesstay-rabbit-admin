package com.andesstay.ms_andesstay_rabbit_admin.service;

import com.andesstay.ms_andesstay_rabbit_admin.config.RabbitAdminProperties;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.BindingRequest;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.BindingResponse;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.CreateExchangeRequest;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.CreateQueueRequest;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.ExchangeResponse;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.PurgeResponse;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.QueueResponse;
import com.rabbitmq.client.ShutdownSignalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Toda la administracion de RabbitMQ pasa por aca: los controladores llaman a estos metodos de
 * alto nivel y reciben DTOs o excepciones del dominio ({@link ResourceNotFoundException},
 * {@link ResourceConflictException}, {@link BrokerUnavailableException}), sin conocer AmqpAdmin,
 * canales ni codigos de error del protocolo AMQP.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdminProperties properties;

    public RabbitAdminService(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate, RabbitAdminProperties properties) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    // ---- Colas ----

    public QueueResponse createQueue(CreateQueueRequest request) {
        if (queueInfo(request.name()) != null) {
            throw new ResourceConflictException("La cola '" + request.name() + "' ya existe");
        }
        if (request.deadLetterExchange() != null && !exchangeExists(request.deadLetterExchange())) {
            throw new ResourceNotFoundException("El exchange de dead-letter '" + request.deadLetterExchange() + "' no existe");
        }

        QueueBuilder builder = request.isDurable() ? QueueBuilder.durable(request.name()) : QueueBuilder.nonDurable(request.name());
        if (request.isAutoDelete()) {
            builder.autoDelete();
        }
        if (request.deadLetterExchange() != null) {
            builder.deadLetterExchange(request.deadLetterExchange());
        }
        if (request.deadLetterRoutingKey() != null) {
            builder.deadLetterRoutingKey(request.deadLetterRoutingKey());
        }
        if (request.messageTtlMs() != null) {
            builder.ttl(request.messageTtlMs());
        }
        if (request.maxLength() != null) {
            builder.maxLength(request.maxLength());
        }
        Queue queue = builder.build();

        call(() -> amqpAdmin.declareQueue(queue));
        log.info("Cola creada: {} (durable={}, autoDelete={}, args={})",
                queue.getName(), queue.isDurable(), queue.isAutoDelete(), queue.getArguments());
        return getQueue(queue.getName());
    }

    public QueueResponse getQueue(String name) {
        QueueInformation info = queueInfo(name);
        if (info == null) {
            throw new ResourceNotFoundException("La cola '" + name + "' no existe");
        }
        return new QueueResponse(info.getName(), info.getMessageCount(), info.getConsumerCount());
    }

    /**
     * @param ifUnused solo la elimina si no tiene consumidores
     * @param ifEmpty  solo la elimina si no tiene mensajes
     */
    public void deleteQueue(String name, boolean ifUnused, boolean ifEmpty) {
        if (properties.isProtectedQueue(name)) {
            throw new ResourceConflictException("La cola '" + name + "' es parte de la topologia del sistema y no se puede eliminar");
        }
        QueueResponse queue = getQueue(name);
        if (ifUnused && queue.consumerCount() > 0) {
            throw new ResourceConflictException("La cola '" + name + "' tiene " + queue.consumerCount() + " consumidor(es)");
        }
        if (ifEmpty && queue.messageCount() > 0) {
            throw new ResourceConflictException("La cola '" + name + "' tiene " + queue.messageCount() + " mensaje(s)");
        }
        call(() -> {
            amqpAdmin.deleteQueue(name, ifUnused, ifEmpty);
            return null;
        });
        log.info("Cola eliminada: {}", name);
    }

    public PurgeResponse purgeQueue(String name) {
        getQueue(name);
        int purged = call(() -> amqpAdmin.purgeQueue(name));
        log.info("Cola purgada: {} ({} mensajes)", name, purged);
        return new PurgeResponse(name, purged);
    }

    // ---- Exchanges ----

    public ExchangeResponse createExchange(CreateExchangeRequest request) {
        if (exchangeExists(request.name())) {
            throw new ResourceConflictException("El exchange '" + request.name() + "' ya existe");
        }
        boolean durable = request.isDurable();
        boolean autoDelete = request.isAutoDelete();
        Exchange exchange = switch (request.type()) {
            case DIRECT -> new DirectExchange(request.name(), durable, autoDelete);
            case TOPIC -> new TopicExchange(request.name(), durable, autoDelete);
            case FANOUT -> new FanoutExchange(request.name(), durable, autoDelete);
            case HEADERS -> new HeadersExchange(request.name(), durable, autoDelete);
        };

        call(() -> {
            amqpAdmin.declareExchange(exchange);
            return null;
        });
        log.info("Exchange creado: {} ({}, durable={}, autoDelete={})", exchange.getName(), exchange.getType(), durable, autoDelete);
        return new ExchangeResponse(exchange.getName(), exchange.getType(), durable, autoDelete);
    }

    public void deleteExchange(String name) {
        if (properties.isProtectedExchange(name)) {
            throw new ResourceConflictException("El exchange '" + name + "' es parte de la topologia del sistema y no se puede eliminar");
        }
        if (!exchangeExists(name)) {
            throw new ResourceNotFoundException("El exchange '" + name + "' no existe");
        }
        call(() -> amqpAdmin.deleteExchange(name));
        log.info("Exchange eliminado: {}", name);
    }

    // ---- Bindings ----

    public BindingResponse createBinding(BindingRequest request) {
        requireBindingEnds(request);
        call(() -> {
            amqpAdmin.declareBinding(toBinding(request));
            return null;
        });
        log.info("Binding creado: {} -> {} (routingKey='{}')", request.exchange(), request.queue(), request.routingKeyOrEmpty());
        return new BindingResponse(request.exchange(), request.queue(), request.routingKeyOrEmpty());
    }

    public void deleteBinding(BindingRequest request) {
        if (properties.isProtectedQueue(request.queue())) {
            throw new ResourceConflictException("Los bindings de la cola '" + request.queue() + "' son parte de la topologia del sistema");
        }
        requireBindingEnds(request);
        call(() -> {
            amqpAdmin.removeBinding(toBinding(request));
            return null;
        });
        log.info("Binding eliminado: {} -> {} (routingKey='{}')", request.exchange(), request.queue(), request.routingKeyOrEmpty());
    }

    // ---- Internos ----

    private void requireBindingEnds(BindingRequest request) {
        if (!exchangeExists(request.exchange())) {
            throw new ResourceNotFoundException("El exchange '" + request.exchange() + "' no existe");
        }
        if (queueInfo(request.queue()) == null) {
            throw new ResourceNotFoundException("La cola '" + request.queue() + "' no existe");
        }
    }

    private static Binding toBinding(BindingRequest request) {
        return new Binding(request.queue(), Binding.DestinationType.QUEUE, request.exchange(), request.routingKeyOrEmpty(), null);
    }

    private QueueInformation queueInfo(String name) {
        return call(() -> amqpAdmin.getQueueInfo(name));
    }

    /** AMQP no tiene "listar/consultar exchange": un declare pasivo falla con 404 si no existe. */
    private boolean exchangeExists(String name) {
        try {
            rabbitTemplate.execute(channel -> channel.exchangeDeclarePassive(name));
            return true;
        } catch (AmqpConnectException ex) {
            throw new BrokerUnavailableException("No hay conexion con RabbitMQ", ex);
        } catch (AmqpException ex) {
            if (hasReplyCode(ex, "NOT_FOUND")) {
                return false;
            }
            throw ex;
        }
    }

    /** Traduce las fallas del broker a excepciones del dominio. */
    private <T> T call(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (AmqpConnectException ex) {
            throw new BrokerUnavailableException("No hay conexion con RabbitMQ", ex);
        } catch (AmqpException ex) {
            if (hasReplyCode(ex, "PRECONDITION_FAILED")) {
                throw new ResourceConflictException("RabbitMQ rechazo la operacion: " + shutdownReason(ex));
            }
            if (hasReplyCode(ex, "NOT_FOUND")) {
                throw new ResourceNotFoundException("RabbitMQ no encontro el recurso: " + shutdownReason(ex));
            }
            throw ex;
        }
    }

    private static boolean hasReplyCode(Throwable error, String replyText) {
        String reason = shutdownReason(error);
        return reason != null && reason.contains(replyText);
    }

    private static String shutdownReason(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ShutdownSignalException shutdown) {
                return String.valueOf(shutdown.getReason());
            }
        }
        return null;
    }
}
