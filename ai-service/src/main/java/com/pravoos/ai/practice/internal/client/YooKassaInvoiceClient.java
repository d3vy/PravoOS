package com.pravoos.ai.practice.internal.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.pravoos.ai.shared.config.InvoicePaymentProperties;
import com.pravoos.ai.shared.exception.PravoosException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class YooKassaInvoiceClient {

  private static final Logger log = LoggerFactory.getLogger(YooKassaInvoiceClient.class);
  private static final String BASE_URL = "https://api.yookassa.ru/v3";
  private static final String IDEMPOTENCE_HEADER = "Idempotence-Key";
  private static final String CURRENCY = "RUB";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
  private static final BigDecimal KOPECKS_IN_RUBLE = BigDecimal.valueOf(100);

  private final RestClient restClient;
  private final InvoicePaymentProperties properties;

  public YooKassaInvoiceClient(InvoicePaymentProperties properties) {
    this.properties = properties;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
    requestFactory.setReadTimeout(READ_TIMEOUT);
    this.restClient = RestClient.builder().baseUrl(BASE_URL).requestFactory(requestFactory).build();
  }

  public YooKassaInvoicePayment createPayment(
      UUID invoiceId, long amountKopecks, String description, String idempotenceKey) {
    requireConfigured();
    Map<String, Object> body =
        Map.of(
            "amount", Map.of("value", formatAmount(amountKopecks), "currency", CURRENCY),
            "capture", true,
            "confirmation", Map.of("type", "redirect", "return_url", properties.returnUrl()),
            "description", description,
            "metadata", Map.of("invoiceId", invoiceId.toString()));

    JsonNode response =
        exchange(
            () ->
                restClient
                    .post()
                    .uri("/payments")
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader())
                    .header(IDEMPOTENCE_HEADER, idempotenceKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class));

    YooKassaInvoicePayment payment = toPayment(response);
    log.info(
        "YooKassa payment {} created for invoice {} ({} kopecks)",
        payment.id(),
        invoiceId,
        amountKopecks);
    return payment;
  }

  public YooKassaInvoicePayment getPayment(String providerPaymentId) {
    requireConfigured();
    return toPayment(
        exchange(
            () ->
                restClient
                    .get()
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

  private YooKassaInvoicePayment toPayment(JsonNode node) {
    String id = node.path("id").asText(null);
    if (id == null) {
      throw providerUnavailable();
    }
    requireExpectedCurrency(id, node.path("amount").path("currency").asText(""));
    return new YooKassaInvoicePayment(
        id,
        node.path("status").asText(""),
        node.path("paid").asBoolean(false),
        toKopecks(node.path("amount").path("value").asText("0")),
        node.path("confirmation").path("confirmation_url").asText(null));
  }

  private void requireExpectedCurrency(String paymentId, String currency) {
    if (!CURRENCY.equals(currency)) {
      log.error(
          "YooKassa payment {} is denominated in '{}' instead of {} — refusing to compare amounts",
          paymentId,
          currency,
          CURRENCY);
      throw providerUnavailable();
    }
  }

  private long toKopecks(String amountValue) {
    try {
      return new BigDecimal(amountValue)
          .multiply(KOPECKS_IN_RUBLE)
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
    return "Basic "
        + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new PravoosException(
          "Приём платежей не настроен", HttpStatus.SERVICE_UNAVAILABLE, "BILLING_NOT_CONFIGURED");
    }
  }

  private PravoosException providerUnavailable() {
    return new PravoosException(
        "Платёжный провайдер недоступен", HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_UNAVAILABLE");
  }

  @FunctionalInterface
  private interface PaymentCall {
    JsonNode execute();
  }
}
