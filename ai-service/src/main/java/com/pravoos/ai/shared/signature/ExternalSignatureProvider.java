package com.pravoos.ai.shared.signature;

import com.pravoos.ai.shared.model.enums.SignatureProviderType;

public interface ExternalSignatureProvider {

    SignatureProviderType type();

    boolean isEnabled();
}
