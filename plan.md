# PravoOS — план: 10 нововведений и критических исправлений

> Составлено на основе обзора кодовой базы (backend: user-service, ai-service; frontend React)
> и анализа конкурентов: Casebook/Caselook (ПравоТех), XSUD, Jeffit, Юрайт: Legal AI,
> Искра (Гарант), Нейроюрист (Яндекс), GigaLegal, а также мировых Harvey AI и Spellbook.
>
> Легенда приоритета: 🔴 критично · 🟡 важно · 🟢 стратегия
> Оценка: S (≤3 дня) · M (до 2 недель) · L (2+ недели)

---

## Критические исправления

### 1. ✅ Стриминг ответов AI (SSE) — сейчас его нет
**Проблема.** `ChatService.chat()` возвращает ответ синхронно, целиком. На запросе с RAG-контекстом
и историей юрист ждёт 5–20 сек пустой экран. Все конкуренты (Harvey, Spellbook, Нейроюрист, Искра)
отдают токены потоком.
**Что делать.**
- ai-service: добавить `POST /api/ai/chat/stream` → `SseEmitter` / `Flux<String>`, прокинуть стрим
  из `OpenAiLlmClient` (OpenAI SSE `stream:true`).
- api-gateway: не буферизовать `text/event-stream`.
- frontend: `ChatPage` — читать поток через `fetch` + `ReadableStream`, дописывать сообщение по токенам.
- Сохранять итоговое сообщение и `sources[]` в Mongo только по завершении стрима.
**Приоритет / оценка:** 🔴 / M

### 2. ✅ RAG отвечает только по загруженным документам — нет актуального законодательства
**Проблема.** База знаний = только файлы, залитые админом. Нет связи с действующими редакциями
кодексов/законов. Риск галлюцинаций и устаревших норм — недопустимо для юр-продукта.
Главная фича конкурентов («Правовой поиск» BotHub, Искра+Гарант, GigaLegal) — ответ строго по
актуальной правовой базе со ссылкой на норму.
**Что делать.**
- Ввести источник «Законодательство» с версионированием (дата редакции) в `document`-модуле.
- Пайплайн загрузки/обновления НПА (Кодексы, ФЗ) с пометкой актуальной редакции; сначала —
  ручной/полуавтоматический импорт, далее коннектор.
- `RagService`: приоритет актуальной редакции, отказ отвечать при отсутствии релевантной нормы
  (усилить существующий `LegalDomainGuard` + `CitationCheckService`).
- В ответе — обязательная ссылка «ст. N, редакция от ДД.ММ.ГГГГ».
**Приоритет / оценка:** 🔴 / L

**✅ Сделано (2026-07-07).**
- Модель: `Document` расширен (не отдельная сущность — переиспользован RAG-пайплайн). V23:
  `document_kind`/`act_canonical`/`article_number`/`edition_date`/`superseded`, partial-UNIQUE на
  актуальную редакцию `(акт, статья)`. Enum `DocumentKind`.
- Загрузка: `DocumentService.uploadLegislation` (новая редакция статьи → старая `superseded`),
  `POST/GET /api/ai/documents/legislation` (ADMIN) + фронт-секция «Законодательство» на `DocumentsPage`.
- RAG: `VectorSearchRepository` исключает `superseded`, мягкий приоритет НПА (`legislation-boost=0.05`);
  метаданные нормы протянуты в `ChunkMatch`/`RetrievedChunk`. `RagService` — обязательная ссылка
  «ст. N <Акт>, ред. от ДД.ММ.ГГГГ» + предупреждение при отсутствии актуальной нормы. Источники в
  ответе для НПА показывают акт+статью+дату.
- Цитаты: `CitationCheckService.checkStatute` — VERIFIED только при наличии актуальной редакции
  (`DocumentAccess.currentLegislation`), упоминание в тексте → UNVERIFIED (fallback). `LegislationRef`.
- Тесты зелёные (91), `ModularityTests` целы; `CitationCheckServiceTest` обновлён под строгую семантику.
- **Follow-up:** авто-коннектор обновления НПА (пока ручной импорт); статус цитаты OUTDATED (маплю в
  UNVERIFIED, чтобы не трогать enum+фронт).

### 3. ✅ Документация проекта устарела и вводит в заблуждение
**Проблема.** `ARCHITECTURE.md`/`README.md` описывают маленькое RAG-приложение (только `/chat`,
эмбеддинги «via DeepSeek»), тогда как реально это полноценный практис-менеджмент: дела, клиенты,
клиентский портал, шаблоны, генерация черновиков, интеграция kad.arbitr, дедлайны, организации,
MFA, шифрование. Эмбеддинги в коде — OpenAI, а не DeepSeek. Онбординг и поддержка страдают.
**Что делать.**
- Переписать `ARCHITECTURE.md` под фактические модули (modulith: identity/registration/collaboration
  в user-service; core/document/llm/practice/shared в ai-service).
- Обновить список маршрутов фронта и таблицу стека (OpenAI, а не DeepSeek).
- Зафиксировать карту Kafka-топиков (deadline/hearing/case-message/lawyer-deleted), а не только
  `application.submitted`.
**Приоритет / оценка:** 🔴 / S

**✅ Сделано.** `README.md` переписан под фактическую архитектуру (modulith-модули, реальный стек
с OpenAI вместо DeepSeek, полная карта Kafka-топиков, все сервисы/фичи/безопасность).

---

## Нововведения от конкурентов

### 4. 🟡 Учёт времени и биллинг (полностью отсутствует)
**Возможность.** У XSUD, Jeffit, Harvey учёт времени по делу и выставление счетов — базовая функция
монетизации юрфирмы. У нас 0 упоминаний billing/timesheet в коде.
**Что делать.** В `practice`-модуле: сущности `TimeEntry` (case_id, lawyer_id, minutes, rate, description,
billable) и `Invoice`; таймер/ручной ввод в `CaseDetailPage`; отчёт по делу/клиенту/юристу; экспорт счёта
в PDF (переиспользовать `CasePdfWriter`).
**Приоритет / оценка:** 🟡 / L

### 5. 🟡 Судебная аналитика поверх kad.arbitr
**Возможность.** Casebook: краткое содержание дела, анализ аргументов сторон, стратегия, статистика.
У нас `ArbitrSyncService`/`ArbitrPollingService` уже тянут данные дел — база для аналитики есть.
**Что делать.** По номеру дела: AI-саммари истории, статистика по судье/суду/оппоненту (доля
удовлетворённых исков, средние сроки), подсказки по стратегии. Новый эндпоинт в `practice`
+ виджет в `CaseDetailPage`. Использовать существующий `LegalAiPort`.
**Приоритет / оценка:** 🟡 / L

### 6. ✅ Сравнение версий и редлайн документов
**Возможность.** Spellbook «Compare to Market», Юрайт: Legal AI (распознавание и сравнение).
У нас есть проверка договора (`ContractReviewService`), но нет диффа версий.
**Что делать.** Загрузка двух версий → семантический + текстовый дифф с подсветкой изменений и
AI-комментарием рисков по каждому изменению. Расширить `ContractReviewService`, добавить redline-view
на фронте.
**Приоритет / оценка:** 🟡 / M

**✅ Сделано (2026-07-08).**
- Бэкенд (core-модуль, рядом с `ContractReview`): новая сущность `DocumentComparison` (V24
  `document_comparisons`) + `DocumentComparisonService`. `TextDiffService` — детерминированный
  абзацный дифф (LCS, без внешних либ) → блоки изменений `DiffChange{order,type(ADDED/REMOVED/MODIFIED),
  baseText,revisedText,riskLevel,comment}`. Блоки нумеруются → `DocumentComparisonPrompt` (анти-инъекция,
  fenced) → LLM оценивает риск каждой правки (JSON `{summary,riskScore,changes:[{index,level,comment}]}`),
  ассессменты мёржатся по индексу. Идентичные версии → без вызова LLM (summary «тексты идентичны»).
- Доступ: обе версии через `DocumentAccess.findForReview` — оба `caseId != null`, один и тот же кейс,
  видимость через `CaseAccessProvider.assertCaseVisible`; квота `LlmQuotaService`. Эндпоинты
  `POST/GET /api/ai/document-comparisons` (+`/{id}`), security-matcher `/api/ai/document-comparisons/**`=LAWYER.
  Конфиг `document-comparison.*` (maxInputChars/maxChanges/changeMaxChars).
- Фронт: секция «Сравнение версий (редлайн)» на `CaseDetailPage` (два дропдауна БЫЛО/СТАЛО,
  карточки-редлайн: было=красный strikethrough / стало=зелёный + бейдж риска + AI-комментарий).
  `api/documentComparisons.ts`, типы `DiffChange/DocumentComparisonDto`.
- Тесты: `TextDiffServiceTest` (6), `ModularityTests`+`ContractReviewServiceTest` зелёные,
  `tsc --noEmit` чист. Без изменений границ modulith (всё в core, deps уже разрешены).

### 7. ✅ Единый календарь заседаний и дедлайнов
**Возможность.** Календарь судебных заседаний — витринная фича XSUD. У нас есть `CaseHearingEvent`,
`CaseDeadlineReminder`, `DeadlineReminderService`, но нет календарного представления во фронте
(в маршрутах его нет).
**Что делать.** Страница `/calendar`: месяц/неделя, заседания + дедлайны + задачи из `CaseTask`,
фильтр по делу/клиенту, экспорт в iCal. Данные уже есть — нужен агрегирующий эндпоинт и UI.
**Приоритет / оценка:** 🟡 / M

**✅ Сделано (2026-07-07).**
- Бэкенд: `CalendarService` агрегирует 3 источника событий по видимым делам (lawyerId OR org):
  дедлайны из полей `Case` (filingDeadline/nextHearingDate/expiresAt → `DeadlineType`), заседания
  из `CaseHearingEvent` (КАД), незакрытые задачи из `CaseTask`. `GET /api/ai/calendar?from&to&caseId&clientId`
  + `GET /api/ai/calendar/export.ics` (iCal all-day VEVENT). Enum `CalendarEventType`, DTO `CalendarEventResponse`,
  util `ICalendarWriter`. Repo: `findVisibleForCalendar`, `findByCaseIdInAndEventDateBetween`,
  `findByCaseIdInAndDoneFalseAndDueDateBetween`. Security-matcher `/api/ai/calendar/**` = LAWYER.
- Фронт: страница `/calendar` (месяц/неделя, навигация, фильтр клиент/дело, легенда, экспорт iCal),
  `api/calendar.ts`, тип `CalendarEvent`, пункт «Календарь» в навбаре. Клик по событию → карточка дела.
- ModularityTests зелёные, ai-service компилируется, `tsc --noEmit` чист.

### 8. ✅ Настраиваемые AI-workflow (агенты) для типовых процессов
**Возможность.** Harvey — 25k кастомных агентов. У нас есть заготовка: `WorkflowService` +
`BankruptcyWorkflow` (пока один зашитый сценарий).
**Что делать.** Обобщить в конструктор процессов: шаги (сбор документов → черновики → проверка →
дедлайны) как конфигурация, а не хардкод-enum. Библиотека готовых процессов (банкротство, взыскание,
регистрация). Переиспользовать `DraftService`, `TemplateService`, `DeadlineReminderService`.
**Приоритет / оценка:** 🟢 / L

**✅ Сделано (2026-07-08).**
- Модель: гибрид seed+кастом в БД (V25 `workflow_definitions`/`workflow_runs`, оба JSONB). Определение =
  `WorkflowDefinition{category, is_system, steps[]}`, шаг = `WorkflowStepConfig{order,type,title,
  instruction/draftType/deadlineType+offset}`. Enum `WorkflowStepType`(AI_ANALYSIS/GENERATE_DRAFT/
  GENERATE_TASKS/SET_DEADLINE), `WorkflowCategory`, `WorkflowRunStatus`, `WorkflowStepStatus`.
  Существующий одношаговый `BankruptcyWorkflow`/`WorkflowService` не тронут (переиспользуется в
  `generateFromChecklist`).
- Движок: `WorkflowExecutionService` последовательно исполняет шаги, каждый результат линкуется к делу.
  Переиспользование: AI_ANALYSIS/GENERATE_TASKS → `LegalAiPort.runCaseWorkflow` (AiResponse),
  GENERATE_TASKS → `CaseTaskService.createFromChecklist` (вынесен из `generateFromChecklist`),
  GENERATE_DRAFT → `DraftService.generate`, SET_DEADLINE → `CaseService.setDeadlineIfAbsent`
  (не перезаписывает заданный дедлайн → подхватывает `DeadlineReminderService`). Промежуточный статус
  RUNNING коммитится; падение шага → FAILED + стоп, дальнейшие шаги остаются PENDING. Квота/видимость дела.
- CRUD: `WorkflowDefinitionService` (система read-only, кастом — личный по `createdBy`, валидация шагов).
  Эндпоинты `GET/POST/PUT/DELETE /api/ai/workflow-definitions` + `POST/GET
  /api/ai/cases/{id}/workflow-runs` (+`/{runId}`). Security-matcher `/api/ai/workflow-definitions/**`=LAWYER.
  Seed: банкротство, взыскание, регистрация.
- Фронт: страница `/workflows` (конструктор — список + редактор шагов с типами/reorder/удалением),
  пункт «Процессы» в навбаре; секция «Процессы (AI-workflow)» на `CaseDetailPage` (выбор процесса,
  запуск, история запусков с пошаговыми статусами). `api/workflows.ts`+`api/cases.ts`, типы в `types`.
- Тесты: `WorkflowExecutionServiceTest`(3)+`WorkflowDefinitionServiceTest`(5), всего 105 зелёных,
  `ModularityTests` целы (всё в practice, deps уже разрешены), `tsc --noEmit` чист.
- **Follow-up:** асинхронный запуск + прогресс по SSE (сейчас синхронно); шаринг кастом-процессов на
  организацию; drag-n-drop reorder вместо стрелок.

### 9. ✅ Онлайн-редактор черновиков в браузере
**Возможность.** Spellbook работает прямо в Word. У нас `DraftService`/`CaseDocxWriter` только
генерируют и экспортируют DOCX/PDF — править сгенерированный документ внутри платформы нельзя.
**Что делать.** Rich-text редактор на странице черновика: правка сгенерированного текста, инлайн
AI-подсказки («усилить пункт», «добавить условие»), сохранение версий, финальный экспорт.
**Приоритет / оценка:** 🟢 / M

**✅ Сделано (2026-07-08).**
- Бэкенд (practice-модуль): версионирование черновиков. V26 `draft_editing.sql` — `case_drafts.updated_at`
  + новая таблица `case_draft_versions{draft_id,version_no,content,note,created_by}` (уникальный индекс
  draft_id+version_no, каскад по FK). Сущность `CaseDraftVersion`, `@PreUpdate` на `CaseDraft`.
  `DraftService`: `getDraft`, `updateContent` (снимок новой версии + обновление контента), `listVersions`,
  `restoreVersion` (контент → выбранная версия + новый снимок, история append-only), `refine`. Каждая
  запись контента (генерация/правка/восстановление) снимается версией через `snapshotVersion`
  (`maxVersionNo`+1). Валидация лимитов через `DraftEditingProperties` (`draft-editing.max-content-chars`
  /`max-refine-chars`), исключение `DraftEditingException` (422).
- Инлайн-AI: `LegalAiPort.refineDraft(caseId,instruction,currentText,lawyerId)` (+`LegalAiPortImpl`)
  переиспользует RAG (`retrieveForCase`) + `DraftRefinePrompt` (анти-инъекция: текст-фрагмент фенсится
  как ДАННЫЕ, инструкция юриста — команда; возвращает только переработанный текст). Правка применяется
  к выделенному фрагменту (`selectedText`) или ко всему документу; квота `LlmQuotaService`. Refine не
  сохраняет — юрист принимает/отклоняет.
- Эндпоинты: `GET/PUT /api/ai/drafts/{id}`, `GET /api/ai/drafts/{id}/versions`,
  `POST /api/ai/drafts/{id}/versions/{versionId}/restore`, `POST /api/ai/drafts/{id}/refine`
  (все под уже существующим security-matcher `/api/ai/drafts/**`=LAWYER, `CaseService.requireVisibleCase`).
- Фронт: новая страница `/cases/:caseId/drafts/:draftId` (`DraftEditorPage`) — редактор (textarea),
  панель AI-помощника (пресеты «усилить/упростить/снизить риски/формальнее» + своя инструкция, работа с
  выделением, превью предложения → применить/отклонить), панель «История версий» (список + восстановление),
  сохранение версии с описанием, скачивание .docx. Кнопка «Редактировать» на карточке черновика в
  `CaseDetailPage`. `api/cases.ts` (+5 методов), типы `CaseDraftVersionDto/UpdateDraftRequest/
  RefineDraftRequest/RefineDraftResponse`, `CaseDraftDto.updatedAt`.
- Тесты: `DraftServiceTest` (6: снимок при генерации, обновление+снимок, лимит, восстановление, refine с
  выделением/без). `WorkflowExecutionServiceTest` обновлён под новый конструктор `CaseDraftDto`.
  `ModularityTests` целы, `tsc --noEmit` чист, `npm run build` ок.

### 10. ✅ Прозрачность качества AI-ответов (доверие)
**Возможность.** Для юр-продукта критично доверие к ответу. У нас уже есть `CitationCheckService`
и рейтинг (`RateRequest`/`RatingButtons`) — но в UI чата нет явной индикации проверенности цитат
и уровня уверенности.
**Что делать.** В ответе показывать статус каждой ссылки (проверена/не найдена — из `CitationStatus`),
предупреждение «ответ не основан на актуальной норме», сбор фидбэка. Свести метрики в `AiStatsPage`
(доля проверенных цитат, средний рейтинг, доля отказов `NonLegalQueryException`).
**Приоритет / оценка:** 🟢 / M

**✅ Сделано (2026-07-08).**
- Чат: под каждым ответом — «Проверить ссылки» (авто-запуск для свежесгенерированного ответа) через
  существующий `POST /api/ai/citation-checks`; статус каждой ссылки (VERIFIED/NOT_FOUND/UNVERIFIED) +
  сводка; жёлтый баннер-предупреждение, если есть ссылки на нормы без подтверждения актуальной редакцией.
  Фидбэк: при 👎 — поле комментария (`RateRequest.comment` → `Message.ratingComment`).
- Общий UI-компонент `components/ui/CitationList.tsx` (`CITATION_STATUS_META`/`CitationRow`/`CitationList`/
  `citationSummary`) — переиспользован в `ChatPage` и `CaseDetailPage` (удалены локальные дубли).
- Метрики: `CitationCheckService` пишет counter `pravoos.citation.checks{status}`; `LegalDomainGuard`
  уже писал `pravoos.guard{result}`. `AdminStatsService` читает их из `MeterRegistry` → `AiStatsResponse`
  += `guardChecks/guardRefusals/citationsChecked/citationsVerified`. `AiStatsPage` — секция «Доверие»:
  доля проверенных ссылок, доля полезных оценок, доля отказов (не по теме).
- Без миграций/новых сущностей. Тесты зелёные (`CitationCheckServiceTest` обновлён под новый конструктор),
  `ModularityTests` целы, `tsc --noEmit` чист.

---

## Рекомендуемый порядок

1. **Спринт 1 (критично):** №1 стриминг · №3 документация · старт №2 (актуальное право).
2. **Спринт 2 (монетизация/удержание):** №4 биллинг · №7 календарь.
3. **Спринт 3 (дифференциация):** №5 судебная аналитика · №6 сравнение версий · №10 доверие.
4. **Стратегия:** №8 workflow-агенты · №9 онлайн-редактор.

## Источники (конкурентный анализ)
- ПравоТех / Casebook — https://pravo.tech/products
- XSUD — https://xsudsoft.ru/ · Jeffit — https://jeffit.ru/cases
- Обзор нейросетей для юристов 2026 — https://vc.ru/legal/2918326-neyroseti-dlya-yuristov-sravnenie-rossiyskih-i-zarubezhnyh-ii
- Harvey AI — https://www.harvey.ai/ · Spellbook — https://spellbook.com/learn/legal-ai-tools
