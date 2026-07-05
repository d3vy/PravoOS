package com.pravoos.ai.util;

import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.Client;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TemplatePlaceholderResolverTest {

    @Test
    void replacesCaseAndClientPlaceholders() {
        Case caseEntity = new Case();
        caseEntity.setTitle("Иск о взыскании");
        caseEntity.setFilingDeadline(LocalDate.of(2026, 7, 1));

        Client client = new Client();
        client.setName("ООО Ромашка");
        client.setInn("7701234567");

        String result = TemplatePlaceholderResolver.resolve(
                "Дело: {{case_title}}, клиент: {{client_name}} (ИНН {{client_inn}}), срок: {{filing_deadline}}",
                caseEntity, client);

        assertThat(result)
                .isEqualTo("Дело: Иск о взыскании, клиент: ООО Ромашка (ИНН 7701234567), срок: 01.07.2026");
    }

    @Test
    void missingDataResolvesToEmptyString() {
        Case caseEntity = new Case();
        caseEntity.setTitle("Дело");

        String result = TemplatePlaceholderResolver.resolve(
                "[{{client_name}}][{{filing_deadline}}][{{case_description}}]", caseEntity, null);

        assertThat(result).isEqualTo("[][][]");
    }

    @Test
    void unknownPlaceholderIsLeftUntouched() {
        Case caseEntity = new Case();
        caseEntity.setTitle("Дело");

        String result = TemplatePlaceholderResolver.resolve("{{case_title}} {{case_number}}", caseEntity, null);

        assertThat(result).isEqualTo("Дело {{case_number}}");
    }

    @Test
    void todayIsResolvedToFormattedDate() {
        String result = TemplatePlaceholderResolver.resolve("{{today}}", new Case(), null);

        assertThat(result).matches("\\d{2}\\.\\d{2}\\.\\d{4}");
    }
}
