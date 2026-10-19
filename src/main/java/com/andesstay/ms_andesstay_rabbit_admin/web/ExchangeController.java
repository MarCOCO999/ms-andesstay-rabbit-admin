package com.andesstay.ms_andesstay_rabbit_admin.web;

import com.andesstay.ms_andesstay_rabbit_admin.service.RabbitAdminService;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.CreateExchangeRequest;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.ExchangeResponse;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.ResourceNames;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Exchanges de RabbitMQ.
 * <ul>
 *   <li>POST   /api/rabbit/exchanges         crea un exchange direct/topic/fanout/headers (201, 409 si ya existe)</li>
 *   <li>DELETE /api/rabbit/exchanges/{name}  elimina (204, 404 si no existe, 409 si es del sistema)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/rabbit/exchanges")
@PreAuthorize("hasRole('Admin')")
public class ExchangeController {

    private final RabbitAdminService rabbitAdminService;

    public ExchangeController(RabbitAdminService rabbitAdminService) {
        this.rabbitAdminService = rabbitAdminService;
    }

    @PostMapping
    public ResponseEntity<ExchangeResponse> create(@Valid @RequestBody CreateExchangeRequest request) {
        ExchangeResponse created = rabbitAdminService.createExchange(request);
        return ResponseEntity.created(URI.create("/api/rabbit/exchanges/" + created.name())).body(created);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable @Pattern(regexp = ResourceNames.NAME_REGEX, message = ResourceNames.NAME_MESSAGE) String name) {
        rabbitAdminService.deleteExchange(name);
        return ResponseEntity.noContent().build();
    }
}
