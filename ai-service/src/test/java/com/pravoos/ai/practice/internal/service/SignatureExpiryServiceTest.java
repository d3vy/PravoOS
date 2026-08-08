package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.model.entity.SignatureRequest;
import com.pravoos.ai.practice.internal.repository.jpa.SignatureRequestRepository;
import com.pravoos.ai.shared.model.enums.SignatureStatus;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignatureExpiryServiceTest {

  @Mock private SignatureRequestRepository signatureRequestRepository;

  @InjectMocks private SignatureExpiryService service;

  @Test
  void sweepExpired_marksOverduePendingRequests() {
    SignatureRequest overdue = pending(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
    when(signatureRequestRepository.findByStatusAndExpiresAtBefore(
            eq(SignatureStatus.PENDING), any()))
        .thenReturn(List.of(overdue));

    int swept = service.sweepExpired();

    assertThat(swept).isEqualTo(1);
    assertThat(overdue.getStatus()).isEqualTo(SignatureStatus.EXPIRED);
    ArgumentCaptor<List<SignatureRequest>> saved = ArgumentCaptor.forClass(List.class);
    verify(signatureRequestRepository).saveAll(saved.capture());
    assertThat(saved.getValue()).containsExactly(overdue);
  }

  @Test
  void sweepExpired_doesNothingWhenNoOverdueRequests() {
    when(signatureRequestRepository.findByStatusAndExpiresAtBefore(
            eq(SignatureStatus.PENDING), any()))
        .thenReturn(List.of());

    assertThat(service.sweepExpired()).isZero();
    verify(signatureRequestRepository, never()).saveAll(any());
  }

  private SignatureRequest pending(LocalDateTime expiresAt) {
    SignatureRequest request = new SignatureRequest();
    request.setStatus(SignatureStatus.PENDING);
    request.setExpiresAt(expiresAt);
    return request;
  }
}
