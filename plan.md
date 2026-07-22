# PravoOS — План конкурентоспособности (стадия: пилот / первые юзеры)

> Приоритет под пилот: сначала монетизация и фичи-крючки (удержание), комплаенс/инфра — фоном.
> Каждый пункт: суть → модули → команды для старта. Код не пишем здесь — только направление и объём.
> Порядок реализации: **1 → 4 → 5 → 6 → 3/7 → 2/8 → 9/10**.

---

## 1. Биллинг и тарифы (YooKassa) — ФУНДАМЕНТ ВЫРУЧКИ · В РАБОТЕ
Есть: `LlmQuotaService` (Redis-квоты, ai-service/shared), роли/орги, JWT-claims `orgs`/`clients`. Нет: планов, платежей, gating фич.
**Архитектурное решение:** новый Modulith-модуль **`billing` в user-service** (зеркало `collaboration`), НЕ отдельный микросервис (для пилота — общий релизный цикл, вынести позже). Подписка привязана к **юристу** (owner); орг-биллинг = follow-up. План пробрасывается в ai-service **JWT-claim'ом `plan`** (тот же паттерн, что `orgs`/`clients`) → `LlmQuotaService` берёт лимиты из плана. Платёжка — **YooKassa** (РФ).

Границы модуля `billing` (package-info): `allowedDependencies = {shared, identity :: api/model/enums/repository}`.

**Под-фазы (каждая = отдельный /clear + ветка):**
- **M1 — Модель тарифов и подписок (без платежей). ✅ СДЕЛАНО.** V19: `plans` (seed FREE/SOLO/TEAM/FIRM — лимиты запросов/токенов/мест; partial-UNIQUE `is_default` держит ровно один дефолтный) + `subscriptions` (user_id UNIQUE → одна подписка на юриста, `@Version` против гонок). Модуль `com.pravoos.user.billing` (allowed=`shared`): `Plan`/`Subscription`/`SubscriptionStatus`, репозитории, `SubscriptionService`. Авто-trial (SOLO, 14 дн, конфиг `app.billing.trial-*`) вешается на `ApplicationService.approveApplication` через фасад **`billing.api.SubscriptionProvisioner`** (registration += `billing :: api`) — идемпотентен. `GET /api/user/billing` (+ security-matcher `LAWYER|ADMIN`, иначе CLIENT прошёл бы в `anyRequest().authenticated()`); для юристов, заведённых ДО биллинга, статус лениво создаёт подписку на дефолтном FREE. Тест `SubscriptionServiceTest` (5). ModularityTests зелёные.
- **M2 — Проброс плана в квоты. ✅ СДЕЛАНО.** Claim `plan` = `{code, dailyRequests, dailyTokens}` в access-токене (`JwtTokenProvider.generateToken` +1 арг; `AuthService.issueTokens` берёт план через фасад **`billing.api.PlanClaimProvider`**, identity += `billing :: api`). Эффективный план: подписка в `ACTIVE`/`TRIALING` → её план, иначе (`PAST_DUE`/`CANCELED`/нет подписки) → дефолтный FREE — без создания строк в БД. CLIENT-роль claim не получает. ai-service: `JwtAuthenticationFilter` парсит claim → `PlanLimits` в `OrgContext` (`pravoos-common-web`), `SecurityUtils.currentPlanLimits`; `PlanLimitsProvider` отдаёт лимиты текущего запроса с фолбэком на статик-конфиг (`llm.quota.*`) для контекстов без auth (async-эмбеддинги). `LlmQuotaService` считает квоту от плана. Лимиты обновляются при refresh-токене (≤ время жизни access-токена). Тесты: `LlmQuotaServiceTest` (5), `SubscriptionServiceTest` (8).
  - ⚠️ **Отложено: `FeatureGate`** (доступность премиум-фич по плану). Сейчас **ничего никому не блокируем** — состав тарифов ещё не продуман. Когда решим, что входит в FREE/SOLO/TEAM/FIRM: хелпер `FeatureGate` (`SecurityUtils.currentPlanLimits().code()` → иерархия тарифов) + `PlanUpgradeRequiredException` (403 `PLAN_UPGRADE_REQUIRED`), вешать на премиум-фичи (кандидаты: анализ договора, сравнение документов, workflow). Делать вместе с M5 (enforcement).
- **M3 — Интеграция YooKassa. ✅ СДЕЛАНО.** V20 `payments` (`provider_payment_id` UNIQUE, `@Version`). `YooKassaClient` (Basic-auth `shopId:secretKey`, `Idempotence-Key` на создании платежа, таймауты 5/20 с; создать платёж → `confirmation_url`, перезапросить платёж → статус). `PaymentService.startCheckout` (план по коду, отказ на бесплатном плане, платёж PENDING) + `POST /api/user/billing/subscribe {planCode}` — взят из M4, иначе вебхук нечем прогнать end-to-end. **Публичный** вебхук `POST /api/billing/webhook`: permitAll в `SecurityConfig` + gateway-маршрут `user-service-billing-webhook` (без `JwtAuthFilter`, свой rate-limit 5/20 по IP).
  - **Подлинность вебхука:** YooKassa **не подписывает** колбэки (HMAC-заголовка нет) — поэтому проверяем (а) IP-allowlist сетей YooKassa (`WebhookIpAllowlist`, CIDR, читает первый элемент `X-Forwarded-For`; пустой список = выключено для dev) и (б) **перезапрашиваем платёж через API** — тело колбэка не является источником истины, из него берём только `object.id`.
  - **Идемпотентность/безопасность денег:** применение в `PaymentApplier` (отдельный бин — `@Transactional` не сработал бы через self-invocation): не-PENDING → выход; сумма из API ≠ сумма платежа → не активируем (лог ERROR); `succeeded` → SUCCEEDED + `SubscriptionService.activateOnPlan` (ACTIVE, `current_period_end` += `period-days`, при продлении того же плана — от текущего конца периода). Гонка параллельных колбэков ловится `@Version`.
  - Конфиг: `app.billing.yookassa.*` (`YOOKASSA_SHOP_ID`/`YOOKASSA_SECRET_KEY`/`YOOKASSA_RETURN_URL`/`BILLING_PERIOD_DAYS`/`YOOKASSA_WEBHOOK_IPS`), пусто → 503 `BILLING_NOT_CONFIGURED`. Секрет забланкован в остальных сервисах compose. Тесты: `PaymentApplierTest` (5), `WebhookIpAllowlistTest` (5).
  - Outbox для активации не понадобился: платёж и подписка — одна БД, активация атомарна в одной транзакции. Outbox-событие заведём, когда появится потребитель (уведомление об оплате).
  - ⚠️ После оплаты новый план попадает в токен только на следующем refresh (≤ 15 мин) — в M4 на фронте после возврата из YooKassa дёрнуть refresh, чтобы лимиты применились сразу.
- **M4 — Управление подпиской (endpoints + UI). ✅ СДЕЛАНО.** V21: `subscriptions.cancel_at_period_end`. Отмена (`POST /api/user/billing/cancel`) **не гасит подписку сразу** — ставит флаг, статус остаётся ACTIVE, доступ до `current_period_end` (иначе CANCELED мгновенно уронил бы entitlement в `effectivePlanFor`); фактический перевод в CANCELED — в M5 (`@Scheduled`). `activateOnPlan` сбрасывает флаг (оплата = реактивация). Ещё: `GET /api/user/billing/plans` (тарифы, сортировка по цене), `GET /api/user/billing/payments` (история). Фронт: `api/billing.ts`, `BillingPage` (`/billing` + пункт «Подписка» в навигации юриста): текущий тариф с лимитами, баннеры trial/past-due/отмена, сетка тарифов с кнопкой «Оплатить» → redirect на `confirmation_url`, история платежей (для PENDING — ссылка «Оплатить»). На маунте `/billing` дёргает `refreshSession()` → после возврата из YooKassa новый план сразу попадает в claim. Тесты: `SubscriptionServiceTest` (11).
- **M5 — Enforcement + grace/downgrade. ✅ СДЕЛАНО.** `SubscriptionLifecycleService.sweepExpired` (`@Scheduled` 03:40 + `@SchedulerLock` — как остальные sweep'ы): истёк `current_period_end` → если стоял `cancel_at_period_end` → CANCELED, иначе → PAST_DUE; PAST_DUE старше `current_period_end + grace-days` → CANCELED. **Downgrade — не в статусе, а в entitlement:** `SubscriptionService.entitled()` (используется `effectivePlanFor` → JWT-claim) — CANCELED → дефолтный FREE; PAST_DUE/ACTIVE/TRIALING внутри `period_end + grace-days` → план сохраняется. То есть grace работает, даже если sweep не отработал (падение шедулера не даёт бесплатного доступа), а после grace лимиты падают на FREE не позднее следующего refresh токена (≤ 15 мин). Конфиг `app.billing.grace-days` (`BILLING_GRACE_DAYS`, дефолт 5). Rate-limit вебхука — сделан в M3. Тесты: `SubscriptionLifecycleServiceTest` (4), `SubscriptionServiceTest` (13).
  - Блок премиум-фич при неоплате не делаем — см. отложенный `FeatureGate` в M2: тарифы не финализированы, при неоплате юрист просто падает на FREE-лимиты запросов/токенов.

```bash
git checkout main && git pull && git checkout -b feat/billing-m1
ls user-service/src/main/resources/db/migration/   # след. номер = V19 (последняя V18)
export JAVA_HOME=/Users/ilia/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home
mvn -B -q verify -pl user-service -am               # модульные + Modulith-gate (ModularityTests)
docker compose -f docker-compose.yml -f docker-compose.override.yml up -d --wait user-service
```
Trade-off: без этого продукт бесплатный. Наивысший ROI. Квоты и роли уже есть — надстройка.

---

## 4. Учёт времени и биллинг часов (time tracking → счета) — KILLER-ФИЧА ЮРФИРМ · ✅ СДЕЛАНО
Было: CRM клиентов, дела, `case_tasks`, DOCX/PDF-экспорт (DejaVuSans). Стало: учёт времени (ручные записи + серверный таймер) → счёт за услуги с PDF-выгрузкой. Всё в модуле `practice` (ai-service), границы Modulith не менялись (`shared` + `core::api` + `document::api`).

- **V33 `time_tracking`:** `time_entries` (case/client/lawyer/description/activity_date/minutes/hourly_rate NUMERIC(12,2)/billable/running/started_at/invoice_id), `invoices` (`@Version`, UNIQUE `(lawyer_id, number)`, subtotal/total NUMERIC(14,2)), `invoice_lines` (снапшот описания/минут/ставки/суммы). FK: time_entries→cases `ON DELETE CASCADE`, time_entries→invoices `ON DELETE SET NULL`, invoice_lines→invoices `CASCADE`. Partial-UNIQUE `uq_time_entries_running_lawyer WHERE running` держит **ровно один активный таймер на юриста** (проверено на живом pg16 — вторая вставка падает). Partial-index на несписанные billable-часы.
- **Деньги — чистая функция `BillingAmounts`:** `lineAmount = rate * minutes / 60`, HALF_UP до копеек. Сумма записи считается на лету (в `time_entries` не хранится); в `invoice_lines` — снапшот на момент выставления.
- **Учёт времени — `TimeEntryService`:** ручная запись + таймер (`startTimer`/`stopTimer`: минуты = round(elapsed), гонка ловится и unique-индексом, и pre-check → `TimerAlreadyRunningException` 409). Запись, попавшая в счёт (`invoice_id != null`), или запущенный таймер — неизменяемы (`TimeEntryLockedException` 409). `CaseTimeSummary` даёт по делу: всего/billable/несписанные минуты и суммы (running исключены).
- **Счета — `InvoiceService`:** `create` собирает несписанные billable-часы клиента (опц. фильтр по делу или явный список `timeEntryIds` с фильтрацией чужих/списанных), строит строки, ставит `invoice_id` на записи (списание). Нумерация `СЧ-{год}-{NNNN}` per-lawyer (`InvoiceNumberGenerator`, count+pad; при коллизии unique — retry с `bump`). Статусы `DRAFT→ISSUED→PAID`/`CANCELED` (`InvoiceStatus.canTransitionTo`); **отмена/удаление черновика возвращают часы** (`releaseByInvoiceId`) — их можно перевыставить. `InvoicePdfWriter` рендерит счёт таблицей (DejaVuSans, ru-формат денег), выгрузка `GET /api/ai/invoices/{id}/export`.
- **Безопасность:** `/api/ai/invoices/**` и `/api/ai/time/**` → `hasRole("LAWYER")` в `SecurityConfig` (иначе CLIENT прошёл бы в `anyRequest().authenticated()`); case-scoped `/api/ai/cases/**` уже под LAWYER. Все операции идут через `requireVisibleCase`/`requireOwnedClient`/`findByIdAndLawyerId`. Очистка при удалении юриста расширена (`LawyerDataCleanupService`: time_entries + invoices до cases/clients).
- **Фронт:** `CaseTimeSection` в карточке дела (живой таймер mm:ss, ручная запись в часах, список с суммами/бейджем «в счёте», кнопка «Выставить счёт» по делу), раздел «Счета» (`/invoices` + пункт навигации): список, карточка счёта с таблицей строк, сменой статуса и «Скачать PDF». API `time.ts`/`invoices.ts`, `utils/billing.ts` (формат денег/длительности).
- Тесты: `BillingAmountsTest` (5), `TimeEntryServiceTest` (7), `InvoiceServiceTest` (8), `InvoiceNumberGeneratorTest` (3), обновлён `LawyerDataCleanupServiceTest`. Весь ai-service: 197 тестов зелёные, ModularityTests зелёные, фронт `tsc && vite build` чистый.
- ⚠️ Реквизиты исполнителя (юрист/фирма) в PDF пока не выводятся — в счёте только плательщик + строки; НДС/налог не считаем (total = subtotal). Добавить реквизиты фирмы можно позже (отдельная сущность настроек биллинга).

Trade-off: причина №1 для B2B-юрфирм платить (ядро Clio). Модель дело→клиент→юрист готова — это надстройка.

---

## 5. Электронная подпись (КЭП/УКЭП) — замкнуть жизненный цикл документа · ✅ СДЕЛАНО
Было: простая ЭП в портале (V32 `signature_requests`, подтверждение клиентом + фиксация хеша/IP/UA) и заглушка `NoopDiadocSignatureProvider`. Стало: **усиленная подпись без внешнего сервиса**, протокол подписания и закрытый жизненный цикл запроса. Всё в `practice` + `shared/signature`, границы Modulith не менялись.

- **УКЭП через открепленную подпись (`DETACHED_CMS`) — вместо интеграции с Диадоком.** Клиент подписывает файл своей КЭП в привычном инструменте (КриптоАРМ / Госключ / КриптоПро CSP) и загружает контейнер `.sig`/`.p7s`; сервер проверяет его сам. **`DetachedCmsVerifier`** (BouncyCastle `bcprov`+`bcpkix`, провайдер BC регистрируется лениво): принимает DER, base64 и PEM-armor; `CMSSignedData(CMSProcessableByteArray(content), container)` — тело колбэка не нужно, **истина — содержимое документа из хранилища**; сверяет подпись, достаёт сертификат подписанта (CN/subject/issuer/serial/срок), алгоритм и `signingTime`. Ошибки — единый `InvalidSignatureFileException` (422 `INVALID_SIGNATURE_FILE`), без утечки деталей крипто-исключений. GOST 2012 работает штатно (BC поддерживает `ECGOST3410-2012` + `GOST3411-2012`), тесты гоняют RSA-контейнер, чтобы не тащить ГОСТ-ключи в репозиторий.
  - **Валидность сертификата проверяется на момент подписания** (`signingTime` из подписанных атрибутов), а не «сейчас»: подпись, сделанная действующим сертификатом, не должна протухать вместе с ним. Само `signingTime` — самодекларация подписанта, поэтому оно кладётся в отдельную колонку `declared_signing_time`, а `signed_at` остаётся серверным временем приёма.
  - **Цепочка до аккредитованного УЦ не проверяется** (нужен доверенный список Минцифры) — это честно написано в протоколе подписания и здесь. Проверяется криптографическая целостность + срок действия.
- **V34:** к `signature_requests` — `signature_data` (base64-контейнер, ~5–10 КБ: держим в БД, а не в файловом хранилище, чтобы не тянуть в `practice` крипто-файлы `document`), `signature_file_name`, `signature_algorithm`, `certificate_*`, `declared_signing_time` + partial-index `(expires_at) WHERE status='PENDING'` под sweep. Прогнано на живом pg16.
- **Запрет подмены способа подписи:** запрос, созданный как `SIMPLE`, нельзя закрыть контейнером и наоборот (409 `SIGNATURE_PROVIDER_MISMATCH`) — иначе «квалифицированная» подпись сводилась бы к галочке в портале. Хеш документа сверяется перед обоими способами (`DOCUMENT_MODIFIED`), размер `.sig` ограничен 512 КБ до разбора.
- **Протокол подписания (PDF)** — `SignatureProtocolPdfWriter` (DejaVuSans, как счета): документ + SHA-256, вид подписи со ссылкой на ст. 5/6 63-ФЗ, подписант, время, для простой ЭП — IP/браузер, для УКЭП — блок сертификата и алгоритм. Доступен и юристу (`GET /api/ai/cases/{caseId}/signatures/{id}/protocol`), и клиенту в портале; юрист может выгрузить сам контейнер (`/signature-file`). Только для статуса `SIGNED` (409 `SIGNATURE_NOT_SIGNED`).
- **Жизненный цикл закрыт:** `SignatureExpiryService.sweepExpired` (`@Scheduled` 03:25 UTC + `@SchedulerLock`) переводит просроченные `PENDING` → `EXPIRED`; раньше `EXPIRED` существовал только как вычисляемый статус в ответе. Конфиг `SIGNATURE_EXPIRY_CRON`.
- **Уведомления — через существующий канал сообщений по делу** (`CaseMessageService.postSystemMessage` → outbox → Kafka → push/Telegram/email): клиенту приходит «запрос на подпись документа X», юристу — «подписано ЭП/КЭП» или «отклонено: причина». Новый топик не заводили: получатели, prefs и фан-аут уже решены в п.9.
- **Диадок остаётся Noop-заглушкой** (`DIADOC_API_KEY` пуст → 503 `SIGNATURE_PROVIDER_UNAVAILABLE`). Сознательно: боевая интеграция требует договора с Контуром, ящика организации и protobuf-SDK, а юридический результат для пилота тот же — УКЭП по 63-ФЗ уже поддержана локально. Интерфейс `ExternalSignatureProvider` под неё сохранён.
- Фронт: выбор вида подписи при отправке (простая / КЭП), в портале для `DETACHED_CMS` — загрузка `.sig` вместо ФИО+галочки, карточка сертификата и кнопки «Протокол подписания (PDF)» / «Скачать .sig», i18n RU/EN.
- Тесты: `DetachedCmsVerifierTest` (6, включая подпись чужого содержимого и просроченный сертификат), `SignatureServiceTest` (17), `SignatureExpiryServiceTest` (2), обновлён `PortalSignatureServiceTest`. Весь ai-service: 240 зелёных, ModularityTests зелёные, фронт `tsc && vite build` + vitest чистые.
- ⚠️ Проверка на реальном ГОСТ-контейнере (Госключ/КриптоАРМ) — на тебе: локально ГОСТ-ключей нет, тесты используют RSA. Формат разбора от алгоритма не зависит.

Trade-off: сложная интеграция (криптопровайдеры), но удерживает юриста внутри платформы до финала.

---

## 6. Гибридный поиск (BM25 + вектор) + reranker — КАЧЕСТВО RAG · ✅ СДЕЛАНО
Было: pgvector cosine top-K, `max-distance`, legislation-boost в SQL. Стало: **вектор ∪ FTS → RRF → LLM-реранкер**, весь пайплайн — новый пакет `document/internal/search` (модуль `document`, границы Modulith не менялись).

- **V31:** `document_chunks.content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('russian'::regconfig, content)) STORED` + GIN. Генерируемая колонка = не нужен ни триггер, ни бэкфилл (ALTER пересчитывает существующие строки).
- **Два источника, один фильтр:** `VectorChunkSearchRepository` (cosine ≤ `max-distance`, score = 1 − distance) и `LexicalChunkSearchRepository` (`websearch_to_tsquery('russian', :q)` + `ts_rank_cd`; websearch — не падает на произвольном вводе юриста). Общие предикаты видимости (superseded / CHAT_ATTACHMENT / scope) — в `ChunkSearchSql`, чтобы источники не разъезжались. Разделение «база знаний vs дело» переехало из двух методов-близнецов в **`ChunkSearchScope`** (`knowledgeBase()` / `forCase(id)`), SQL один.
- **Слияние — `ReciprocalRankFusion`** (чистая функция, без Spring): `score = Σ weight / (k + rank)`. Ранги, а не сырые скоры — cosine-distance и ts_rank несравнимы по шкале. Чанк, найденный обоими источниками, поднимается наверх. **Legislation-boost переехал из SQL в фьюжн** (`document.search.legislation-boost` = 0.005 в RRF-шкале, где 1-е место ≈ 0.016): near-ties выигрывает НПА, явно более релевантный фрагмент — не перебивается.
- **Реранкер:** `Reranker` (порт) → `LlmReranker` (дешёвая модель через `llm-service`, профиль **`rerank`** — новый `OPENAI_RERANK_MODEL` рядом с `guard`; оценка 0–10 на фрагмент, ответ = JSON-массив) или `PassThroughReranker` (порядок RRF), выбор — в `RerankerConfig` по `document.search.rerank.enabled`. Парсинг ответа вынесен в `RerankScoreParser` (терпит markdown-фенсы и болтовню вокруг JSON). **fail-open по умолчанию:** модель упала/вернула мусор → отдаём порядок RRF, а не 503; неоценённые кандидаты сохраняют позицию фьюжна.
- **Деградация:** FTS-запрос упал → `HybridSearchService` логирует WARN и продолжает на одном векторе (поиск не должен падать целиком из-за одного источника). `RetrievedChunk.distance` → `score` (выше = лучше; distance после фьюжна не имеет смысла, потребители его не читали).
- Конфиг: `document.search.*` (все `RAG_*` env в `.env.example`). Тесты: `ReciprocalRankFusionTest` (8), `HybridSearchServiceTest` (7), `LlmRerankerTest` (7), `RerankScoreParserTest` (5), `OpenAiEngineTest` (+1 на профиль rerank). Миграция и оба native-запроса прогнаны на живом pg16.
- ⚠️ Ручной прогон на реальном корпусе (сравнить выдачу до/после на 10–20 юридических запросах) — на тебе; конфиг вынесен в env, чтобы крутить веса без пересборки.

Trade-off: главный дифференциатор legal-AI — точность ответа со ссылками. У тебя уже есть citation-check и guard.

---

## 3. CI/CD + staging — БЕЗОПАСНЫЕ РЕЛИЗЫ · ✅ СДЕЛАНО
Было: `deploy.sh` (ручной, единый VPS), ModularityTests как gate. Стало: полный пайплайн PR → merge → GHCR → staging → smoke → (ручной аппрув) → prod, с авто-откатом. `deploy.sh` оставлен как fallback.
- **Workflows:** `_verify.yml` (reusable — единственное определение зелёной сборки: `mvn -B verify` + Modulith-гейт + frontend lint/vitest/build + shellcheck/bash-тесты), `ci.yml` (PR/не-main + валидация сборки образов), `release.yml` (push main: publish → deploy-staging → deploy-production за environment-аппрувом), `rollback.yml` (ручной).
- **Образы:** `docker buildx bake` (`docker-bake.hcl`) — 5 бэкендов из общего maven-stage компилятся один раз; тег = `github.sha` + `latest`; реестр GHCR. Pull-based деплой через оверлей `docker-compose.images.yml`.
- **Модульный bash `scripts/cicd/`** (SRP + юнит-тесты): `lib/{log,health,deploy_state,registry}.sh` + энтрипоинты `smoke-test.sh`/`deploy-remote.sh` (авто-откат на previous при провале smoke/health)/`rollback-remote.sh`. Композитные actions `setup-backend`/`ssh-deploy`. Единый источник правды сервисов проверяется `tests/consistency.sh`.
- Настройка окружений/секретов — `docs/CICD.md`.

Trade-off: ручной деплой на 1 VPS = релиз это риск даунтайма. Дёшево, окупается на первом откате.

---

## 7. Централизованные логи + Sentry — НАБЛЮДАЕМОСТЬ · ✅ СДЕЛАНО
Было: Prometheus/Grafana/Tempo, сквозной `X-Request-Id`/MDC; логи — только `docker logs`, `errorReporter.ts` = no-op.

- **Общий Maven-модуль `pravoos-observability`** (зависимость всех 5 сервисов; в Dockerfile добавлены `COPY` pom+src): единый logback-фрагмент `logback/pravoos-base.xml` (профиль `docker` → JSON в stdout через `logstash-logback-encoder`, иначе — человекочитаемый формат + файлы) — `logback-spring.xml` каждого сервиса сжался до 4 property + include. Плюс автоконфигурация Sentry (`ObservabilityAutoConfiguration`): `PiiScrubber` (email/JWT/Bearer/телефон), `SentryEventEnricher` (теги `service`/`requestId`/`traceId`, чистка cookies и чувствительных заголовков), `ServerErrorOnlyPolicy` (4xx не репортим — иначе Sentry завалит ожидаемыми 403/422). Общий `config/pravoos-observability.yml` подключается одной строкой `spring.config.import` — конфиг Sentry не дублируется по сервисам.
- **Фильтр 4xx** опирается на `HttpStatusCarrier` (новый интерфейс в `pravoos-common`), который реализует `PravoosException` в ai/user/llm.
- **Loki (3.4.2) + Grafana Alloy (v1.7.5)** в compose: Alloy читает docker-логи (discovery по compose-лейблам), парсит JSON только у 5 наших сервисов (`stage.match`), кладёт лейблы `app`/`level` + structured metadata `requestId`/`traceId`/`logger`/`thread`. Retention 14 дн. Loki-ruler → тот же alertmanager → Telegram: `HighErrorLogRate`, `ErrorLogBurst`, `ServiceLogsSilent` (сервис молчит 10 мин).
- **Корреляция:** датасорс Loki с derived field `traceId` → Tempo, Tempo `tracesToLogsV2` → Loki. Дашборд «PravoOS — Логи».
- **Фронт:** `src/lib/observability/` вместо `errorReporter.ts` — `ErrorSink`-абстракция (`sentrySink` на `@sentry/react`, `beaconSink` = старый `VITE_ERROR_REPORT_URL`), `ReportThrottle` (кап 25/сессию + дедуп 10с), `scrubPii`. Появился vitest (8 тестов).
- **Гейтинг:** пустые `SENTRY_DSN`/`VITE_SENTRY_DSN` → SDK no-op (тот же паттерн, что `ARBITR_API_KEY`). `SENTRY_INGEST_ORIGIN` подставляется в CSP `connect-src` (`render-nginx.sh`), иначе браузер режет отправку.
- Тесты: `PiiScrubberTest` (6), `SentryEventEnricherTest` (4), `ServerErrorOnlyPolicyTest` (4), фронт — `scrub.test.ts` (5), `throttle.test.ts` (3).

Trade-off: было — прод-баг = grep по контейнерам вслепую. Стало — поиск по `requestId` через все сервисы + алерт в Telegram до жалобы юриста.

---

## 2. Соответствие 152-ФЗ (ПДн) — ЛИЦЕНЗИЯ НА СУЩЕСТВОВАНИЕ В LEGAL-TECH РФ
Есть: шифрование файлов at-rest (C7), audit trail (C8), бэкапы. Нет: согласий, шифрования PII в БД (email/phone/ФИО клиентов plaintext), выгрузки/удаления по запросу субъекта, фиксации локализации в РФ.
- Согласие при регистрации/заведении клиента (версионирование), шифрование PII-колонок, endpoint «выгрузить/удалить мои данные».

```bash
grep -rn "FileCryptoService\|FILE_ENCRYPTION_KEY" ai-service/src/main/java   # образец крипто для PII-колонок
# проверить юрисдикцию VPS (сейчас 77.110.116.203) — должен быть РФ
```
Trade-off: для legal-tech в РФ не «фича», а обязательное. На enterprise/тендерах этим давят конкуренты.

---

## 8. Отказоустойчивость — единый VPS = single point of failure
Есть: 1 VPS, всё в одном Compose, бэкапы. Нет: реплики Postgres, теста восстановления, резерва.
- Минимум для пилота: **managed Postgres с репликой** (Yandex Cloud/Selectel) вместо контейнера + drill восстановления бэкапа.

```bash
cat scripts/backup.sh            # есть pg_dumpall+mongodump+GPG; проверить, что restore реально работает
# drill: развернуть бэкап в чистую БД и сверить
gpg --batch --decrypt --passphrase "$BACKUP_ENCRYPTION_KEY" backup-XXX.tar.gz.gpg | tar xz
```
Trade-off: пока юзеров мало — терпимо, но первая потеря данных = конец в legal. Начать с реплики БД + restore-теста.

---

## 9. Мобильный доступ (PWA) + push — УДЕРЖАНИЕ. ✅ СДЕЛАНО
Было: React 18 SPA, уведомления только Telegram/email. Стало: installable PWA + Web Push как полноценный третий канал уведомлений.

**Хранение подписок — новый Modulith-модуль `push` в user-service** (`allowedDependencies = {shared, identity :: model/enums/repository}`), рядом с `billing`/`collaboration`. V22: `push_subscriptions` (`endpoint` UNIQUE, ключи `p256dh`/`auth`, `user_agent`, FK на `users` с ON DELETE CASCADE) + колонки `users.login_alert_push` / `users.case_message_push` (дефолт TRUE) — push встроен в существующую матрицу prefs, а не отдельной сущностью. `PushSubscriptionService`: upsert по `endpoint` (перерегистрация того же устройства не плодит строк, endpoint переезжает на нового владельца при смене аккаунта в браузере), лимит 10 устройств на юзера с вытеснением самых старых, `prune(endpoint)` для протухших. Публично: `GET /api/user/push/config` (VAPID public-key + флаг `configured`), `POST /api/user/push/subscriptions`, `POST /api/user/push/subscriptions/remove`. Внутренне (`X-Internal-Secret`): `GET /internal/push/subscriptions/{userId}`, `POST /internal/push/subscriptions/prune`.

**Доставка — notification-service** (владелец каналов, как и для Telegram). Библиотека `nl.martijndwars:web-push` (VAPID + RFC 8291, BouncyCastle-провайдер). Гейтинг по ключам: пусто → `NoopWebPushSender` (подписки принимаются, отправки нет — тот же паттерн, что Noop-провайдер арбитража). Приватный VAPID-ключ забланкован в остальных сервисах compose.
- **Рефактор фан-аута:** консьюмеры больше не дергают Telegram напрямую — появился `NotificationDispatcher` (оркестрация каналов), `TelegramNotificationService` сведён к чистому Telegram (`sendDeadline`/`sendHearingUpdate`/`sendCaseMessage`/`sendNewLogin` → boolean «доставлено»), email-фолбэк вынесен в `DeadlineEmailFallbackService`, тексты push собирает `PushMessageFactory`. Добавить 4-й канал = один бин, консьюмеры не трогаем.
- **Выбор канала:** дедлайн/заседание → push всем устройствам юриста + Telegram; email-фолбэк только если не сработали ОБА (раньше — если нет Telegram). Сообщения по делу → prefs получателя приходят из `CaseMessageNotificationResult` (расширен `recipientUserId` + `pushEnabled`), ссылка в push разная для юриста (`/cases/:id`) и клиента портала (`/portal/cases/:id`). Новый вход → флаги `telegramEnabled`/`pushEnabled` едут прямо в `NewLoginKafkaPayload` (событие теперь публикуется, если включён любой из каналов).
- **Протухшие подписки:** 404/410 от push-сервиса → `PushNotificationService` зовёт prune в user-service, БД не копит мусор. Дедуп повторной доставки — по `tag` в самом уведомлении (браузер схлопывает).

**Фронт:** `vite-plugin-pwa` (`injectManifest`), свой `src/sw.ts` (precache app-shell, network-first для GET `/api/`, SWR для картинок, обработчики `push` и `notificationclick` с фокусом уже открытой вкладки), манифест + иконки 192/512/maskable/badge, `/sw.js` и манифест отдаются с `no-cache` в nginx. Слой PWA изолирован: `src/pwa/` (`registerServiceWorker`, `pushClient`, `vapid`), хук `usePushNotifications` (состояния `unsupported`/`not-configured`/`blocked`/`subscribed`), карточка «Push на этом устройстве» + канал «Push в браузере» в настройках уведомлений.

Тесты: `PushSubscriptionServiceTest` (7), `NotificationDispatcherTest` (7), `PushNotificationServiceTest` (6), `PushMessageFactoryTest` (5), `vapid.test.ts` (4) + обновлены `AuthServiceTest`/`UserServiceTest`/`TelegramNotificationServiceTest`/`NewLoginConsumerTest`.

```bash
npx web-push generate-vapid-keys        # → VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY в .env
```
Осталось на будущее: in-app центр уведомлений (сейчас есть раздел «Сообщения»), push по счетам/оплате биллинга.

---

## 10. Email-синк переписки + расширение судов (СОЮ/ГАС «Правосудие»)
Есть: `ClientContact` (ручная история), КАД.Арбитр (parser-api.com). Нет: авто-логирования email, судов общей юрисдикции.
- (а) привязка ящика юриста → авто-привязка писем к делу/клиенту; (б) коннектор к ГАС «Правосудие»/Casebook/Sudact.

```bash
grep -rn "ArbitrPollingService\|details_by_number" ai-service/src/main/java   # образец коннектора для СОЮ
```
Trade-off: email-синк резко повышает «залипание» (вся переписка внутри). Расширение судов — прямое преимущество над арбитраж-only трекерами.

---

## Быстрый старт (что запустить прямо сейчас)
```bash
# 1. убедиться, что локалка собирается и тесты зелёные
export JAVA_HOME=/Users/ilia/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home
mvn -B -q verify                                  # весь монорепо: модульные + Modulith-gate
# 2. поднять окружение
docker compose -f docker-compose.yml -f docker-compose.override.yml up -d --wait
docker compose ps                                 # проверить healthy
# 3. начать с пункта 1 (биллинг)
git checkout main && git pull && git checkout -b feat/billing
```
Первый заход рекомендую **пункт 1 (биллинг)** — у тебя уже есть квоты и роли/орги, надстройка ложится органично.

11. Не работает кнопка прикрепления медиа в чате AI
12. Сделали в настройках кнопочку с уведомленяими из чата а сам чат я не вижу видимо не добавили? Нужно сделать
13. Как работает календарь только отображает? как думаешь стоит ли добавлять возможность прямо в клеточку самому добавлять что-либо в режиме календаря
14. ✅ **СДЕЛАНО.** Eureka (service discovery): модуль `discovery-server` (:8761, self-preservation off — флот маленький). Клиентская часть — общий модуль `pravoos-cloud` (`config/pravoos-cloud.yml` подключается через `spring.config.import` рядом с observability). Gateway ходит в `lb://user-service` / `lb://ai-service`, ai/notification — в `lb://llm-service` / `lb://user-service`. Блокирующие `RestClient`'ы балансируются через `@LoadBalanced RestClient.Builder` (autoconfig в `pravoos-cloud`, только SERVLET); `DiscoveryAwareRestClients.builderFor()` берёт балансируемый билдер **только** для `lb://`, обычный URL идёт напрямую — статический адрес остаётся рабочим fallback'ом. `EUREKA_ENABLED` по умолчанию `false` (локальный `spring-boot:run` не требует реестра), в compose — `true`. Статических `host:port` в compose больше нет.
15. ✅ **СДЕЛАНО.** Config Server: модуль `config-server` (:8888, native backend, `config-repo/` запечён в образ — иммутабельный деплой, без volume). `application.yml` — общее для всех, `<service>.yml` — по сервису; **секретов нет** (env контейнера приоритетнее). Клиенты — `spring.config.import: optional:configserver:...` с `fail-fast` + ретраи (8 попыток). `CONFIG_SERVER_ENABLED` по умолчанию `false`, в compose — `true`; при недоступном сервере с `fail-fast=true` сервис не стартует (проверено). Оба новых сервиса — в bake/registry/images-overlay (consistency-тест зелёный), prometheus, alloy, loki-rules и дашборде логов.
16. ✅ **СДЕЛАНО.** Антивирус и защита загрузок. Было: `MalwareScanClient` (сокет к clamd, INSTREAM, fail-closed по конфигу) + магические байты PDF/DOCX. Стало — три слоя.
    - **Слой clamd:** сокетная работа вынесена в `ClamdClient` (`scanStream` + `version`, чтение ответа до NUL, а не одним `read`), `MalwareScanClient` остался чистой политикой. `docker/clamav/clamd.conf` монтируется в контейнер: `StreamMaxLength 64M` (обязан быть больше лимита загрузки, иначе clamd рвёт соединение на большом файле и fail-closed отклоняет легитимный документ), `AlertOLE2Macros`/`AlertEncryptedArchive`/`AlertBrokenExecutables`, `ConcurrentDatabaseReload no` (память VPS). `AlertEncryptedDoc` намеренно **off** — PDF под паролем у юристов легитимен.
    - **Свежесть сигнатур:** `freshclam.conf` + `FRESHCLAM_CHECKS=24` (ScriptedUpdates, `NotifyClamd` → clamd подхватывает базы без рестарта). Со стороны приложения — `ClamAvSignatureMonitor`: раз в час спрашивает у clamd `VERSION`, `ClamdVersionParser` достаёт дату базы, возраст пишется в gauge `pravoos.document.malware.signature.age.days`. База старше `CLAMAV_MAX_SIGNATURE_AGE_DAYS` (7) → при fail-closed загрузки отклоняются **до** обращения к сканеру (устаревший сканер = ложное чувство защиты). Недоступный clamd = возраст «неизвестен», а не «просрочен» — этот случай и так ловит fail-closed при самом скане. Мониторинг per-instance, без `@SchedulerLock`: каждый инстанс проверяет свой сканер.
    - **Второй слой без второго движка — `UploadContentInspector`** (метрика `pravoos.document.upload.rejected{reason}`): лимит размера (`DOCUMENT_MAX_FILE_BYTES`, 50 МБ — проверяется по `MultipartFile.getSize()` до чтения в память), активное содержимое в PDF (`/JavaScript`, `/Launch`, `/EmbeddedFile`, `/RichMedia` — короткие маркеры вроде `/JS` не берём: в сжатых потоках 3 байта совпадают случайно; `/XFA` не берём — это легитимные формы ФНС/судов), в DOCX — VBA-макросы (`vbaProject.bin`), исполняемые вложения, path-traversal в именах entry и zip-бомбы (предел распакованного объёма + степень сжатия). Отключается флагом `DOCUMENT_BLOCK_ACTIVE_CONTENT=false`. Новые исключения: `UnsafeFileContentException` (422 `UNSAFE_FILE_CONTENT`), `FileTooLargeException` (413 `FILE_TOO_LARGE`).
    - Порядок в `DocumentService.validateAndScan`: размер → квота → магические байты → инспектор → clamd (дешёвые проверки раньше дорогих).
    - Тесты: `UploadContentInspectorTest` (11), `MalwareScanClientTest` (8), `ClamAvSignatureMonitorTest` (5), `ClamdVersionParserTest` (5). Весь ai-service: 226 зелёных, ModularityTests зелёные.
    - ⚠️ Второй движок/sandbox сознательно не заводили: +контейнер и +лицензия ради маргинального выигрыша на пилоте. Образ `clamav/clamav:1.4` — **только amd64**, на arm64-маке контейнер не поднимается (прод-VPS amd64); конфиг проверен через `clamconf` под `--platform linux/amd64`.
17. Локализация (i18n) — сейчас UI только на русском. Нужно: (а) вынести строки фронта в словари (`react-i18next`, namespaces по разделам, RU как базовый), (б) авто-детект языка по адресу — аккуратно, по приоритету: сохранённый выбор юзера (localStorage/prefs) → `navigator.language` браузера → опц. гео по IP (только как подсказка дефолта, не жёстко; через тот же паттерн гейтинга по env-ключу — пусто → пропускаем гео-шаг), (в) ручная смена языка (переключатель в шапке/настройках, сохранение выбора в prefs юзера, чтобы держался между устройствами). Начать с RU→EN, инфраструктуру сразу делать на N языков. Серверные тексты (письма/уведомления/PDF-счета) — отдельная фаза: язык из prefs юзера, дефолт RU.
