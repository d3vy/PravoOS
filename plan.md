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

## 4. Учёт времени и биллинг часов (time tracking → счета) — KILLER-ФИЧА ЮРФИРМ
Есть: CRM клиентов, дела, `case_tasks`, DOCX/PDF-экспорт (DejaVuSans). Нет: таймеров, ставок, счетов.
- `time_entries` (case/client/lawyer/duration/rate/billable), таймер в UI карточки дела, генерация счёта через существующий экспорт.
- Модуль: `practice` (ai-service) — рядом с делами, границы Modulith уже разрешены.

```bash
git checkout -b feat/time-tracking
ls ai-service/src/main/resources/db/migration/    # след. после V24 → V25__time_entries.sql
mvn verify -pl ai-service -am -B                   # ModularityTests не даст обойти границы
```
Trade-off: причина №1 для B2B-юрфирм платить (ядро Clio). Модель дело→клиент→юрист готова — это надстройка.

---

## 5. Электронная подпись (КЭП/УКЭП) + Диадок/Контур — замкнуть жизненный цикл документа
Есть: драфты, шаблоны, редлайн, экспорт. Нет: подписания — документ «выпадает» из системы.
- Интеграция Контур.Диадок / КриптоПро, либо простая e-sign в клиентском портале (клиент подтверждает документ).
- Гейтинг по env-ключу (как `ARBITR_API_KEY`): пусто → no-op провайдер.

```bash
git checkout -b feat/e-signature
# по образцу arbitr-провайдера (Noop при пустом ключе):
grep -rn "NoopArbitrCaseProvider\|ARBITR_API_KEY" ai-service/src/main/java
```
Trade-off: сложная интеграция (криптопровайдеры), но удерживает юриста внутри платформы до финала.

---

## 6. Гибридный поиск (BM25 + вектор) + reranker — КАЧЕСТВО RAG
Есть: pgvector cosine top-K, `max-distance`, legislation-boost, pg_trgm по файлам. Нет: гибрида и реранкинга — чистый вектор мажет по номерам статей.
- Postgres FTS (`tsvector`) ∪ vector → слить через RRF → reranker дешёвой моделью через `llm-service`.

```bash
git checkout -b feat/hybrid-search
grep -rn "VectorSearchRepository\|vector_cosine_ops\|CAST(:vec AS vector)" ai-service/src/main/java
# добавить tsvector-колонку + GIN-индекс миграцией V25/V26, править native-запрос в VectorSearchRepository
```
Trade-off: главный дифференциатор legal-AI — точность ответа со ссылками. У тебя уже есть citation-check и guard.

---

## 3. CI/CD + staging — БЕЗОПАСНЫЕ РЕЛИЗЫ
Есть: `deploy.sh` (ручной, единый VPS), ModularityTests как gate. Нет: pipeline, staging, автотестов на PR, rollback.
- GitHub Actions: на PR → `mvn verify` (модульные + Modulith) + сборка образов; на merge → staging → smoke → prod.

```bash
mkdir -p .github/workflows      # ci.yml: mvn -B verify на push/PR
# smoke-проверка после деплоя (пример):
curl -fsS https://<domain>/actuator/health || echo "DOWN"
# текущий деплой (ручной, оставить как fallback):
cat deploy.sh
```
Trade-off: ручной деплой на 1 VPS = релиз это риск даунтайма. Дёшево, окупается на первом откате.

---

## 7. Централизованные логи + Sentry — НАБЛЮДАЕМОСТЬ
Есть: Prometheus/Grafana/Tempo, сквозной `X-Request-Id`/MDC. Нет: агрегации логов (`errorReporter.ts` = no-op) и трекинга ошибок.
- Loki (минимальная добавка к стеку Grafana) + Sentry/GlitchTip (фронт+бэк). Корреляция по `requestId` заработает из коробки.

```bash
ls docker/tempo/                 # рядом положить docker/loki/loki.yaml, добавить сервис в compose
grep -rn "VITE_ERROR_REPORT_URL\|errorReporter" frontend/src   # включить реальный sink
```
Trade-off: сейчас прод-баг = grep по контейнерам вслепую. Резко снижает MTTR.

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

## 9. Мобильный доступ (PWA) + push — УДЕРЖАНИЕ
Есть: React 18 SPA, уведомления только Telegram. Нет: PWA, in-app и web-push.
- Installable PWA (манифест + service worker на Vite) + Web Push для дедлайнов/заседаний параллельно Telegram.

```bash
cd frontend && npm i -D vite-plugin-pwa   # манифест+SW; далее Web Push к дедлайнам
```
Trade-off: PWA закрывает 80% мобильного за 20% усилий, нативный app не нужен сразу.

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
