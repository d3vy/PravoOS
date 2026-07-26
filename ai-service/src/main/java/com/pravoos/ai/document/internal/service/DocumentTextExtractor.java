package com.pravoos.ai.document.internal.service;

import com.pravoos.ai.document.internal.model.entity.Document;
import com.pravoos.ai.document.internal.pipeline.DocumentParser;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DocumentTextExtractor {

  private static final Logger log = LoggerFactory.getLogger(DocumentTextExtractor.class);

  private final FileCryptoService fileCryptoService;
  private final DocumentParser documentParser;

  public DocumentTextExtractor(FileCryptoService fileCryptoService, DocumentParser documentParser) {
    this.fileCryptoService = fileCryptoService;
    this.documentParser = documentParser;
  }

  public String extractText(Document document) {
    Path path = Paths.get(document.getFilePath());
    if (!Files.isReadable(path)) {
      log.warn("Document {} has missing file on disk: {}", document.getId(), path);
      throw new DocumentNotFoundException(document.getId());
    }
    byte[] content = fileCryptoService.decryptFile(path);
    return documentParser.extractText(content, document.getFileType());
  }
}
