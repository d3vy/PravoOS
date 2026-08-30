package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.dto.CreateSavedViewRequest;
import com.pravoos.ai.practice.internal.dto.SavedViewResponse;
import com.pravoos.ai.practice.internal.model.SavedViewScope;
import com.pravoos.ai.practice.internal.model.entity.SavedView;
import com.pravoos.ai.practice.internal.repository.jpa.SavedViewRepository;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.DeletionRole;
import com.pravoos.ai.recyclebin.api.RecycleBin;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.shared.exception.OrganizationAccessException;
import com.pravoos.ai.shared.exception.SavedViewNameTakenException;
import com.pravoos.ai.shared.exception.SavedViewNotFoundException;
import com.pravoos.ai.shared.exception.SavedViewOrgRequiredException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SavedViewServiceTest {

  private static final String CONFIG = "{\"status\":\"ALL\"}";

  @Mock private SavedViewRepository savedViewRepository;
  @Mock private RecycleBin recycleBin;

  private SavedViewService service() {
    return new SavedViewService(savedViewRepository, recycleBin, new SavedViewCatalogCache(), true);
  }

  @Test
  void privateViewHasNoOrganization() {
    UUID lawyerId = UUID.randomUUID();
    when(savedViewRepository.save(any(SavedView.class))).thenAnswer(call -> call.getArgument(0));

    SavedViewResponse created =
        service()
            .create(
                new CreateSavedViewRequest(
                    SavedViewScope.CASES, "  Мои дела  ", CONFIG, false, UUID.randomUUID()),
                lawyerId,
                List.of(UUID.randomUUID()));

    assertThat(created.name()).isEqualTo("Мои дела");
    assertThat(created.sharedWithTeam()).isFalse();
    assertThat(created.orgId()).isNull();
    assertThat(created.owned()).isTrue();
  }

  @Test
  void sharedViewKeepsRequestedOrganization() {
    UUID lawyerId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    when(savedViewRepository.save(any(SavedView.class))).thenAnswer(call -> call.getArgument(0));

    SavedViewResponse created =
        service()
            .create(
                new CreateSavedViewRequest(SavedViewScope.CASES, "Команда", CONFIG, true, orgId),
                lawyerId,
                List.of(orgId, UUID.randomUUID()));

    assertThat(created.sharedWithTeam()).isTrue();
    assertThat(created.orgId()).isEqualTo(orgId);
  }

  @Test
  void sharedViewFallsBackToTheSingleOrganizationOfTheCaller() {
    UUID orgId = UUID.randomUUID();
    when(savedViewRepository.save(any(SavedView.class))).thenAnswer(call -> call.getArgument(0));

    SavedViewResponse created =
        service()
            .create(
                new CreateSavedViewRequest(SavedViewScope.CASES, "Команда", CONFIG, true, null),
                UUID.randomUUID(),
                List.of(orgId));

    assertThat(created.orgId()).isEqualTo(orgId);
  }

  @Test
  void sharingWithForeignOrganizationIsRejected() {
    assertThatThrownBy(
            () ->
                service()
                    .create(
                        new CreateSavedViewRequest(
                            SavedViewScope.CASES, "Чужая", CONFIG, true, UUID.randomUUID()),
                        UUID.randomUUID(),
                        List.of(UUID.randomUUID())))
        .isInstanceOf(OrganizationAccessException.class);
  }

  @Test
  void duplicateNameInSameScopeIsRejected() {
    UUID lawyerId = UUID.randomUUID();
    when(savedViewRepository.existsByLawyerIdAndScopeAndName(
            lawyerId, SavedViewScope.CASES, "Просрочки"))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                service()
                    .create(
                        new CreateSavedViewRequest(
                            SavedViewScope.CASES, "Просрочки", CONFIG, false, null),
                        lawyerId,
                        List.of()))
        .isInstanceOf(SavedViewNameTakenException.class);
  }

  @Test
  void concurrentNameRaceIsReportedAsNameTaken() {
    UUID lawyerId = UUID.randomUUID();
    when(savedViewRepository.existsByLawyerIdAndScopeAndName(
            lawyerId, SavedViewScope.CASES, "Гонка"))
        .thenReturn(false);
    when(savedViewRepository.save(any(SavedView.class)))
        .thenThrow(new DataIntegrityViolationException("uq_saved_views_owner_scope_name"));

    assertThatThrownBy(
            () ->
                service()
                    .create(
                        new CreateSavedViewRequest(
                            SavedViewScope.CASES, "Гонка", CONFIG, false, null),
                        lawyerId,
                        List.of()))
        .isInstanceOf(SavedViewNameTakenException.class);
  }

  @Test
  void sharingWithAmbiguousOrganizationRequiresExplicitOrgId() {
    assertThatThrownBy(
            () ->
                service()
                    .create(
                        new CreateSavedViewRequest(
                            SavedViewScope.CASES, "Много организаций", CONFIG, true, null),
                        UUID.randomUUID(),
                        List.of(UUID.randomUUID(), UUID.randomUUID())))
        .isInstanceOf(SavedViewOrgRequiredException.class);
  }

  @Test
  void deletingSomeoneElsesViewIsRejected() {
    UUID viewId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    when(savedViewRepository.findByIdAndLawyerId(viewId, lawyerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service().delete(viewId, actor(lawyerId)))
        .isInstanceOf(SavedViewNotFoundException.class);
    verifyNoInteractions(recycleBin);
  }

  @Test
  void deleteMovesOwnedViewToRecycleBin() {
    UUID viewId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    SavedView view = new SavedView();
    view.setLawyerId(lawyerId);
    when(savedViewRepository.findByIdAndLawyerId(viewId, lawyerId)).thenReturn(Optional.of(view));
    DeletionActor actor = actor(lawyerId);

    service().delete(viewId, actor);

    verify(recycleBin).moveToBin(RecycleBinEntityType.SAVED_VIEW, viewId.toString(), actor);
  }

  private DeletionActor actor(UUID lawyerId) {
    return new DeletionActor(lawyerId, DeletionRole.LAWYER, null, List.of());
  }

  @Test
  void findVisibleCacheDoesNotLeakBetweenOrganizations() {
    UUID lawyerId = UUID.randomUUID();
    UUID orgA = UUID.randomUUID();
    UUID orgB = UUID.randomUUID();
    SavedView sharedInOrgA = new SavedView();
    sharedInOrgA.setLawyerId(UUID.randomUUID());
    sharedInOrgA.setSharedWithTeam(true);
    sharedInOrgA.setOrgId(orgA);
    sharedInOrgA.setName("Вид организации A");
    SavedView sharedInOrgB = new SavedView();
    sharedInOrgB.setLawyerId(UUID.randomUUID());
    sharedInOrgB.setSharedWithTeam(true);
    sharedInOrgB.setOrgId(orgB);
    sharedInOrgB.setName("Вид организации B");
    when(savedViewRepository.findVisible(lawyerId, List.of(orgA), SavedViewScope.CASES))
        .thenReturn(List.of(sharedInOrgA));
    when(savedViewRepository.findVisible(lawyerId, List.of(orgB), SavedViewScope.CASES))
        .thenReturn(List.of(sharedInOrgB));
    SavedViewService service = service();

    List<SavedViewResponse> forOrgA =
        service.findVisible(SavedViewScope.CASES, lawyerId, List.of(orgA));
    List<SavedViewResponse> forOrgB =
        service.findVisible(SavedViewScope.CASES, lawyerId, List.of(orgB));

    assertThat(forOrgA).extracting(SavedViewResponse::name).containsExactly("Вид организации A");
    assertThat(forOrgB).extracting(SavedViewResponse::name).containsExactly("Вид организации B");
  }
}
