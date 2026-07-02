package com.pravoos.user.security;

import com.pravoos.user.config.InternalSecretProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;

class InternalSecretVerifierTest {

    private final InternalSecretVerifier verifier =
            new InternalSecretVerifier(new InternalSecretProperties("super-secret-value"));

    @Test
    void matches_returnsTrue_forCorrectSecret() {
        assertThat(verifier.matches("super-secret-value")).isTrue();
    }

    @Test
    void matches_returnsFalse_forWrongOrNullSecret() {
        assertThat(verifier.matches("wrong")).isFalse();
        assertThat(verifier.matches(null)).isFalse();
    }

    @Test
    void verify_throwsForbidden_forWrongSecret() {
        assertThatThrownBy(() -> verifier.verify("wrong"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void verify_passes_forCorrectSecret() {
        assertThatCode(() -> verifier.verify("super-secret-value")).doesNotThrowAnyException();
    }

    @Test
    void matches_returnsFalse_whenConfiguredSecretBlank() {
        InternalSecretVerifier blankVerifier = new InternalSecretVerifier(new InternalSecretProperties(""));
        assertThat(blankVerifier.matches("")).isFalse();
        assertThat(blankVerifier.matches("anything")).isFalse();
        assertThat(blankVerifier.matches(null)).isFalse();
    }
}
