package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.BillingProfile;
import java.time.LocalDateTime;

public record BillingProfileResponse(
    String name,
    String inn,
    String kpp,
    String ogrn,
    String legalAddress,
    String bankName,
    String bankBic,
    String bankAccount,
    String corrAccount,
    String email,
    String phone,
    LocalDateTime updatedAt) {
  public static BillingProfileResponse from(BillingProfile profile) {
    return new BillingProfileResponse(
        profile.getName(),
        profile.getInn(),
        profile.getKpp(),
        profile.getOgrn(),
        profile.getLegalAddress(),
        profile.getBankName(),
        profile.getBankBic(),
        profile.getBankAccount(),
        profile.getCorrAccount(),
        profile.getEmail(),
        profile.getPhone(),
        profile.getUpdatedAt());
  }
}
