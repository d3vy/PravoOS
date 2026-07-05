package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.exception.TemplateNotFoundException;
import com.pravoos.ai.model.dto.CaseDraftDto;
import com.pravoos.ai.model.dto.CreateTemplateRequest;
import com.pravoos.ai.model.dto.TemplateResponse;
import com.pravoos.ai.model.dto.UpdateTemplateRequest;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseDraft;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.model.entity.DocumentTemplate;
import com.pravoos.ai.practice.internal.repository.jpa.CaseDraftRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.DocumentTemplateRepository;
import com.pravoos.ai.util.TemplatePlaceholderResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TemplateService {

    static final String TEMPLATE_DRAFT_TYPE = "TEMPLATE";

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    private final DocumentTemplateRepository templateRepository;
    private final CaseDraftRepository caseDraftRepository;
    private final ClientRepository clientRepository;
    private final CaseService caseService;

    public TemplateService(DocumentTemplateRepository templateRepository,
                           CaseDraftRepository caseDraftRepository,
                           ClientRepository clientRepository,
                           CaseService caseService) {
        this.templateRepository = templateRepository;
        this.caseDraftRepository = caseDraftRepository;
        this.clientRepository = clientRepository;
        this.caseService = caseService;
    }

    @Transactional
    public TemplateResponse create(CreateTemplateRequest request, UUID lawyerId) {
        DocumentTemplate template = new DocumentTemplate();
        template.setLawyerId(lawyerId);
        template.setName(request.name().trim());
        template.setContent(request.content());

        DocumentTemplate saved = templateRepository.save(template);
        log.info("Template created: '{}' ({}) by lawyer {}", saved.getName(), saved.getId(), lawyerId);
        return TemplateResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> findByLawyer(UUID lawyerId) {
        return templateRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)
                .stream()
                .map(TemplateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(UUID templateId, UUID lawyerId) {
        return TemplateResponse.from(requireOwnedTemplate(templateId, lawyerId));
    }

    @Transactional
    public TemplateResponse update(UUID templateId, UpdateTemplateRequest request, UUID lawyerId) {
        DocumentTemplate template = requireOwnedTemplate(templateId, lawyerId);
        template.setName(request.name().trim());
        template.setContent(request.content());
        log.info("Template updated: {} by lawyer {}", templateId, lawyerId);
        return TemplateResponse.from(template);
    }

    @Transactional
    public void delete(UUID templateId, UUID lawyerId) {
        DocumentTemplate template = requireOwnedTemplate(templateId, lawyerId);
        templateRepository.delete(template);
        log.info("Template deleted: {} by lawyer {}", templateId, lawyerId);
    }

    @Transactional
    public CaseDraftDto applyToCase(UUID caseId, UUID templateId, UUID lawyerId, List<UUID> orgIds) {
        Case caseEntity = caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        DocumentTemplate template = requireOwnedTemplate(templateId, lawyerId);
        Client client = resolveClient(caseEntity.getClientId(), caseEntity.getLawyerId());

        String content = TemplatePlaceholderResolver.resolve(template.getContent(), caseEntity, client);

        CaseDraft draft = new CaseDraft();
        draft.setCaseId(caseId);
        draft.setLawyerId(lawyerId);
        draft.setDraftType(TEMPLATE_DRAFT_TYPE);
        draft.setTitle(template.getName());
        draft.setContent(content);

        CaseDraft saved = caseDraftRepository.save(draft);
        log.info("Template {} applied to case {} as draft {} by lawyer {}",
                templateId, caseId, saved.getId(), lawyerId);
        return CaseDraftDto.from(saved);
    }

    private Client resolveClient(UUID clientId, UUID lawyerId) {
        if (clientId == null) {
            return null;
        }
        return clientRepository.findById(clientId)
                .filter(client -> client.getLawyerId().equals(lawyerId))
                .orElse(null);
    }

    private DocumentTemplate requireOwnedTemplate(UUID templateId, UUID lawyerId) {
        return templateRepository.findByIdAndLawyerId(templateId, lawyerId)
                .orElseThrow(() -> new TemplateNotFoundException(templateId));
    }
}
