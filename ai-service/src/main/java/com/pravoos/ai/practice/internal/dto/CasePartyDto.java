package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.CaseParty;

public record CasePartyDto(String name, String role) {

  public static CasePartyDto from(CaseParty party) {
    return new CasePartyDto(party.getName(), party.getRole());
  }
}
