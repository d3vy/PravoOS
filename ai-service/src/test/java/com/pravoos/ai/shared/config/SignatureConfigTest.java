package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.shared.config.SignatureProperties.Diadoc;
import com.pravoos.ai.shared.signature.ExternalSignatureProvider;
import com.pravoos.ai.shared.signature.NoopDiadocSignatureProvider;
import org.junit.jupiter.api.Test;

class SignatureConfigTest {

  private final SignatureConfig config = new SignatureConfig();

  @Test
  void returnsNoopProviderWhenDiadocKeyMissing() {
    SignatureProperties properties =
        new SignatureProperties(30, new Diadoc("https://diadoc", null), null);

    ExternalSignatureProvider provider = config.diadocSignatureProvider(properties);

    assertThat(provider).isInstanceOf(NoopDiadocSignatureProvider.class);
    assertThat(provider.isEnabled()).isFalse();
  }

  @Test
  void returnsNoopProviderWhenDiadocKeyPresent() {
    SignatureProperties properties =
        new SignatureProperties(30, new Diadoc("https://diadoc", "api-key"), null);

    ExternalSignatureProvider provider = config.diadocSignatureProvider(properties);

    assertThat(provider).isInstanceOf(NoopDiadocSignatureProvider.class);
  }
}
