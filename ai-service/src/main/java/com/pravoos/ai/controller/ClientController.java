package com.pravoos.ai.controller;

import com.pravoos.ai.model.dto.ClientDetailResponse;
import com.pravoos.ai.model.dto.ClientResponse;
import com.pravoos.ai.model.dto.CreateClientRequest;
import com.pravoos.ai.model.dto.UpdateClientRequest;
import com.pravoos.common.web.SecurityUtils;
import com.pravoos.ai.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @PostMapping
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody CreateClientRequest request,
                                                 Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(clientService.create(request, lawyerId));
    }

    @GetMapping
    public ResponseEntity<List<ClientResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(clientService.findByLawyer(SecurityUtils.currentUserId(authentication)));
    }

    @GetMapping("/{clientId}")
    public ResponseEntity<ClientDetailResponse> get(@PathVariable UUID clientId,
                                                    Authentication authentication) {
        return ResponseEntity.ok(clientService.get(clientId, SecurityUtils.currentUserId(authentication)));
    }

    @PutMapping("/{clientId}")
    public ResponseEntity<ClientResponse> update(@PathVariable UUID clientId,
                                                 @Valid @RequestBody UpdateClientRequest request,
                                                 Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(clientService.update(clientId, request, lawyerId));
    }

    @DeleteMapping("/{clientId}")
    public ResponseEntity<Void> delete(@PathVariable UUID clientId,
                                       @RequestParam(defaultValue = "false") boolean cascade,
                                       Authentication authentication) {
        clientService.delete(clientId, SecurityUtils.currentUserId(authentication), cascade);
        return ResponseEntity.noContent().build();
    }
}
