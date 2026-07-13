package com.pravoos.user.billing.internal.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.pravoos.user.billing.internal.config.YooKassaProperties;
import com.pravoos.user.shared.exception.PravoosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Component
public class YooKassaClient {

    private static final Logger log = LoggerFactory.getLogger(YooKassaClient.class);
    private static final String BASE_URL = "https://api.yookassa.ru/v3";
    private static final String IDEMPOTENCE_HEADER = "Idempotence-Key";
    private static final String CURRENCY = "RUB";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
    private static final BigDecimal KOPECKS_IN_RUBLE = BigDecimal.valueOf(100);

    private final RestClient restClient;
    private final YooKassaProperties properties;

    public YooKassaClient(YooKassaProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    public YooKassaPayment createPayment(UUID userId, String planCode, long amountKopecks,
                                         String description, String idempotenceKey) {
        requireConfigured();
        Map<String, Object> body = Map.of(
                "amount", Map.of("value", formatAmount(amountKopecks), "currency", CURRENCY),
                "capture", true,
                "confirmation", Map.of("type", "redirect", "return_url", properties.returnUrl()),
                "description", description,
                "metadata", Map.of("userId", userId.toString(), "planCode", planCode));

        JsonNode response = exchange(() -> restClient.post()
                .uri("/payments")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader())
                .header(IDEMPOTENCE_HEADER, idempotenceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));

        YooKassaPayment payment = toPayment(response);
        log.info("YooKassa payment {} created for user {} on plan {} ({} kopecks)",
                payment.id(), userId, planCode, amountKopecks);
        return payment;
    }

    public YooKassaPayment getPayment(String providerPaymentId) {
        requireConfigured();
        return toPayment(exchange(() -> restClient.get()
                .uri("/payments/{id}", providerPaymentId)
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader())
                .retrieve()
                .body(JsonNode.class)));
    }

    private JsonNode exchange(PaymentCall call) {
        try {
            JsonNode response = call.execute();
            if (response == null) {
                throw providerUnavailable();
            }
            return response;
        } catch (RestClientException ex) {
            log.error("YooKassa API call failed", ex);
            throw providerUnavailable();
        }
    }

    private YooKassaPayment toPayment(JsonNode node) {
        String id = node.path("id").asText(null);
        if (id == null) {
            throw providerUnavailable();
        }
        return new YooKassaPayment(
                id,
                node.path("status").asText(""),
                node.path("paid").asBoolean(false),
                toKopecks(node.path("amount").path("value").asText("0")),
                node.path("confirmation").path("confirmation_url").asText(null));
    }

    private long toKopecks(String amountValue) {
        try {
            return new BigDecimal(amountValue).multiply(KOPECKS_IN_RUBLE)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            log.error("YooKassa returned unparseable amount: {}", amountValue, ex);
            throw providerUnavailable();
        }
    }

    private String formatAmount(long amountKopecks) {
        return BigDecimal.valueOf(amountKopecks)
                .divide(KOPECKS_IN_RUBLE, 2, RoundingMode.UNNECESSARY)
                .toPlainString();
    }

    private String basicAuthHeader() {
        String credentials = properties.shopId() + ":" + properties.secretKey();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new PravoosException("Приём платежей не настроен", HttpStatus.SERVICE_UNAVAILABLE,
                    "BILLING_NOT_CONFIGURED");
        }
    }

    private PravoosException providerUnavailable() {
        return new PravoosException("Платёжный провайдер недоступен", HttpStatus.BAD_GATEWAY,
                "PAYMENT_PROVIDER_UNAVAILABLE");
    }

    @FunctionalInterface
    private interface PaymentCall {
        JsonNode execute();
    }
}
