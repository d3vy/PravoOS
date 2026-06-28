package com.pravoos.user.email;

import com.pravoos.user.config.ResendProperties;
import com.pravoos.user.util.EmailMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class ResendEmailClient {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final RestClient restClient;
    private final ResendProperties properties;

    public ResendEmailClient(ResendProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .requestFactory(requestFactory)
                .build();
    }

    public void sendVerificationEmail(String to, String verificationLink) {
        send(to, "Подтвердите вашу почту — PravoOS", buildHtml(
                "Подтвердите вашу электронную почту, чтобы завершить регистрацию.",
                "Подтвердить почту",
                verificationLink,
                "Ссылка действительна 24 часа. Если вы не регистрировались в PravoOS — проигнорируйте это письмо."
        ));
        log.info("Verification email sent to {}", EmailMasker.mask(to));
    }

    public void sendApprovalEmail(String to, String fullName, String loginLink) {
        String greeting = (fullName == null || fullName.isBlank())
                ? "Ваша заявка одобрена."
                : fullName + ", ваша заявка одобрена.";
        send(to, "Заявка одобрена — PravoOS", buildHtml(
                greeting + " Доступ к системе предоставлен — войдите в личный кабинет, используя email и пароль из заявки.",
                "Перейти в личный кабинет",
                loginLink,
                "Если кнопка не работает, откройте адрес в браузере: " + loginLink
        ));
        log.info("Approval email sent to {}", EmailMasker.mask(to));
    }

    public void sendDeadlineEmail(String to, String caseTitle, String deadlineTypeName,
                                  String deadlineDate, int daysLeft, String caseLink) {
        String bodyText = String.format(
                "Напоминание по делу «%s»: %s — %s. Осталось дней: %d.",
                caseTitle, deadlineTypeName, deadlineDate, daysLeft);
        send(to, "Напоминание о дедлайне — PravoOS", buildHtml(
                bodyText,
                "Открыть дело",
                caseLink,
                "Вы получаете это письмо, так как Telegram-уведомления не подключены. Подключить можно в профиле PravoOS."
        ));
        log.info("Deadline email sent to {}", EmailMasker.mask(to));
    }

    public void sendPasswordResetEmail(String to, String resetLink) {
        send(to, "Сброс пароля — PravoOS", buildHtml(
                "Мы получили запрос на сброс пароля. Нажмите кнопку ниже, чтобы задать новый пароль.",
                "Сбросить пароль",
                resetLink,
                "Если вы не запрашивали сброс пароля — проигнорируйте это письмо, ваш пароль останется прежним."
        ));
        log.info("Password reset email sent to {}", EmailMasker.mask(to));
    }

    private void send(String to, String subject, String html) {
        Map<String, Object> body = Map.of(
                "from", properties.from(),
                "to", List.of(to),
                "subject", subject,
                "html", html
        );

        restClient.post()
                .uri("/emails")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private String buildHtml(String bodyText, String buttonText, String link, String footnote) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Inter, Arial, sans-serif; color: #09090b; max-width: 600px; margin: 0 auto; padding: 40px 20px;">
                  <h2 style="font-weight: 900; letter-spacing: -0.5px; margin-bottom: 8px;">PravoOS</h2>
                  <p style="font-size: 16px; margin-bottom: 24px;">%s</p>
                  <a href="%s"
                     style="display: inline-block; background: #09090b; color: #ffffff; padding: 12px 24px; text-decoration: none; font-weight: 600; font-size: 14px;">
                    %s
                  </a>
                  <p style="margin-top: 24px; font-size: 13px; color: #71717a;">%s</p>
                </body>
                </html>
                """.formatted(bodyText, link, buttonText, footnote);
    }
}
