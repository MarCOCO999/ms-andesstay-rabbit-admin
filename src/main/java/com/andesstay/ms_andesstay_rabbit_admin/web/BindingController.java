package com.andesstay.ms_andesstay_rabbit_admin.web;

import com.andesstay.ms_andesstay_rabbit_admin.service.RabbitAdminService;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.BindingRequest;
import com.andesstay.ms_andesstay_rabbit_admin.web.dto.BindingResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bindings exchange -> cola. Un binding no tiene nombre propio: se identifica por
 * exchange + cola + routing key.
 * <ul>
 *   <li>POST   /api/rabbit/bindings                                     crea (201; 404 si falta el exchange o la cola)</li>
 *   <li>DELETE /api/rabbit/bindings?exchange=..&amp;queue=..&amp;routingKey=..  elimina (204)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/rabbit/bindings")
@PreAuthorize("hasRole('Admin')")
public class BindingController {

    private final RabbitAdminService rabbitAdminService;

    public BindingController(RabbitAdminService rabbitAdminService) {
        this.rabbitAdminService = rabbitAdminService;
    }

    @PostMapping
    public ResponseEntity<BindingResponse> create(@Valid @RequestBody BindingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rabbitAdminService.createBinding(request));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@Valid @ModelAttribute BindingRequest request) {
        rabbitAdminService.deleteBinding(request);
        return ResponseEntity.noContent().build();
    }
}
