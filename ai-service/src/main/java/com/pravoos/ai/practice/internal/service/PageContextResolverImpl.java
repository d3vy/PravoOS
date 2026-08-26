package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.api.PageContextResolver;
import com.pravoos.ai.core.api.PageContextScope;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseDraft;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.practice.internal.repository.jpa.CaseDraftRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PageContextResolverImpl implements PageContextResolver {

  private static final Logger log = LoggerFactory.getLogger(PageContextResolverImpl.class);

  private final CaseService caseService;
  private final CaseDraftRepository caseDraftRepository;
  private final ClientRepository clientRepository;
  private final InvoiceRepository invoiceRepository;

  public PageContextResolverImpl(
      CaseService caseService,
      CaseDraftRepository caseDraftRepository,
      ClientRepository clientRepository,
      InvoiceRepository invoiceRepository) {
    this.caseService = caseService;
    this.caseDraftRepository = caseDraftRepository;
    this.clientRepository = clientRepository;
    this.invoiceRepository = invoiceRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public PageContextScope resolve(
      String entityType, UUID entityId, UUID lawyerId, List<UUID> orgIds) {
    if (entityType == null || entityId == null) {
      return PageContextScope.none();
    }
    return switch (entityType) {
      case "CASE" -> resolveCase(entityId, lawyerId, orgIds);
      case "DRAFT" -> resolveDraft(entityId, lawyerId, orgIds);
      case "CLIENT" -> resolveClient(entityId, lawyerId);
      case "INVOICE" -> resolveInvoice(entityId, lawyerId);
      default -> PageContextScope.none();
    };
  }

  private PageContextScope resolveCase(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
    return visibleCase(caseId, lawyerId, orgIds)
        .map(caseEntity -> PageContextScope.ofCase(caseEntity.getId(), caseEntity.getTitle()))
        .orElseGet(PageContextScope::none);
  }

  private PageContextScope resolveDraft(UUID draftId, UUID lawyerId, List<UUID> orgIds) {
    Optional<CaseDraft> draft = caseDraftRepository.findById(draftId);
    if (draft.isEmpty()) {
      return PageContextScope.none();
    }
    CaseDraft caseDraft = draft.get();
    return visibleCase(caseDraft.getCaseId(), lawyerId, orgIds)
        .map(caseEntity -> PageContextScope.ofCase(caseEntity.getId(), caseDraft.getTitle()))
        .orElseGet(PageContextScope::none);
  }

  private PageContextScope resolveClient(UUID clientId, UUID lawyerId) {
    return clientRepository
        .findById(clientId)
        .filter(client -> ownedBy(client, lawyerId))
        .map(client -> PageContextScope.ofLabel(client.getName()))
        .orElseGet(PageContextScope::none);
  }

  private PageContextScope resolveInvoice(UUID invoiceId, UUID lawyerId) {
    return invoiceRepository
        .findById(invoiceId)
        .filter(invoice -> ownedBy(invoice, lawyerId))
        .map(invoice -> PageContextScope.ofLabel(invoice.getNumber()))
        .orElseGet(PageContextScope::none);
  }

  private Optional<Case> visibleCase(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
    if (caseId == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(caseService.requireVisibleCase(caseId, lawyerId, orgIds));
    } catch (RuntimeException ex) {
      log.debug("Page context case {} is not visible to lawyer {}", caseId, lawyerId);
      return Optional.empty();
    }
  }

  private boolean ownedBy(Client client, UUID lawyerId) {
    return client.getLawyerId() != null && client.getLawyerId().equals(lawyerId);
  }

  private boolean ownedBy(Invoice invoice, UUID lawyerId) {
    return invoice.getLawyerId() != null && invoice.getLawyerId().equals(lawyerId);
  }
}
