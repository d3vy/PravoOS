package com.pravoos.ai.shared.signature;

import com.pravoos.ai.shared.model.enums.SignatureProviderType;

public class NoopDiadocSignatureProvider implements ExternalSignatureProvider {

  @Override
  public SignatureProviderType type() {
    return SignatureProviderType.DIADOC;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }
}
