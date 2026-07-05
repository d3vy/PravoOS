# Рефакторинг: модуляризация ai-service / user-service (Spring Modulith)

> План для себя. Правило CLAUDE.md: сначала план по модулям без кода → подтверждение → по одному модулю за раз → `/clear` между модулями.
> Статус: **Ф0 ✅, Ф1 ✅ (user-service `identity` + `shared` выделены). Дальше — Ф2.**

## 0. Контекст и цель

Боль: `ai-service` разросся до ~50 сервис-классов и смешивает два разных bounded-context; `user-service` (~28) тоже несёт разнородное. Логика «размыта».

Цель: чёткие границы «каждый модуль за своё», без потери безопасности и без микросервисного налога.

## 1. Решение и что ОТВЕРГНУТО (чтобы будущий я не переигрывал)

**Отвергнуто: разбиение по ролям** (`lawyer-service` / `client-service` / `admin-service`).
Причина: данные PravoOS не делятся по актёру. Одно `case` трогают юрист (owner), клиент (portal read), админ (stats). Сервис-на-роль → все трое пишут/читают одни `cases`/`clients`/`documents` → распределённый монолит с общей БД ЛИБО кросс-вызовы + eventual consistency там, где сейчас SQL JOIN. Плюс ломает engineered-инвариант «ноль кросс-чтений» (JWT-claim `orgs`/`clients`).

**Выбрано: вариант C — Spring Modulith сейчас, физический сплит потом.**
Режем по **bounded-context / владению данными**, а не по актёру. Границы проводим пакетами внутри существующих сервисов (`@ApplicationModule`, ArchUnit-верификация границ, события вместо прямых вызовов). Один деплой, одна БД на сервис, ноль сетевого tax. Физический вынос — отдельный будущий этап при реальной нагрузочной причине (см. §7).

## 2. Целевая карта модулей

### ai-service → 2 модуля
| Модуль | Ответственность | Владеет данными | Классы (из текущих) |
|---|---|---|---|
| **`ai`** (ядро) | RAG, LLM, эмбеддинги, чат, квоты, guard, citation/contract-анализ, документы+чанки | `documents`, `document_chunks`, `ai_responses`, Mongo conversations/messages | Chat, Rag, Embedding, LlmClient/OpenAi, LlmQuota, CitationCheck, ContractReview, Document, EmbeddingPipeline, DocumentParser, TextChunker, FileCrypto, MalwareScan, AiResponse |
| **`practice`** | Управление практикой: дела, клиенты, контакты, драфты, задачи, workflow, шаблоны, дедлайны, arbitr, экспорт, dashboard, поиск, клиентский портал | `cases`, `case_*`, `clients`, `client_*`, `case_drafts`, `document_templates` | Case, Client, ClientContact, Draft, CaseTask, Workflow, Template, DeadlineReminder, Arbitr*, CaseExport/DocxExport, Dashboard, Search, Portal*, AdminStats |

### user-service → 3 модуля
| Модуль | Ответственность | Классы |
|---|---|---|
| **`identity`** | Auth, JWT-выпуск, refresh, MFA, denylist, парольная политика, login-attempt, email-верификация/reset | Auth, RefreshToken, Mfa, MfaChallenge, TokenDenylist, PasswordPolicy, PasswordReset, LoginAttempt, EmailVerification, User |
| **`registration`** | Заявки юристов + одобрение админом (lifecycle пользователя) | Application, Admin, InternalApplication |
| **`collaboration`** | Организации, инвайты орг, клиентский портал-инвайт, telegram-link, делегированные уведомления (deadline/case-message email) | Organization, OrganizationInvite, ClientPortalInvite, TelegramLink, DeadlineNotificationEmail, CaseMessageNotification |

`notification-service` — уже связный (чистый Kafka-consumer + Telegram). Не трогаем, кроме возможной чистки.

## 3. Реальные швы связности (замерено по коду) и как режем

Ссылки между контекстами **уже soft UUID** (`documents.case_id`, `ai_responses.case_id` — колонки `UUID` без FK/`@JoinColumn`, как `client_portal_invites.client_id`). Граница режется чисто, hard-FK разрывать не нужно.

Кросс-модульные вызовы, которые надо сделать явными (published API модуля) или заменить событием:

1. **practice → ai** (потребление AI-способностей):
   `WorkflowService`, `DraftService`, `ContractReviewService` держат `LlmClient`, `RagService`, `EmbeddingService`, `LlmQuotaService`.
   → Ввести published-фасад модуля `ai`: `LegalAiPort` (методы `answer(...)`, `embed(...)`, `assertQuota(...)`). Practice зависит только от него, не от внутренностей `ai`.

2. **ai → practice** (`ChatService` держит `CaseRepository`):
   чат скоупится к делу → нужна проверка доступа/существования дела.
   → Published-фасад модуля `practice`: `CaseAccessQuery.isVisible(caseId, principal, orgs)`. Ai зависит от него, не от `CaseRepository`.

3. **ai ↔ document (EmbeddingPipeline)**:
   `EmbeddingPipeline` (ai) уже использует `DocumentService`. Document остаётся в модуле `ai` (владеет чанками/эмбеддингами). Practice/Portal, которым нужен список документов дела, ходят через published `DocumentQuery` модуля `ai` (по `caseId`), не в репозиторий напрямую.

4. **Внутрипроцессные события** (Spring `ApplicationEventPublisher`) уже используются (`EmbeddingPipeline` слушает upload). Их оставляем как штатный меж-модульный канал (Modulith их формализует).

Вывод: тяжёлых разрывов нет. Работа — переложить пакеты + ввести 3 фасада (`LegalAiPort`, `CaseAccessQuery`, `DocumentQuery`) + запретить обход границ ArchUnit-ом.

## 4. Механизм: Spring Modulith

- Зависимость `spring-modulith-starter-core` (+ `spring-modulith-starter-test` для верификации границ).
- Каждый модуль = пакет верхнего уровня внутри сервиса: `com.pravoos.ai.core`, `com.pravoos.ai.practice`, `com.pravoos.user.identity`, `...registration`, `...collaboration`.
- Внутренности модуля — под-пакет `internal/`; наружу торчит только API-пакет (фасады + DTO + события).
- `package-info.java` с `@ApplicationModule(allowedDependencies = {...})` фиксирует разрешённые зависимости.
- Тест `ApplicationModules.of(App.class).verify()` в CI — падает при обходе границы (это и есть «не потерять красоту»: границы теперь исполняемые, а не на словах).

## 5. Фазовый план (по одному модулю за раз, `/clear` между)

Порядок — от наименее связного к самому связному, чтобы каждый шаг был отдельно верифицируем и деплоился.

- **Ф0 ✅ — каркас.** Modulith-зависимости (test-scope) в оба сервиса + `ModularityTests` (bootstrap+docs, без `verify()`). Прод-classpath не тронут. Зелёная сборка.
- **Ф1 ✅ — user-service `identity` (+ `shared`).** Перенесены auth/jwt/mfa/refresh/password/login-attempt/email-verif/`User` → `com.pravoos.user.identity.internal.*`. Сквозная инфра (SecurityConfig, InternalSecret*, exception, util, kafka/outbox, ResendEmailClient) → `com.pravoos.user.shared.*`. DTO/enums и registration/collaboration-классы пока в старых пакетах (Ф2). `@ApplicationModule`+`allowedDependencies` не ставим (нужен `starter-core` на main — придёт с Ф2/Ф5, `verify()` off). Все 74 теста зелёные. → `/clear`.
- **Ф2 — user-service `registration` + `collaboration`.** Заявки/admin отдельно; орги/инвайты/telegram/деленированные email отдельно. Зафиксировать `allowedDependencies` (registration→identity на выпуск токена через фасад). → `/clear`.
- **Ф3 — ai-service `ai` (ядро).** Выделить RAG/LLM/chat/embeddings/documents. Ввести фасад `LegalAiPort` + `DocumentQuery`. Пока practice ещё в общем пакете. Тесты `RagServiceContextBudgetTest`, guard/quota. → `/clear`.
- **Ф4 — ai-service `practice`.** Перенести cases/clients/drafts/workflows/portal/arbitr/dashboard/search. Переключить их AI-зависимости на `LegalAiPort`. Ввести `CaseAccessQuery`, `ChatService` → на него. Тесты IDOR (CaseService/ClientService), `LawyerDataCleanupServiceTest`. → `/clear`.
- **Ф5 — замкнуть границы.** Убрать `open`, прописать финальные `allowedDependencies`, включить `verify()` в CI как gate. Прогон полного набора тестов + smoke по §8.

Каждая фаза = перемещение пакетов (без изменения логики) + минимальные фасады. Никаких новых БД, портов, gateway-маршрутов, Kafka-топиков — топология §Топология памяти не меняется.

## 6. Что НЕ трогаем на этом этапе (инварианты)

- Порты/деплой/compose/gateway-маршруты — без изменений (модули внутри тех же джаров).
- Схемы БД, Flyway, JWT-claims (`orgs`/`clients`), outbox, Kafka-топики — без изменений.
- Публичные REST-контракты `/api/**` — без изменений (контроллеры лишь переезжают в под-пакет модуля).
- Безопасность: фильтры/SecurityConfig/internal-secret/audit — переносятся как есть, поведение идентично.

## 7. Будущий этап (НЕ сейчас): физический сплит

Триггер: реальная причина независимого масштабирования/изоляции (например AI-нагрузка душит practice-запросы, или отдельный SLA/деплой-цикл).
Кандидат №1 на вынос — модуль `ai` (или `practice`). Т.к. границы уже проведены и общение идёт через фасады+события, вынос становится механическим: фасад-интерфейс → REST/Kafka-адаптер, разрез БД `pravoos_ai` по владению таблицами (§2), soft-UUID `case_id` уже готов к межсервисной ссылке. `admin-service`/`client-service` НЕ создаём никогда — это роли-читатели, а не контексты.

## 8. Верификация каждой фазы

1. `mvn -pl <service> -am test` — юнит-тесты модуля зелёные.
2. `ModularityTests.verify()` — границы не нарушены.
3. Точечный ребилд образа (память §Деплой) + `docker compose up -d <service>` + smoke: login → chat → создать дело → загрузить документ на дело (эмбеддинг) → portal-доступ клиента. Ни один REST-контракт не изменился → фронт не трогаем.
4. `git commit` по фазе (обратимость: каждая фаза самостоятельна).

## 9. Открытые вопросы к реализации (решить в начале Ф3/Ф4)

- **Document — в `ai` или `practice`?** План: в `ai` (владеет эмбеддингами); practice видит через `DocumentQuery`. Проверить, что PortalDocument/CaseExport этим покрываются без просачивания репозитория.
- **AdminStats** (`/api/ai/admin/ai-stats`) читает и ai_responses, и cases → положить в `practice` с чтением ai-метрик через `LegalAiPort`, либо оставить тонкий stats-контроллер в `ai`. Решить при Ф4.
