package com.pravoos.ai.practice.internal.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import com.pravoos.ai.shared.util.ClientNameMatch;
import com.pravoos.ai.shared.util.LikePattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class InvoiceRepositoryIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final UUID lawyerId = UUID.randomUUID();

  private UUID client() {
    UUID clientId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO clients (id, lawyer_id, name, type) VALUES (?, ?, ?, 'INDIVIDUAL')",
        clientId,
        lawyerId,
        "client-" + clientId);
    return clientId;
  }

  private Invoice invoice(String number, UUID clientId) {
    Invoice invoice = new Invoice();
    invoice.setLawyerId(lawyerId);
    invoice.setClientId(clientId);
    invoice.setNumber(number);
    invoice.setStatus(InvoiceStatus.DRAFT);
    invoice.setCurrency("RUB");
    invoice.setSubtotal(BigDecimal.TEN);
    invoice.setTotal(BigDecimal.TEN);
    invoice.setIssueDate(LocalDate.of(2026, 8, 8));
    invoice.setDueDate(LocalDate.of(2026, 8, 18));
    return invoiceRepository.save(invoice);
  }

  @Test
  void searchMatchesByNumberOrByClientAndRespectsThePageSize() {
    UUID matchingClient = client();
    Invoice byNumber = invoice("INV-ROMASHKA-1", client());
    Invoice byClient = invoice("INV-0002", matchingClient);
    invoice("INV-0003", client());

    List<Invoice> found =
        invoiceRepository.search(
            lawyerId,
            LikePattern.contains("romashka"),
            List.of(matchingClient),
            PageRequest.of(0, 10));

    assertThat(found)
        .extracting(Invoice::getId)
        .containsExactlyInAnyOrder(byNumber.getId(), byClient.getId());

    assertThat(
            invoiceRepository.search(
                lawyerId,
                LikePattern.contains("inv-"),
                List.of(ClientNameMatch.noMatchSentinel()),
                PageRequest.of(0, 2)))
        .hasSize(2);
  }

  @Test
  void searchEscapesWildcardsInTheQuery() {
    invoice("INV-0001", client());

    assertThat(
            invoiceRepository.search(
                lawyerId,
                LikePattern.contains("%"),
                List.of(ClientNameMatch.noMatchSentinel()),
                PageRequest.of(0, 10)))
        .isEmpty();
  }
}
