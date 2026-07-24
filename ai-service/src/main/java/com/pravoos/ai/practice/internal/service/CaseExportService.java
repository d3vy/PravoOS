package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.api.AiResponseQuery;
import com.pravoos.ai.core.api.SourceReference;
import com.pravoos.ai.document.api.DocumentQuery;
import com.pravoos.ai.practice.internal.dto.CaseExportModel;
import com.pravoos.ai.practice.internal.dto.CaseExportModel.*;
import com.pravoos.ai.practice.internal.dto.ExportedFile;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.repository.jpa.CaseDraftRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.shared.model.enums.DocumentStatus;
import com.pravoos.ai.shared.model.enums.DraftType;
import com.pravoos.ai.shared.model.enums.ExportFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseExportService {

  private static final Logger log = LoggerFactory.getLogger(CaseExportService.class);

  private final CaseService caseService;
  private final ClientRepository clientRepository;
  private final DocumentQuery documentQuery;
  private final AiResponseQuery aiResponseQuery;
  private final CaseDraftRepository caseDraftRepository;
  private final CaseTaskRepository caseTaskRepository;
  private final CaseDocxWriter docxWriter;
  private final CasePdfWriter pdfWriter;

  public CaseExportService(
      CaseService caseService,
      ClientRepository clientRepository,
      DocumentQuery documentQuery,
      AiResponseQuery aiResponseQuery,
      CaseDraftRepository caseDraftRepository,
      CaseTaskRepository caseTaskRepository,
      CaseDocxWriter docxWriter,
      CasePdfWriter pdfWriter) {
    this.caseService = caseService;
    this.clientRepository = clientRepository;
    this.documentQuery = documentQuery;
    this.aiResponseQuery = aiResponseQuery;
    this.caseDraftRepository = caseDraftRepository;
    this.caseTaskRepository = caseTaskRepository;
    this.docxWriter = docxWriter;
    this.pdfWriter = pdfWriter;
  }

  @Transactional(readOnly = true)
  public ExportedFile export(UUID caseId, UUID lawyerId, String formatValue, List<UUID> orgIds) {
    ExportFormat format = ExportFormat.parse(formatValue);
    Case caseEntity = caseService.requireVisibleCase(caseId, lawyerId, orgIds);
    CaseExportModel model = assemble(caseEntity);

    byte[] content =
        switch (format) {
          case DOCX -> docxWriter.write(model);
          case PDF -> pdfWriter.write(model);
        };

    String fileName = buildFileName(caseEntity.getTitle(), format);
    log.info(
        "Case {} exported as {} by lawyer {} ({} bytes)", caseId, format, lawyerId, content.length);
    return new ExportedFile(content, fileName, format.contentType());
  }

  private CaseExportModel assemble(Case caseEntity) {
    UUID caseId = caseEntity.getId();

    ClientSection client =
        caseEntity.getClientId() == null
            ? null
            : clientRepository
                .findById(caseEntity.getClientId())
                .map(
                    c ->
                        new ClientSection(
                            c.getName(),
                            c.getType().getDisplayName(),
                            c.getPhone(),
                            c.getEmail(),
                            c.getInn(),
                            c.getNotes()))
                .orElse(null);

    List<DocumentSection> documents =
        documentQuery.findByCase(caseId).stream()
            .map(
                d ->
                    new DocumentSection(
                        d.title(), d.fileName(), statusLabel(d.status()), d.uploadedAt()))
            .toList();

    List<TaskSection> tasks =
        caseTaskRepository.findByCaseIdOrderByDoneAscCreatedAtAsc(caseId).stream()
            .map(t -> new TaskSection(t.getText(), t.isDone(), t.getDueDate()))
            .toList();

    List<ResponseSection> responses =
        aiResponseQuery.listByCase(caseId).stream()
            .map(
                dto ->
                    new ResponseSection(
                        dto.workflowName(),
                        dto.query(),
                        dto.result(),
                        dto.sources().stream().map(SourceReference::title).toList(),
                        dto.createdAt()))
            .toList();

    List<DraftSection> drafts =
        caseDraftRepository.findByCaseIdOrderByCreatedAtDesc(caseId).stream()
            .map(
                d ->
                    new DraftSection(
                        draftTypeLabel(d.getDraftType()),
                        d.getTitle(),
                        d.getContent(),
                        d.getCreatedAt()))
            .toList();

    return new CaseExportModel(
        caseEntity.getTitle(),
        caseEntity.getDescription(),
        caseEntity.getStatus().getDisplayName(),
        caseEntity.getCreatedAt(),
        client,
        documents,
        tasks,
        responses,
        drafts);
  }

  private String buildFileName(String title, ExportFormat format) {
    String safeTitle = title.replaceAll("[^а-яА-ЯёЁa-zA-Z0-9]", "_").replaceAll("_+", "_");
    return "Дело_" + safeTitle + "." + format.extension();
  }

  private String statusLabel(DocumentStatus status) {
    return switch (status) {
      case READY -> "Обработан";
      case PROCESSING -> "Обрабатывается";
      case FAILED -> "Ошибка обработки";
    };
  }

  private String draftTypeLabel(String draftType) {
    try {
      return DraftType.valueOf(draftType).displayName();
    } catch (IllegalArgumentException ex) {
      return draftType;
    }
  }
}
