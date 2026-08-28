package com.pravoos.ai.document.api;

import com.pravoos.ai.recyclebin.api.BinSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentCommand {

  DocumentUploadResponse upload(MultipartFile file, String title, UUID uploadedBy, UUID caseId);

  DocumentUploadResponse uploadChatAttachment(MultipartFile file, String title, UUID lawyerId);

  DocumentUploadResponse upload(
      MultipartFile file, String title, UUID uploadedBy, UUID caseId, boolean visibleToClient);

  void purgeByCase(UUID caseId);

  List<BinSnapshot> moveCaseDocumentsToBin(UUID caseId);

  DocumentResponse setClientVisibility(UUID documentId, UUID caseId, boolean visibleToClient);

  DocumentResponse attachToCase(UUID documentId, UUID caseId);

  DocumentContent loadClientContent(UUID documentId, UUID caseId);

  DocumentRef clientVisibleRef(UUID documentId, UUID caseId);

  String contentSha256(UUID documentId, UUID caseId);

  DocumentContent loadCaseContent(UUID documentId, UUID caseId);

  DocumentRef caseRef(UUID documentId, UUID caseId);

  String caseContentSha256(UUID documentId, UUID caseId);
}
