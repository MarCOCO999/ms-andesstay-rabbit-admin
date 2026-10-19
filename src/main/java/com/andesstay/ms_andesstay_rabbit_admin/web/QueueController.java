package com.andesstay.ms_andesstay_rabbit_admin.web;

import com.andesstay.ms_andesstay_rabbit_admin.service.RabbitAdminService;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.CreateQueueRequest;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.PurgeResponse;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.QueueResponse;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.ResourceNames;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Colas de RabbitMQ.
 * <ul>
 *   <li>POST   /api/rabbit/queues                  crea una cola (201, 409 si ya existe)</li>
 *   <li>GET    /api/rabbit/queues/{name}           mensajes y consumidores (404 si no existe)</li>
 *   <li>DELETE /api/rabbit/queues/{name}           elimina (204; ?ifUnused=true&amp;ifEmpty=true para exigirlo)</li>
 *   <li>DELETE /api/rabbit/queues/{name}/messages  vacia la cola (200 con la cantidad eliminada)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/rabbit/queues")
@PreAuthorize("hasRole('Admin')")
public class QueueController {

    private final RabbitAdminService rabbitAdminService;

    public QueueController(RabbitAdminService rabbitAdminService) {
        this.rabbitAdminService = rabbitAdminService;
    }

    @PostMapping
    public ResponseEntity<QueueResponse> create(@Valid @RequestBody CreateQueueRequest request) {
        QueueResponse created = rabbitAdminService.createQueue(request);
        return ResponseEntity.created(URI.create("/api/rabbit/queues/" + created.name())).body(created);
    }

    @GetMapping("/{name}")
    public QueueResponse get(@PathVariable @Pattern(regexp = ResourceNames.NAME_REGEX, message = ResourceNames.NAME_MESSAGE) String name) {
        return rabbitAdminService.getQueue(name);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable @Pattern(regexp = ResourceNames.NAME_REGEX, message = ResourceNames.NAME_MESSAGE) String name,
                                       @RequestParam(defaultValue = "false") boolean ifUnused,
                                       @RequestParam(defaultValue = "false") boolean ifEmpty) {
        rabbitAdminService.deleteQueue(name, ifUnused, ifEmpty);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{name}/messages")
    public PurgeResponse purge(@PathVariable @Pattern(regexp = ResourceNames.NAME_REGEX, message = ResourceNames.NAME_MESSAGE) String name) {
        return rabbitAdminService.purgeQueue(name);
    }
}
