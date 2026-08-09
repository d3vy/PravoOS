package com.pravoos.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.user.billing.internal.model.entity.Payment;
import com.pravoos.user.billing.internal.model.enums.PaymentStatus;
import com.pravoos.user.billing.internal.repository.PaymentRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PaymentRepositoryIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Autowired private PaymentRepository paymentRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void locksThePaymentRowByItsProviderId() {
    Payment saved = paymentRepository.saveAndFlush(payment("yk-lock-1"));

    assertThat(paymentRepository.findByProviderPaymentIdForUpdate("yk-lock-1"))
        .get()
        .extracting(Payment::getId)
        .isEqualTo(saved.getId());
  }

  @Test
  void returnsEmptyForAnUnknownProviderId() {
    assertThat(paymentRepository.findByProviderPaymentIdForUpdate("yk-missing")).isEmpty();
  }

  private Payment payment(String providerPaymentId) {
    Payment payment = new Payment();
    payment.setUserId(insertUser());
    payment.setPlanId(insertPlan());
    payment.setProviderPaymentId(providerPaymentId);
    payment.setAmountKopecks(499_00L);
    payment.setStatus(PaymentStatus.PENDING);
    return payment;
  }

  private UUID insertUser() {
    UUID userId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, password_hash, role, status) VALUES (?, ?, 'hash', "
            + "'LAWYER', 'ACTIVE')",
        userId,
        userId + "@example.com");
    return userId;
  }

  private UUID insertPlan() {
    UUID planId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO plans (id, code, name, price_kopecks, daily_requests, daily_tokens, seats, "
            + "created_at) VALUES (?, ?, 'Plan', 49900, 100, 100000, 1, NOW())",
        planId,
        planId.toString().substring(0, 20));
    return planId;
  }
}
