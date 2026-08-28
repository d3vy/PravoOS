package com.pravoos.ai.practice.internal.agent;

import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.ADMIN;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.LAWYER;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.LAWYER_ID;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.MAPPER;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.VALIDATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pravoos.ai.core.api.AiActionProposals;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.core.api.ProposedAction;
import com.pravoos.ai.practice.internal.dto.CreateInvoiceRequest;
import com.pravoos.ai.practice.internal.dto.InvoiceResponse;
import com.pravoos.ai.practice.internal.service.InvoiceService;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateInvoiceToolTest {

  private static final UUID CLIENT_ID = UUID.randomUUID();
  private static final UUID CASE_ID = UUID.randomUUID();

  @Mock private AiActionProposals proposals;
  @Mock private InvoiceService invoiceService;

  private CreateInvoiceTool tool() {
    return new CreateInvoiceTool(proposals, VALIDATOR, invoiceService, MAPPER);
  }

  @Test
  void isAWriteToolOfferedToLawyersOnly() {
    assertThat(tool().readOnly()).isFalse();
    assertThat(tool().requiresApproval()).isTrue();
    assertThat(tool().availableFor(LAWYER)).isTrue();
    assertThat(tool().availableFor(ADMIN)).isFalse();
  }

  @Test
  void executeOnlyProposesAndNeverIssuesTheInvoice() {
    when(proposals.propose(any(), any(), any(), any()))
        .thenReturn(
            new ProposedAction(
                UUID.randomUUID(), "create_invoice", "t", LocalDateTime.now().plusMinutes(30)));

    AiToolResult result = tool().execute(arguments(), LAWYER);

    assertThat(result.ok()).isTrue();
    verify(invoiceService, never()).create(any(), any());
  }

  @Test
  void namesTheCaseInTheProposalTitleWhenTheInvoiceIsScopedToOne() {
    ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
    when(proposals.propose(any(), any(), title.capture(), any()))
        .thenReturn(
            new ProposedAction(UUID.randomUUID(), "create_invoice", "t", LocalDateTime.now()));

    tool().execute(arguments().put("caseId", CASE_ID.toString()), LAWYER);

    assertThat(title.getValue()).contains(CASE_ID.toString());
  }

  @Test
  void rejectsAMissingClientBeforeCreatingAProposal() {
    AiToolResult result = tool().execute(MAPPER.createObjectNode(), LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("clientId");
    verify(proposals, never()).propose(any(), any(), any(), any());
  }

  @Test
  void rejectsAVatRateOutsideThePercentageRangeBeforeCreatingAProposal() {
    AiToolResult result = tool().execute(arguments().put("vatRate", 120), LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("vatRate");
    verify(proposals, never()).propose(any(), any(), any(), any());
  }

  @Test
  void rejectsAVatRateThatIsNotANumberBeforeCreatingAProposal() {
    AiToolResult result = tool().execute(arguments().put("vatRate", "двадцать"), LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("vatRate");
    verify(proposals, never()).propose(any(), any(), any(), any());
  }

  @Test
  void performIssuesTheInvoiceWithoutPickingTimeEntriesItself() {
    when(invoiceService.create(any(), eq(LAWYER_ID)))
        .thenReturn(
            new InvoiceResponse(
                UUID.randomUUID(),
                CLIENT_ID,
                "Иванов",
                "INV-1",
                InvoiceStatus.DRAFT,
                "Черновик",
                LocalDate.of(2026, 8, 27),
                LocalDate.of(2026, 9, 10),
                "RUB",
                BigDecimal.TEN,
                BigDecimal.valueOf(20),
                BigDecimal.TWO,
                BigDecimal.valueOf(12),
                null,
                List.of(),
                LocalDateTime.now()));

    AiToolResult result = tool().perform(arguments().put("vatRate", 20), LAWYER);

    assertThat(result.ok()).isTrue();
    assertThat(result.content()).contains("INV-1").contains("Черновик");
    ArgumentCaptor<CreateInvoiceRequest> request =
        ArgumentCaptor.forClass(CreateInvoiceRequest.class);
    verify(invoiceService).create(request.capture(), eq(LAWYER_ID));
    assertThat(request.getValue().clientId()).isEqualTo(CLIENT_ID);
    assertThat(request.getValue().timeEntryIds()).isNull();
  }

  @Test
  void schemaRequiresOnlyTheClientAndForbidsUnknownFields() {
    JsonNode schema = tool().parameters();

    assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.get("required")).extracting(JsonNode::asText).containsExactly("clientId");
    assertThat(schema.get("properties").has("timeEntryIds")).isFalse();
  }

  private static ObjectNode arguments() {
    return MAPPER.createObjectNode().put("clientId", CLIENT_ID.toString());
  }
}
