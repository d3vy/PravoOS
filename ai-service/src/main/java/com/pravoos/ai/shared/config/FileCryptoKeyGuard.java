package com.pravoos.ai.shared.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("docker")
public class FileCryptoKeyGuard {

    private final FileCryptoProperties fileCryptoProperties;

    public FileCryptoKeyGuard(FileCryptoProperties fileCryptoProperties) {
        this.fileCryptoProperties = fileCryptoProperties;
    }

    @PostConstruct
    void verifyKeyPresent() {
        if (!fileCryptoProperties.hasKey()) {
            throw new IllegalStateException(
                    "FILE_ENCRYPTION_KEY must be set in production (profile 'docker'). "
                            + "Without it, client documents are stored on disk in plaintext.");
        }
    }
}
