package com.pravoos.ai.model.enums;

import com.pravoos.ai.exception.WorkflowNotFoundException;

import java.util.Arrays;

public enum BankruptcyWorkflow {

    DEBTOR_SOLVENCY_ANALYSIS(
            "Анализ платёжеспособности должника",
            "Проанализируй финансовое состояние и признаки неплатёжеспособности должника по материалам дела. "
                    + "Определи наличие признаков банкротства согласно ст. 3, ст. 213.3 Закона N 127-ФЗ, "
                    + "оцени достаточность имущества для расчётов с кредиторами."),

    CHALLENGE_TRANSACTIONS(
            "Оспаривание сделок должника",
            "Выяви в материалах дела сделки, которые могут быть оспорены как подозрительные "
                    + "(ст. 61.2 Закона N 127-ФЗ) или влекущие предпочтение (ст. 61.3 Закона N 127-ФЗ). "
                    + "Для каждой сделки укажи основание оспаривания, период подозрительности и необходимые доказательства."),

    CREDITOR_CLAIMS(
            "Включение требований в реестр кредиторов",
            "Оцени обоснованность требований кредитора для включения в реестр требований кредиторов. "
                    + "Проверь соблюдение сроков (ст. 100, ст. 142 Закона N 127-ФЗ), состав и подтверждающие документы, "
                    + "определи очерёдность удовлетворения требования."),

    SUBSIDIARY_LIABILITY(
            "Субсидиарная ответственность КДЛ",
            "Проанализируй основания для привлечения контролирующих должника лиц к субсидиарной ответственности "
                    + "(глава III.2 Закона N 127-ФЗ). Определи круг КДЛ, наличие презумпций (ст. 61.11, ст. 61.12), "
                    + "причинно-следственную связь между действиями КДЛ и невозможностью погашения требований."),

    BANKRUPTCY_ESTATE(
            "Формирование конкурсной массы",
            "Определи состав конкурсной массы должника по материалам дела (ст. 131 Закона N 127-ФЗ). "
                    + "Выяви имущество, подлежащее включению и исключению, оцени перспективы пополнения массы "
                    + "за счёт оспаривания сделок и взыскания дебиторской задолженности.");

    private final String displayName;
    private final String instruction;

    BankruptcyWorkflow(String displayName, String instruction) {
        this.displayName = displayName;
        this.instruction = instruction;
    }

    public String displayName() {
        return displayName;
    }

    public String instruction() {
        return instruction;
    }

    public static BankruptcyWorkflow fromId(String id) {
        return Arrays.stream(values())
                .filter(workflow -> workflow.name().equalsIgnoreCase(id))
                .findFirst()
                .orElseThrow(() -> new WorkflowNotFoundException(id));
    }
}
