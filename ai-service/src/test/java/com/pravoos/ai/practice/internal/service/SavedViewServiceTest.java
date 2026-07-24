package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.dto.CreateSavedViewRequest;
import com.pravoos.ai.practice.internal.dto.SavedViewResponse;
import com.pravoos.ai.practice.internal.model.SavedViewScope;
import com.pravoos.ai.practice.internal.model.entity.SavedView;
import com.pravoos.ai.practice.internal.repository.jpa.SavedViewRepository;
import com.pravoos.ai.shared.exception.OrganizationAccessException;
import com.pravoos.ai.shared.exception.SavedViewNameTakenException;
import com.pravoos.ai.shared.exception.SavedViewNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavedViewServiceTest {

  private static final String CONFIG = "{\"status\":\"ALL\"}";

  @Mock private SavedViewRepository savedViewRepository;

  private SavedViewService service() {
    return new SavedViewService(savedViewRepository);
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
  void deletingSomeoneElsesViewIsRejected() {
    UUID viewId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    when(savedViewRepository.findByIdAndLawyerId(viewId, lawyerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service().delete(viewId, lawyerId))
        .isInstanceOf(SavedViewNotFoundException.class);
  }
}
