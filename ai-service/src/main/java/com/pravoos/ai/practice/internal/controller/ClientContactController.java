package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.ContactResponse;
import com.pravoos.ai.practice.internal.dto.CreateContactRequest;
import com.pravoos.ai.practice.internal.dto.UpdateContactRequest;
import com.pravoos.ai.practice.internal.service.ClientContactService;
import com.pravoos.ai.shared.security.CallerContext;
import com.pravoos.ai.shared.util.PagedResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/clients/{clientId}/contacts")
public class ClientContactController {

  private final ClientContactService contactService;

  public ClientContactController(ClientContactService contactService) {
    this.contactService = contactService;
  }

  @GetMapping
  public ResponseEntity<List<ContactResponse>> list(
      @PathVariable UUID clientId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return PagedResponse.of(contactService.findByClient(clientId, lawyerId, page, size));
  }

  @PostMapping
  public ResponseEntity<ContactResponse> create(
      @PathVariable UUID clientId,
      @Valid @RequestBody CreateContactRequest request,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(contactService.create(clientId, request, lawyerId));
  }

  @PutMapping("/{contactId}")
  public ResponseEntity<ContactResponse> update(
      @PathVariable UUID clientId,
      @PathVariable UUID contactId,
      @Valid @RequestBody UpdateContactRequest request,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(contactService.update(clientId, contactId, request, lawyerId));
  }

  @DeleteMapping("/{contactId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID clientId, @PathVariable UUID contactId, CallerContext caller) {
    UUID lawyerId = caller.userId();
    contactService.delete(clientId, contactId, lawyerId);
    return ResponseEntity.noContent().build();
  }
}
