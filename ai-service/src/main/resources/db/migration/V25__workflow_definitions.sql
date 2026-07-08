CREATE TABLE workflow_definitions
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    org_id      UUID,
    created_by  UUID,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    category    VARCHAR(40)  NOT NULL,
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    steps       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_workflow_definitions_created_by ON workflow_definitions (created_by);
CREATE INDEX idx_workflow_definitions_org ON workflow_definitions (org_id);
CREATE INDEX idx_workflow_definitions_system ON workflow_definitions (is_system);

CREATE TABLE workflow_runs
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    case_id         UUID         NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    definition_id   UUID         NOT NULL,
    definition_name VARCHAR(200) NOT NULL,
    category        VARCHAR(40)  NOT NULL,
    lawyer_id       UUID         NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    steps           JSONB        NOT NULL DEFAULT '[]'::jsonb,
    started_at      TIMESTAMP    NOT NULL DEFAULT now(),
    finished_at     TIMESTAMP
);

CREATE INDEX idx_workflow_runs_case ON workflow_runs (case_id, started_at DESC);

INSERT INTO workflow_definitions (name, description, category, is_system, steps)
VALUES ('Банкротство физического лица',
        'Типовой процесс сопровождения банкротства: анализ, документы, заявление, дедлайн и карта рисков.',
        'BANKRUPTCY', TRUE,
        '[
          {"order":0,"type":"AI_ANALYSIS","title":"Анализ платёжеспособности должника",
           "instruction":"Проанализируй финансовое состояние и признаки неплатёжеспособности должника по материалам дела. Определи наличие признаков банкротства согласно ст. 3 и ст. 213.3 Закона N 127-ФЗ, оцени достаточность имущества для расчётов с кредиторами."},
          {"order":1,"type":"GENERATE_TASKS","title":"Чеклист документов",
           "instruction":"Проверь полноту пакета документов по делу о банкротстве. Для каждого обязательного типа документов укажи статус: присутствует / отсутствует / присутствует частично. Результат представь в виде markdown-таблицы: Документ | Статус | Примечание."},
          {"order":2,"type":"GENERATE_DRAFT","title":"Заявление о признании банкротом","draftType":"STATEMENT"},
          {"order":3,"type":"SET_DEADLINE","title":"Срок подготовки заявления","deadlineType":"FILING_DEADLINE","deadlineOffsetDays":14},
          {"order":4,"type":"AI_ANALYSIS","title":"Карта рисков процедуры",
           "instruction":"Определи и систематизируй правовые риски по делу о банкротстве. Для каждого риска укажи наименование, правовое обоснование, степень вероятности и рекомендуемые действия. Результат представь в виде markdown-таблицы: Риск | Обоснование | Степень | Рекомендация."}
        ]'::jsonb);

INSERT INTO workflow_definitions (name, description, category, is_system, steps)
VALUES ('Взыскание задолженности',
        'Процесс досудебного и судебного взыскания: анализ основания долга, документы, проект иска и срок подачи.',
        'DEBT_COLLECTION', TRUE,
        '[
          {"order":0,"type":"AI_ANALYSIS","title":"Анализ основания задолженности и доказательств",
           "instruction":"Проанализируй основание возникновения задолженности по материалам дела: договор, факт исполнения, размер долга, начисленные проценты и неустойку. Оцени достаточность доказательств и перспективы взыскания со ссылками на нормы ГК РФ."},
          {"order":1,"type":"GENERATE_TASKS","title":"Чеклист документов для взыскания",
           "instruction":"Проверь полноту пакета документов, необходимых для взыскания задолженности (договор, первичные документы, акт сверки, претензия, расчёт долга и процентов). Для каждого типа укажи статус: присутствует / отсутствует / присутствует частично. Результат представь в виде markdown-таблицы: Документ | Статус | Примечание."},
          {"order":2,"type":"AI_ANALYSIS","title":"Проект искового заявления о взыскании",
           "instruction":"Составь проект искового заявления о взыскании задолженности на основании материалов дела. Структура: наименование суда, стороны, цена иска, обстоятельства, правовое обоснование со ссылками на ГК РФ, расчёт требований, просительная часть, перечень приложений. Соблюдай требования ст. 125 АПК РФ."},
          {"order":3,"type":"SET_DEADLINE","title":"Срок подачи иска","deadlineType":"FILING_DEADLINE","deadlineOffsetDays":30}
        ]'::jsonb);

INSERT INTO workflow_definitions (name, description, category, is_system, steps)
VALUES ('Регистрация юридического лица',
        'Сопровождение регистрации: чеклист документов, проверка учредительных документов и проект заявления.',
        'REGISTRATION', TRUE,
        '[
          {"order":0,"type":"GENERATE_TASKS","title":"Чеклист документов для регистрации",
           "instruction":"Проверь полноту пакета документов для государственной регистрации юридического лица (устав, решение о создании, заявление по форме, документ об оплате пошлины, сведения об адресе). Для каждого типа укажи статус: присутствует / отсутствует / присутствует частично. Результат представь в виде markdown-таблицы: Документ | Статус | Примечание."},
          {"order":1,"type":"AI_ANALYSIS","title":"Проверка учредительных документов",
           "instruction":"Проверь учредительные документы на соответствие требованиям законодательства о государственной регистрации юридических лиц. Выяви несоответствия и риски отказа в регистрации, предложи корректировки со ссылками на нормы ГК РФ и Федерального закона N 129-ФЗ."},
          {"order":2,"type":"AI_ANALYSIS","title":"Проект заявления о регистрации",
           "instruction":"Составь проект заявления и сопроводительного письма для подачи документов на государственную регистрацию юридического лица на основании материалов дела. Укажи перечень прилагаемых документов."}
        ]'::jsonb);
