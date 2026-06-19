package com.pravoos.ai.service;

import com.pravoos.ai.exception.ClientContactNotFoundException;
import com.pravoos.ai.model.dto.ContactResponse;
import com.pravoos.ai.model.dto.CreateContactRequest;
import com.pravoos.ai.model.dto.UpdateContactRequest;
import com.pravoos.ai.model.entity.ClientContact;
import com.pravoos.ai.repository.jpa.ClientContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ClientContactService {

    private static final Logger log = LoggerFactory.getLogger(ClientContactService.class);

    private final ClientContactRepository contactRepository;
    private final ClientService clientService;

    public ClientContactService(ClientContactRepository contactRepository, ClientService clientService) {
        this.contactRepository = contactRepository;
        this.clientService = clientService;
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> findByClient(UUID clientId, UUID lawyerId) {
        clientService.requireOwnedClient(clientId, lawyerId);
        return contactRepository.findByClientIdOrderByContactDateDescCreatedAtDesc(clientId)
                .stream()
                .map(ContactResponse::from)
                .toList();
    }

    @Transactional
    public ContactResponse create(UUID clientId, CreateContactRequest request, UUID lawyerId) {
        clientService.requireOwnedClient(clientId, lawyerId);

        ClientContact contact = new ClientContact();
        contact.setClientId(clientId);
        contact.setType(request.type());
        contact.setContactDate(request.contactDate());
        contact.setNotes(blankToNull(request.notes()));

        ClientContact saved = contactRepository.save(contact);
        log.info("Contact {} created for client {} by lawyer {}", saved.getId(), clientId, lawyerId);
        return ContactResponse.from(saved);
    }

    @Transactional
    public ContactResponse update(UUID clientId, UUID contactId, UpdateContactRequest request, UUID lawyerId) {
        ClientContact contact = requireContactInClient(clientId, contactId, lawyerId);
        contact.setType(request.type());
        contact.setContactDate(request.contactDate());
        contact.setNotes(blankToNull(request.notes()));
        log.info("Contact {} updated for client {} by lawyer {}", contactId, clientId, lawyerId);
        return ContactResponse.from(contact);
    }

    @Transactional
    public void delete(UUID clientId, UUID contactId, UUID lawyerId) {
        ClientContact contact = requireContactInClient(clientId, contactId, lawyerId);
        contactRepository.delete(contact);
        log.info("Contact {} deleted for client {} by lawyer {}", contactId, clientId, lawyerId);
    }

    private ClientContact requireContactInClient(UUID clientId, UUID contactId, UUID lawyerId) {
        clientService.requireOwnedClient(clientId, lawyerId);
        ClientContact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new ClientContactNotFoundException(contactId));
        if (!contact.getClientId().equals(clientId)) {
            throw new ClientContactNotFoundException(contactId);
        }
        return contact;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
