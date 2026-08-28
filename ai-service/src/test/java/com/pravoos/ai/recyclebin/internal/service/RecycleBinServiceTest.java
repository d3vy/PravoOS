package com.pravoos.ai.recyclebin.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pravoos.ai.recyclebin.api.BinContents;
import com.pravoos.ai.recyclebin.api.BinSnapshot;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.DeletionRole;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.recyclebin.api.SoftDeleteStore;
import com.pravoos.ai.recyclebin.internal.config.RecycleBinProperties;
import com.pravoos.ai.recyclebin.internal.model.DeletedEntry;
import com.pravoos.ai.recyclebin.internal.repository.jpa.DeletedEntryRepository;
import com.pravoos.ai.shared.exception.RecycleBinCascadeRestoreException;
import com.pravoos.ai.shared.exception.RecycleBinEntityTypeUnsupportedException;
import com.pravoos.ai.shared.exception.RecycleBinEntryAlreadyRestoredException;
import com.pravoos.ai.shared.exception.RecycleBinEntryNotFoundException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecycleBinServiceTest {

  private static final Duration RETENTION = Duration.ofDays(7);

  @Mock private DeletedEntryRepository deletedEntryRepository;
  @Mock private SoftDeleteStore caseStore;
  @Mock private SoftDeleteStore documentStore;

  private final UUID lawyerId = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();
  private final UUID documentId = UUID.randomUUID();

  @BeforeEach
  void stubStoreTypes() {
    when(caseStore.entityType()).thenReturn(RecycleBinEntityType.CASE);
    when(documentStore.entityType()).thenReturn(RecycleBinEntityType.DOCUMENT);
  }

  private RecycleBinService service(boolean enabled) {
    return new RecycleBinService(
        deletedEntryRepository,
        new RecycleBinProperties(enabled, RETENTION, new RecycleBinProperties.Purge(100)),
        List.of(caseStore, documentStore));
  }

  private DeletionActor actor() {
    return new DeletionActor(lawyerId, DeletionRole.LAWYER, orgId, List.of(orgId));
  }

  private BinSnapshot caseSnapshot() {
    return new BinSnapshot(
        RecycleBinEntityType.CASE,
        caseId.toString(),
        "Иванов против ООО Ромашка",
        lawyerId,
        orgId,
        Map.of("status", "INTAKE"));
  }

  private BinSnapshot documentSnapshot() {
    return new BinSnapshot(
        RecycleBinEntityType.DOCUMENT,
        documentId.toString(),
        "Исковое заявление",
        lawyerId,
        null,
        Map.of());
  }

  private DeletedEntry entry(
      RecycleBinEntityType entityType, String entityId, UUID groupId, boolean root) {
    DeletedEntry entry = new DeletedEntry();
    ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
    entry.setEntityType(entityType);
    entry.setEntityId(entityId);
    entry.setArea(entityType.area());
    entry.setTitle("Запись");
    entry.setOwnerId(lawyerId);
    entry.setOrgId(orgId);
    entry.setDeletedBy(lawyerId);
    entry.setDeletedByRole(DeletionRole.LAWYER);
    entry.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
    entry.setPurgeAfter(LocalDateTime.now(ZoneOffset.UTC).plus(RETENTION));
    entry.setCascadeGroupId(groupId);
    entry.setCascadeRoot(root);
    return entry;
  }

  @Test
  void moveToBinWritesRootAndCascadedEntriesIntoOneGroup() {
    when(caseStore.moveToBin(eq(caseId.toString()), any(), eq(true)))
        .thenReturn(new BinContents(caseSnapshot(), List.of(documentSnapshot())));
    when(deletedEntryRepository.findByEntityTypeAndEntityIdAndRestoredAtIsNull(any(), anyString()))
        .thenReturn(Optional.empty());

    service(true).moveToBin(RecycleBinEntityType.CASE, caseId.toString(), actor());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<DeletedEntry>> captor = ArgumentCaptor.forClass(List.class);
    verify(deletedEntryRepository).saveAll(captor.capture());
    List<DeletedEntry> saved = captor.getValue();
    assertThat(saved).hasSize(2);
    assertThat(saved.get(0).isCascadeRoot()).isTrue();
    assertThat(saved.get(1).isCascadeRoot()).isFalse();
    assertThat(saved.get(1).getCascadeGroupId()).isEqualTo(saved.get(0).getCascadeGroupId());
    assertThat(saved.get(0).getPurgeAfter()).isEqualTo(saved.get(0).getDeletedAt().plus(RETENTION));
  }

  @Test
  void moveToBinIsIdempotentForAnEntryAlreadyInTheBin() {
    when(deletedEntryRepository.findByEntityTypeAndEntityIdAndRestoredAtIsNull(
            RecycleBinEntityType.CASE, caseId.toString()))
        .thenReturn(
            Optional.of(
                entry(RecycleBinEntityType.CASE, caseId.toString(), UUID.randomUUID(), true)));

    service(true).moveToBin(RecycleBinEntityType.CASE, caseId.toString(), actor());

    verify(caseStore, never()).moveToBin(anyString(), any(), eq(true));
    verify(deletedEntryRepository, never()).saveAll(any());
  }

  @Test
  void moveToBinFallsBackToPhysicalDeleteWhenDisabled() {
    service(false).moveToBin(RecycleBinEntityType.CASE, caseId.toString(), actor());

    verify(caseStore).purge(caseId.toString());
    verifyNoInteractions(deletedEntryRepository);
  }

  @Test
  void moveToBinRejectsUnsupportedEntityType() {
    assertThatThrownBy(
            () ->
                service(true)
                    .moveToBin(RecycleBinEntityType.INVOICE, UUID.randomUUID().toString(), actor()))
        .isInstanceOf(RecycleBinEntityTypeUnsupportedException.class);
  }

  @Test
  void restoreOfARootRestoresTheWholeCascadeGroup() {
    UUID groupId = UUID.randomUUID();
    DeletedEntry root = entry(RecycleBinEntityType.CASE, caseId.toString(), groupId, true);
    DeletedEntry child =
        entry(RecycleBinEntityType.DOCUMENT, documentId.toString(), groupId, false);
    when(deletedEntryRepository.findById(root.getId())).thenReturn(Optional.of(root));
    when(deletedEntryRepository.findByCascadeGroupIdAndRestoredAtIsNull(groupId))
        .thenReturn(List.of(root, child));

    service(true).restore(root.getId(), actor());

    verify(caseStore).restore(caseId.toString());
    verify(documentStore).restore(documentId.toString());
    assertThat(root.getRestoredAt()).isNotNull();
    assertThat(child.getRestoredAt()).isNotNull();
  }

  @Test
  void restoreOfANestedEntryIsRejectedWhileItsRootIsStillInTheBin() {
    UUID groupId = UUID.randomUUID();
    DeletedEntry root = entry(RecycleBinEntityType.CASE, caseId.toString(), groupId, true);
    DeletedEntry child =
        entry(RecycleBinEntityType.DOCUMENT, documentId.toString(), groupId, false);
    when(deletedEntryRepository.findById(child.getId())).thenReturn(Optional.of(child));
    when(deletedEntryRepository.findByCascadeGroupIdAndRestoredAtIsNull(groupId))
        .thenReturn(List.of(root, child));

    assertThatThrownBy(() -> service(true).restore(child.getId(), actor()))
        .isInstanceOf(RecycleBinCascadeRestoreException.class);
    verify(documentStore, never()).restore(anyString());
  }

  @Test
  void restoreRejectsAnEntryThatIsAlreadyRestored() {
    DeletedEntry restored =
        entry(RecycleBinEntityType.CASE, caseId.toString(), UUID.randomUUID(), true);
    restored.setRestoredAt(LocalDateTime.now(ZoneOffset.UTC));
    when(deletedEntryRepository.findById(restored.getId())).thenReturn(Optional.of(restored));

    assertThatThrownBy(() -> service(true).restore(restored.getId(), actor()))
        .isInstanceOf(RecycleBinEntryAlreadyRestoredException.class);
  }

  @Test
  void restoreHidesEntriesOfAnotherLawyerAndOrganization() {
    DeletedEntry foreign =
        entry(RecycleBinEntityType.CASE, caseId.toString(), UUID.randomUUID(), true);
    foreign.setOwnerId(UUID.randomUUID());
    foreign.setOrgId(UUID.randomUUID());
    when(deletedEntryRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> service(true).restore(foreign.getId(), actor()))
        .isInstanceOf(RecycleBinEntryNotFoundException.class);
    verify(caseStore, never()).restore(anyString());
  }

  @Test
  void adminPurgeRemovesNestedEntriesBeforeTheRoot() {
    UUID groupId = UUID.randomUUID();
    DeletedEntry root = entry(RecycleBinEntityType.CASE, caseId.toString(), groupId, true);
    DeletedEntry child =
        entry(RecycleBinEntityType.DOCUMENT, documentId.toString(), groupId, false);
    when(deletedEntryRepository.findById(root.getId())).thenReturn(Optional.of(root));
    when(deletedEntryRepository.findByCascadeGroupIdAndRestoredAtIsNull(groupId))
        .thenReturn(List.of(root, child));
    DeletionActor admin = new DeletionActor(UUID.randomUUID(), DeletionRole.ADMIN, null, List.of());

    service(true).purgeNow(root.getId(), admin);

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(documentStore, caseStore);
    order.verify(documentStore).purge(documentId.toString());
    order.verify(caseStore).purge(caseId.toString());
    verify(deletedEntryRepository).delete(child);
    verify(deletedEntryRepository).delete(root);
  }
}
