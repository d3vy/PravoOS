package com.pravoos.user.identity.internal.service;

import com.pravoos.user.identity.internal.config.PasswordPolicyProperties;
import com.pravoos.user.shared.exception.WeakPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

@Service
public class PasswordPolicyService {

    private static final Logger log = LoggerFactory.getLogger(PasswordPolicyService.class);
    private static final String BLACKLIST_RESOURCE = "security/common-passwords.txt";
    private static final String HIBP_BASE_URL = "https://api.pwnedpasswords.com";
    private static final int HIBP_PREFIX_LENGTH = 5;

    private final Set<String> blacklist;
    private final PasswordPolicyProperties properties;
    private final RestClient hibpClient;

    public PasswordPolicyService(PasswordPolicyProperties properties) {
        this.properties = properties;
        this.blacklist = loadBlacklist();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.hibpTimeout());
        requestFactory.setReadTimeout(properties.hibpTimeout());
        this.hibpClient = RestClient.builder()
                .baseUrl(HIBP_BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    public void validate(String password) {
        if (blacklist.contains(password.toLowerCase())) {
            throw new WeakPasswordException("Этот пароль слишком распространён. Выберите более надёжный.");
        }
        if (properties.hibpEnabled() && isPwned(password)) {
            throw new WeakPasswordException("Этот пароль найден в утечках данных. Выберите другой пароль.");
        }
    }

    private boolean isPwned(String password) {
        String sha1 = sha1UpperHex(password);
        String prefix = sha1.substring(0, HIBP_PREFIX_LENGTH);
        String suffix = sha1.substring(HIBP_PREFIX_LENGTH);
        try {
            String body = hibpClient.get()
                    .uri("/range/{prefix}", prefix)
                    .header("Add-Padding", "true")
                    .retrieve()
                    .body(String.class);
            if (body == null) {
                return false;
            }
            for (String line : body.split("\\R")) {
                int separator = line.indexOf(':');
                if (separator > 0 && line.substring(0, separator).equalsIgnoreCase(suffix)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            if (properties.hibpFailOpen()) {
                log.warn("HIBP check unavailable, allowing password (fail-open): {}", ex.getMessage());
                return false;
            }
            log.error("HIBP check unavailable, rejecting password (fail-closed): {}", ex.getMessage());
            throw new WeakPasswordException("Проверка надёжности пароля временно недоступна. Попробуйте позже.");
        }
    }

    private String sha1UpperHex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02X", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 unavailable", ex);
        }
    }

    private Set<String> loadBlacklist() {
        Set<String> result = new HashSet<>();
        ClassPathResource resource = new ClassPathResource(BLACKLIST_RESOURCE);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim().toLowerCase();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    result.add(trimmed);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load common-passwords blacklist", ex);
        }
        log.info("Loaded {} entries into password blacklist", result.size());
        return result;
    }
}
