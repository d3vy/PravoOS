package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.BillingProfileRequest;
import com.pravoos.ai.practice.internal.dto.BillingProfileResponse;
import com.pravoos.ai.practice.internal.model.entity.BillingProfile;
import com.pravoos.ai.practice.internal.repository.jpa.BillingProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingProfileService {

  private final BillingProfileRepository billingProfileRepository;

  public BillingProfileService(BillingProfileRepository billingProfileRepository) {
    this.billingProfileRepository = billingProfileRepository;
  }

  @Transactional(readOnly = true)
  public Optional<BillingProfileResponse> find(UUID lawyerId) {
    return billingProfileRepository.findById(lawyerId).map(BillingProfileResponse::from);
  }

  @Transactional(readOnly = true)
  public Optional<BillingProfile> findEntity(UUID lawyerId) {
    return billingProfileRepository.findById(lawyerId);
  }

  @Transactional
  public BillingProfileResponse save(BillingProfileRequest request, UUID lawyerId) {
    BillingProfile profile =
        billingProfileRepository
            .findById(lawyerId)
            .orElseGet(
                () -> {
                  BillingProfile created = new BillingProfile();
                  created.setLawyerId(lawyerId);
                  return created;
                });
    profile.setName(request.name().trim());
    profile.setInn(trimToNull(request.inn()));
    profile.setKpp(trimToNull(request.kpp()));
    profile.setOgrn(trimToNull(request.ogrn()));
    profile.setLegalAddress(trimToNull(request.legalAddress()));
    profile.setBankName(trimToNull(request.bankName()));
    profile.setBankBic(trimToNull(request.bankBic()));
    profile.setBankAccount(trimToNull(request.bankAccount()));
    profile.setCorrAccount(trimToNull(request.corrAccount()));
    profile.setEmail(trimToNull(request.email()));
    profile.setPhone(trimToNull(request.phone()));
    return BillingProfileResponse.from(billingProfileRepository.save(profile));
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
