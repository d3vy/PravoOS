package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.dto.BillingProfileRequest;
import com.pravoos.ai.practice.internal.dto.BillingProfileResponse;
import com.pravoos.ai.practice.internal.model.entity.BillingProfile;
import com.pravoos.ai.practice.internal.repository.jpa.BillingProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillingProfileServiceTest {

  @Mock private BillingProfileRepository billingProfileRepository;

  @InjectMocks private BillingProfileService billingProfileService;

  @Test
  void findReturnsEmptyWhenNoProfile() {
    UUID lawyerId = UUID.randomUUID();
    when(billingProfileRepository.findById(lawyerId)).thenReturn(Optional.empty());

    assertThat(billingProfileService.find(lawyerId)).isEmpty();
  }

  @Test
  void findMapsExistingProfileToResponse() {
    UUID lawyerId = UUID.randomUUID();
    BillingProfile profile = new BillingProfile();
    profile.setLawyerId(lawyerId);
    profile.setName("ИП Иванов");
    when(billingProfileRepository.findById(lawyerId)).thenReturn(Optional.of(profile));

    Optional<BillingProfileResponse> result = billingProfileService.find(lawyerId);

    assertThat(result).isPresent();
    assertThat(result.get().name()).isEqualTo("ИП Иванов");
  }

  @Test
  void saveCreatesNewProfileWhenNoneExists() {
    UUID lawyerId = UUID.randomUUID();
    when(billingProfileRepository.findById(lawyerId)).thenReturn(Optional.empty());
    when(billingProfileRepository.save(any(BillingProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    BillingProfileRequest request =
        new BillingProfileRequest(
            " ИП Иванов ",
            " 770123456789 ",
            "",
            null,
            "Адрес",
            "Банк",
            "044525225",
            "40702810",
            "30101810",
            "a@b.com",
            "+79990000000");

    BillingProfileResponse response = billingProfileService.save(request, lawyerId);

    ArgumentCaptor<BillingProfile> captor = ArgumentCaptor.forClass(BillingProfile.class);
    verify(billingProfileRepository).save(captor.capture());
    BillingProfile saved = captor.getValue();

    assertThat(saved.getLawyerId()).isEqualTo(lawyerId);
    assertThat(saved.getName()).isEqualTo("ИП Иванов");
    assertThat(saved.getInn()).isEqualTo("770123456789");
    assertThat(saved.getKpp()).isNull();
    assertThat(saved.getOgrn()).isNull();
    assertThat(response.name()).isEqualTo("ИП Иванов");
  }

  @Test
  void saveUpdatesExistingProfileInPlace() {
    UUID lawyerId = UUID.randomUUID();
    BillingProfile existing = new BillingProfile();
    existing.setLawyerId(lawyerId);
    existing.setName("Старое имя");
    when(billingProfileRepository.findById(lawyerId)).thenReturn(Optional.of(existing));
    when(billingProfileRepository.save(any(BillingProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    BillingProfileRequest request =
        new BillingProfileRequest(
            "Новое имя", null, null, null, null, null, null, null, null, null, null);

    billingProfileService.save(request, lawyerId);

    assertThat(existing.getName()).isEqualTo("Новое имя");
  }
}
