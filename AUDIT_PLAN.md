# PravoOS — план прод-аудита (пасс 3)

> Дата: 2026-06-28. Запрос: «найти 30 недочётов и составить план».
> Сверка кода с `memory/MEMORY.md` выполнена — архитектура в памяти **совпадает** с кодом
> (топология сервисов, JWT/refresh, Kafka, RAG, КАД, дедлайны, дашборд). Расхождения отмечены ниже (см. №4, №20).

## Честный вердикт (по правилу «не накручивать баги под таргет»)

Код **прод-уровня**. Поверхностных багов почти нет. Прямо проверены и **уже корректно решены** гипотезы из запроса:

- **Тайминг-атака на /login (email есть / нет)** — закрыта: `AuthService.login` гоняет `passwordEncoder.matches` по `DUMMY_PASSWORD_HASH` для несуществующего юзера (одинаковая задержка). Ручной `Thread.sleep` не нужен.
- **Хэширование паролей** — BCrypt (`BCryptPasswordEncoder`), refresh/reset-токены хранятся как SHA-256 hex, не в открытом виде.
- **Enumeration** — `/forgot-password`, `/resend-verification` всегда 202; `/apply` под IP-rate-limit.
- **Метрики** — Prometheus во всех 4 сервисах + Grafana-дашборд + alerts.yml уже есть.
- **pgvector** — HNSW + `vector_cosine_ops` (а не ivfflat/seq-scan), параметризованные native-запросы.
- **Секреты** — `.env`/`jwt.pem` в `.gitignore`, в compose только `${ENV}`-подстановки, токены/PII маскируются в логах.
- **Kafka** — `ErrorHandlingDeserializer` + `DefaultErrorHandler` + DLT, trusted-packages по пакету (не `*`).

Поэтому список ниже — это **системные пробелы и улучшения**, а не «30 критических багов». Разбит по приоритету:
🔴 критично · 🟡 важно · 🟢 желательно. Часть пунктов — задачи на **верификацию** (память утверждает, проверить на проде).

---

## A. Безопасность

### 1. 🟡 `/login` без per-IP rate-limit
`AuthController.login` (стр. 73) НЕ использует `IpRateLimiter` — он стоит только на `/apply` (стр. 102).
Brute-force защита — только per-email lockout (Redis, 5 попыток). Значит password-spray / credential-stuffing
по тысяче разных email с одного IP ничем не throttle-ится.
**Фикс:** добавить `ipRateLimiter.allow("login", clientIp, N, window)` в начало `/login` (как в `/apply`), напр. 20/мин на IP.

### 2. 🟢 BCrypt strength = 10 (дефолт)
`new BCryptPasswordEncoder()` → cost 10. Норма 2025 — 12.
**Фикс:** `new BCryptPasswordEncoder(12)`. Старые хэши верифицируются прозрачно (cost в самом хэше); новые/смена пароля — на 12.

### 3. 🟡 nginx без security-заголовков
`docker/nginx/generated/default.conf` ставит только `Cache-Control`. Нет HSTS, X-Frame-Options,
X-Content-Type-Options, Referrer-Policy, базового CSP.
**Фикс:** добавить `add_header Strict-Transport-Security`, `X-Frame-Options DENY`, `X-Content-Type-Options nosniff`,
`Referrer-Policy strict-origin-when-cross-origin`, CSP (хотя бы `default-src 'self'`). Сверить с генератором конфига.

### 4. 🟢 `/actuator/prometheus` — `permitAll` (расхождение с памятью)
Память (пасс 1) фиксировала «prometheus ЗА аутентификацией». В коде сейчас во **всех 4** `SecurityConfig`
он `permitAll`, и Prometheus скрейпит без `basic_auth`. Наружу не торчит (gateway роутит только `/api/**`),
но внутри сети открыт.
**Фикс:** либо вернуть за auth + `basic_auth` в `prometheus.yml`, либо явно задокументировать в памяти, что
это осознанно (доступ ограничен docker-сетью). Сейчас память и код противоречат — поправить одно из двух.

### 5. 🟡 Верификация: gateway access-token denylist + JWT
Проверить `JwtAuthFilter` на gateway: fail-open или fail-closed при недоступности Redis (denylist),
корректность проверки `iat < revoked_after`, чистку входящих `X-User-*`. Память описывает — нужен тест/прогон.

---

## B. AI-сервис: корректность и стоимость токенов

### 6. 🔴 RAG-контекст из вложений не ограничен (token-overflow / стоимость)
`ChatService.chat` → `loadOwnedAttachedDocuments` → `findContentByDocumentIdIn` тянет **ВСЕ** чанки вложенных
документов и `relevantChunks.addAll(0, attachedChunks)` вставляет их целиком в системный промпт **без token-бюджета**.
Вложение большого PDF → промпт превышает контекст модели → 400 от OpenAI / резкий рост стоимости.
**Фикс:** ввести общий бюджет контекста (напр. лимит N токенов на `joinContext`), либо top-K по релевантности
и для вложений (embed запрос → similarity внутри вложенных чанков), либо хотя бы hard-cap на число чанков/символов.

### 7. 🟡 Guard-классификатор на КАЖДОМ сообщении
`assertLegalQuery` — отдельный LLM-вызов на каждый `chat()`, в т.ч. на 10-е сообщение заведомо юридического диалога.
Лишняя латентность + стоимость на каждый запрос.
**Фикс:** пропускать guard для продолжения существующей беседы (только первое сообщение), либо кэшировать вердикт
по нормализованному тексту (см. №29).

### 8. 🟡 Guard делит RestClient с read-timeout 120с
`LegalDomainGuard` и `OpenAiLlmClient` используют один `openAiRestClient` (`READ_TIMEOUT=120s`).
Зависший дешёвый guard-вызов держит весь чат 2 минуты.
**Фикс:** отдельный `RestClient`/таймаут для guard (5–10с) — guard должен быстро fail-open.

### 9. 🟡 Нет retry/backoff на 429/5xx OpenAI
`OpenAiLlmClient` при `RestClientResponseException` сразу кидает `LlmException`. На rate-limit (429) или
кратковременный 5xx — запрос юриста падает.
**Фикс:** retry с экспоненциальным backoff на 429/500/502/503 (Spring Retry или ручной), 2–3 попытки, jitter.

### 10. 🟡 «Осиротевшее» USER-сообщение при падении LLM
`ChatService.chat` сохраняет USER-`Message` (стр. 103) ДО `llmClient.complete`. Если LLM падает —
в истории Mongo остаётся вопрос без ответа, который потом грузится в `buildLlmHistory`.
**Фикс:** сохранять пару user+assistant атомарно после успешного ответа, либо удалять user-сообщение в catch,
либо помечать беседу статусом и не подмешивать «висячие» сообщения в историю.

### 11. 🟢 Guard fail-open — нет наблюдаемости
При недоступности OpenAI guard пропускает нежюридические запросы (осознанный trade-off из памяти),
но это «тихо».
**Фикс:** counter `pravoos.guard{result=allow_failopen|block|pass}` + алерт на всплеск fail-open.

### 12. 🟢 История диалога — жёсткий top-10 без token-бюджета
`findTop10ByConversationIdOrderByCreatedAtDesc` — 10 сообщений вне зависимости от их размера.
**Фикс:** ограничивать историю по токенам, при длинной беседе — суммаризация старых сообщений.

### 13. 🟢 Верификация: usage-токены не учитываются
Проверить, парсит ли `OpenAiChatResponse` `usage.total_tokens`. Если нет — добавить и публиковать метрику
стоимости (токены на запрос/юриста) — прямой ответ на вопрос «как мы тратим токены».

---

## C. Производительность / надёжность

### 14. 🟡 OpenAI RestClient без пула соединений
`RestClientConfig` использует `SimpleClientHttpRequestFactory` (JDK, без keep-alive/пула) → новый TCP+TLS
handshake на каждый вызов к OpenAI. На нагрузке — лишняя латентность.
**Фикс:** `HttpComponentsClientHttpRequestFactory` (Apache HttpClient5) с пулом + keep-alive.

### 15. 🟡 Нет пагинации на списках
`/cases`, `/clients`, `/documents`, `/conversations`, contacts, messages отдают всё. Растёт линейно с данными юриста.
**Фикс (согласованно backend+frontend):** `Pageable` + `Page<>`; фронт сейчас ждёт массив — менять контракт парой.

### 16. 🟡 DLT-топики не провижинятся
`DeadLetterPublishingRecoverer` пишет в `<topic>.DLT`, но `NewTopic`/`KafkaAdmin` для `.DLT` нет.
При `auto.create.topics=false` на проде — сообщения после ретраев теряются.
**Фикс:** объявить `.DLT`-топики через `@Bean NewTopic` в каждом сервисе-консьюмере.

### 17. 🟢 ai-service Kafka listener concurrency не задан
В `notification` `setConcurrency(2)`, в `ai-service/KafkaConsumerConfig` — нет (дефолт 1).
**Фикс:** задать concurrency по числу партиций топика `lawyer.deleted`.

### 18. 🟢 Mongo conversations/messages растут безгранично
Нет TTL / архивации / лимита истории. Чат-история копится вечно.
**Фикс:** политика ретенции (TTL-индекс или архив старых бесед), либо лимит N бесед на юриста.

### 19. 🟢 Hikari — не настроен размер пула
Заданы `connection-timeout` и `leak-detection-threshold`, но не `maximum-pool-size`/`minimum-idle`
под фактическую нагрузку и лимиты PostgreSQL.
**Фикс:** явно задать пул и сверить с `max_connections` Postgres.

---

## D. Тесты / наблюдаемость

### 20. 🔴 Нет тестов критичного пути (IDOR/ownership)
Сейчас 13 тест-классов (util + JWT). Не покрыты: `CaseService.requireOwnedCase`, `ClientService.requireOwnedClient`,
attach-IDOR в `ChatService`, refresh-ротация + reuse-detection, `updateStatus`, guard-классификатор.
Это самый ценный пробел: ownership-чеки — главная линия защиты от утечки данных между юристами.
**Фикс:** интеграционные/слайс-тесты на каждый ownership-метод (свой/чужой/несуществующий id → 404/403).

### 21. 🟢 Нет distributed tracing
Correlation-id (`X-Request-Id`) есть, span-трейсинга между сервисами — нет.
**Фикс:** `micrometer-tracing` + OTel/Zipkin (отдельная задача, не срочно).

### 22. 🟡 Верификация качества алертов Prometheus
Проверить `docker/prometheus/alerts.yml`: покрыты ли service-down, 5xx-rate, p99-latency, Kafka consumer lag,
`*.DLT` size > 0, OpenAI-ошибки. Дашборд `pravoos-overview.json` — добавить панели login success/fail,
guard-block rate, LLM-латентность и токены.

---

## E. Данные / валидация входа

### 23. 🟡 Нет ограничения длины `ChatRequest.message` (token-бомба)
Проверить DTO — если нет `@Size`, юрист (или скомпрометированный токен) может прислать мегабайтный текст →
дорогой embed + промпт.
**Фикс:** `@Size(max=...)` на `message` и на текстовые поля шаблонов/черновиков.

### 24. 🟢 `document_templates.content` без лимита → token-бомба при apply
`TemplatePlaceholderResolver` детерминирован, но огромный шаблон раздувает итоговый черновик/экспорт.
**Фикс:** лимит размера контента шаблона (валидация в `TemplateService`).

### 25. 🟢 Верификация: индексы на FK и каскады
Память подробно описывает индексы (`lawyer_id`, `case_id`, partial unique и т.д.) и `ON DELETE CASCADE`.
Прогнать `flyway info` + `EXPLAIN (ANALYZE)` на горячих запросах (`findByLawyerId*`, поиск, vector-search)
— подтвердить, что индексы используются и нет seq-scan.

### 26. 🟢 Верификация: cleanup при удалении юриста
`LawyerDataCleanupService` — проверить, что покрывает ВСЕ сущности (cases, drafts, clients, contacts, templates,
tasks, hearing_events, conversations+messages Mongo). Память утверждает полноту — нужен интеграционный тест
«удалили юриста → 0 осиротевших строк во всех таблицах + Mongo».

---

## F. Прочие улучшения

### 27. 🟢 Кэш guard-вердикта
Одинаковые/повторные запросы повторно классифицируются (платный вызов). Кэшировать вердикт по нормализованному
тексту (Caffeine, короткий TTL) — экономия токенов.

### 28. 🟢 Метрика стоимости LLM на юриста
Завести counter токенов/вызовов по `lawyerId` (или агрегированно) → видеть, кто и сколько «жжёт», основа для
будущих лимитов/тарифов.

### 29. 🟢 Лимит LLM-запросов на юриста (quota)
Сейчас юрист может слать неограниченно много дорогих chat/workflow-запросов. Ввести дневной/часовой лимит
(Redis-счётчик, как brute-force) → защита от runaway-стоимости.

### 30. 🟢 Frontend прод-наблюдаемость
`ErrorBoundary` уже есть. Добавить отправку фронт-ошибок в прод (Sentry/собственный эндпоинт), проверить что
single-flight refresh и boundary покрывают lazy-загруженные роуты.

---

## Предлагаемый порядок реализации (фазами, по одному модулю за раз)

**Фаза 1 — критично (данные/стоимость/защита):** ✅ ВЫПОЛНЕНО (2026-06-28)
- ✅ №1 — IP-rate-limit на `/login` (`AuthController`, 30/5мин на IP через `IpRateLimiter`).
- ✅ №9 — retry с экспон. backoff+jitter на 429/5xx OpenAI (`OpenAiLlmClient.executeWithRetry`).
- ✅ №6 — token-бюджет RAG (`document.context-max-chars=24000`, обрезка в `RagService.joinContext` с приоритетом вложений).
- ✅ №16 — **уже было сделано ранее** (NewTopic-бины `.DLT` в обоих `KafkaTopicsConfig`); находка аудита оказалась неточной.
- ✅ №20 — тесты ownership/IDOR: `CaseServiceOwnershipTest`, `ClientServiceOwnershipTest`, `RagServiceContextBudgetTest` (9 тестов, зелёные). Остаток (refresh-ротация, attach-IDOR в ChatService) — отдельным пассом.

Не закоммичено (по правилу — только по команде).

**Фаза 2 — важно (надёжность/наблюдаемость):**
- ✅ №7 — guard только на первом сообщении новой беседы (`ChatService.chat`).
- ✅ №8 — отдельный `openAiGuardRestClient` с read-timeout 10с (`RestClientConfig`, `LegalDomainGuard`).
- ✅ №10 — user+assistant сохраняются атомарно ПОСЛЕ успешного ответа LLM; при падении — ни orphan-сообщения, ни пустой беседы (`ChatService.chat`).
- ✅ №23 — **уже было** (`@Size(max=4000)` на `ChatRequest.message`).
- ✅ №3 — security-заголовки nginx (HSTS на 443, X-Frame-Options DENY, X-Content-Type-Options nosniff, Referrer-Policy, CSP) в обоих шаблонах + regenerated `generated/default.conf`; `/grafana/` без своей CSP.
- ✅ №14 — пул соединений OpenAI на Apache HttpClient5 (`PoolingHttpClientConnectionManager`, keep-alive, shared между основным и guard-клиентом) в `RestClientConfig`.
- ✅ №22 — алерты `HighRequestLatencyP99`, `KafkaConsumerLag`; включены percentiles-histogram (`http.server.requests`) во всех 4 сервисах (чинит существующую p95-панель дашборда). Дашборд-панели guard-block/LLM-токены — после появления метрик (фаза 3, №11/№13). Выделенный алерт на OpenAI-ошибки — туда же; сейчас покрыто через `HighHttp5xxRate` на ai-service.
- ✅ №5 — верификация gateway denylist: fail-open/closed конфигурируем и корректен, `iat<=revoked_after` верно, X-User-* стираются глобально (`UserHeaderSanitizingFilter`) и переустанавливаются из проверенных claims. Зафиксировано тестом `JwtAuthFilterTest` (7 тестов, зелёные).
- ✅ №15 (пагинация) — отдельным коммитом. Backend: `Pageable`+`Page<>` на 6 эндпоинтах (cases, clients, documents, conversations, messages, contacts), отдаём **массив в теле + `X-Total-Count`** (как в админ-эндпоинтах user-service — единая конвенция), `PageRequests` клампит size (default 20, max 100), `PagedResponse` хелпер. Frontend: общий `api/pagination.ts` (`Page<T>`, `readTotal`), реальная page-навигация на Cases/Clients/Documents (`Pagination`-компонент), чат/контакты/дропдауны — bounded одной страницей (size=100). `tsc`+`vite build` зелёные, 26 backend-тестов зелёные. Прогон на запущенном фронте — за тобой (Docker daemon в этой сессии не поднят).

**Фаза 3 — желательно (полировка/экономия):** ✅ ВЫПОЛНЕНО ПОЛНОСТЬЮ (2026-06-28)
- ✅ №2 — BCrypt strength 12 (`SecurityConfig.BCRYPT_STRENGTH`); старые cost-10 хэши верифицируются прозрачно.
- ✅ №4 — `/actuator/prometheus` зафиксирован как осознанный `permitAll` (internal-only docker-сеть); противоречие в `memory/MEMORY.md` устранено (код первичен).
- ✅ №11 — counter `pravoos.guard{result=pass|block|allow_failopen|cache_hit}` в `LegalDomainGuard`.
- ✅ №12 — история диалога ограничена по символам (`llm.history-max-chars=12000`, `ChatService.applyCharBudget`), а не жёстким top-10.
- ✅ №13 — парсинг `usage.{prompt,completion,total}_tokens` (`OpenAiChatResponse.Usage` → `LlmUsage`); `complete` возвращает `LlmResult{content,usage}`.
- ✅ №17 — `ai-service` Kafka concurrency = числу партиций (`KafkaConsumerConfig`, `${app.kafka.topic-partitions:3}`).
- ✅ №18 — конфигурируемый TTL чат-истории (`chat.retention-days`, **default 0 = выкл** — включение ops-решением; при >0 TTL-индексы на `createdAt` обеих коллекций в `MongoIndexConfig`).
- ✅ №19 — **уже было** (Hikari `maximum-pool-size`/`minimum-idle`/timeout/leak в обоих сервисах); находка аудита неточна.
- ✅ №21 — distributed tracing: `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` во всех 4 сервисах; экспорт OTLP → **Grafana Tempo** (`docker/tempo/tempo.yaml`, сервис в compose, datasource в Grafana с trace↔metrics/serviceMap). Конфиг `management.tracing.sampling.probability` (`TRACING_SAMPLE_PROBABILITY`, prod-дефолт 0.1, dev-override 1.0) + `management.otlp.tracing.endpoint`. traceId/spanId добавлены в logback-паттерн всех 4 (корреляция логов и трейсов; `X-Request-Id` сохранён). `.env.example` дополнен.
- ✅ №24 — лимит размера шаблона `@Size(max=50000)` на `content` в Create/UpdateTemplateRequest.
- ✅ №25 — `scripts/verify-indexes.sql` (EXPLAIN ANALYZE по горячим запросам + аудит `pg_stat_user_indexes`/`_tables`); прогон — на запущенной БД с данными.
- ✅ №26 — `LawyerDataCleanupServiceTest`: удаление юриста чистит все 6 реляционных сущностей (`verifyNoMoreInteractions`) + conversations/messages Mongo + pending-маркер.
- ✅ №27 — Caffeine-кэш guard-вердикта (`maxSize=10k`, TTL=1ч, ключ — нормализованный текст; fail-open НЕ кэшируется).
- ✅ №28 — агрегатные counters `pravoos.llm.requests` и `pravoos.llm.tokens{type=prompt|completion}` (`LlmMetrics`); per-lawyer — через INFO-лог токенов (избегаем cardinality-взрыва в Prometheus).
- ✅ №29 — дневная квота LLM на юриста (`LlmQuotaService`, Redis-счётчик `llm_quota:<id>:<date>`, `llm.quota.daily-requests=200`, **0=выкл**); проверяется в chat/workflow/draft, при превышении — 429 `LLM_QUOTA_EXCEEDED`.
- ✅ №30 — frontend error-reporter (`lib/errorReporter.ts`): отправка на `VITE_ERROR_REPORT_URL` через `sendBeacon`/`fetch keepalive` (no-op если не задан), дедуп + лимит на сессию; подключён в `ErrorBoundary` + глобальные `error`/`unhandledrejection` (покрывают lazy-роуты).

Проверки: `mvn -pl ai-service test` — 28 тестов зелёные; `tsc --noEmit` фронт — чисто. №25 (EXPLAIN) и прогон фронта — на запущенном окружении. Не закоммичено (по правилу — только по команде).

> После подтверждения плана — реализуем по одному пункту/модулю, с `/clear` между крупными блоками
> (правило экономии контекста из CLAUDE.md). Каждая правка — с тестом, где это применимо.
