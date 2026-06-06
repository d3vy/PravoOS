package com.pravoos.user.email;

import com.pravoos.user.config.ResendProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class ResendEmailClient {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailClient.class);

    private final RestClient restClient;
    private final ResendProperties properties;

    public ResendEmailClient(ResendProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .build();
    }

    public void sendVerificationEmail(String to, String verificationLink) {
        Map<String, Object> body = Map.of(
                "from", properties.from(),
                "to", List.of(to),
                "subject", "Подтвердите вашу почту — PravoOS",
                "html", buildHtml(verificationLink)
        );

        restClient.post()
                .uri("/emails")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        log.info("Verification email sent to {}", to);
    }

    private String buildHtml(String verificationLink) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Inter, Arial, sans-serif; color: #09090b; max-width: 600px; margin: 0 auto; padding: 40px 20px;">
                  <h2 style="font-weight: 900; letter-spacing: -0.5px; margin-bottom: 8px;">PravoOS</h2>
                  <p style="font-size: 16px; margin-bottom: 24px;">Подтвердите вашу электронную почту, чтобы завершить регистрацию.</p>
                  <a href="%s"
                     style="display: inline-block; background: #09090b; color: #ffffff; padding: 12px 24px; text-decoration: none; font-weight: 600; font-size: 14px;">
                    Подтвердить почту
                  </a>
                  <p style="margin-top: 24px; font-size: 13px; color: #71717a;">
                    Ссылка действительна 24 часа. Если вы не регистрировались в PravoOS — проигнорируйте это письмо.
                  </p>
                </body>
                </html>
                """.formatted(verificationLink);
    }
}
