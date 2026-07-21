<div align="center">

# ⚖️ PravoOS

**Legal AI SaaS для юристов**

AI-ассистент, который отвечает на юридические вопросы по загруженной базе практики (RAG),
ведёт дела и клиентов, следит за дедлайнами и синхронизируется с КАД.Арбитр.

Java 21 · Spring Boot 3.3 · Spring Cloud Gateway · Spring Modulith · PostgreSQL + pgvector · MongoDB · Kafka · Redis · React 18

</div>

---

## О продукте

B2B-платформа для юристов и юрфирм. Вход — заявочный: юрист подаёт заявку, администратор одобряет её через веб-интерфейс или прямо из Telegram.

**Роли:**

| Роль | Возможности |
|------|-------------|
| `LAWYER` | вопросы к AI, ведение дел и клиентов, дедлайны, драфты, экспорт, организации |
| `ADMIN` | одобрение заявок, база знаний (документы), статистика |
| `CLIENT` | клиентский портал по инвайту от юриста *(в работе)* |

---

## Стек

| Слой | Технологии |
|------|-----------|
| **Backend** | Java 21, Spring Boot 3.3.5, Spring Cloud 2023.0.3, Spring Modulith |
| **Gateway** | Spring Cloud Gateway (WebFlux / reactive) |
| **Auth** | JWT **RS256** (JJWT 0.12.6), BCrypt-12, TOTP 2FA, refresh-ротация |
| **Хранилища** | PostgreSQL + pgvector · MongoDB · Redis |
| **Миграции** | Flyway |
| **Шина** | Apache Kafka (Transactional Outbox + DLT) |
| **LLM** | OpenAI `gpt-4o-mini` + `text-embedding-3-small` (swappable через `LlmClient`) |
| **Парсинг** | Apache PDFBox 3.x, Apache POI 5.x |
| **Уведомления** | Telegram Bot (telegrambots 6.9), Resend (email) |
| **Frontend** | React 18, TypeScript, Vite, Tailwind 3, Zustand, React Query |
| **Observability** | Prometheus, Grafana, Tempo (OTel-трейсы), **Loki + Alloy** (централизованные логи), **Sentry/GlitchTip** (ошибки фронта и бэка) |
| **Безопасность** | ClamAV (антивирус), AES-256-GCM (шифрование файлов at-rest) |
| **Инфра** | Docker Compose, Nginx, Let's Encrypt, Cloudflare |

---

## Архитектура

Монорепо (Maven multi-module, `com.pravoos`) → 4 прикладных сервиса + 2 инфраструктурных + 4 общих библиотеки.
Внутри `user-service` и `ai-service` — модульная структура на **Spring Modulith** с проверкой границ в CI.

```
                            ┌─────────────┐
   Browser  ──── HTTPS ────▶│ api-gateway │  :8080  (WebFlux, reactive)
                            │  JWT verify │  rate-limit · denylist · header-sanitize
                            └──────┬──────┘
             ┌─────────────────────┼─────────────────────┐
             ▼                     ▼                     ▼
   /api/auth  /api/admin    /api/ai/**            (события Kafka)
   /api/user  ──▶                  ──▶                   ──▶
   ┌───────────────┐      ┌───────────────┐      ┌──────────────────────┐
   │ user-service  │:8081 │  ai-service   │:8082 │ notification-service │:8083
   │               │      │               │      │                      │
   │ PostgreSQL    │      │ PostgreSQL    │      │ stateless            │
   │ pravoos_users │      │ +pgvector     │      │ Kafka → Telegram/Email│
   │               │      │ MongoDB (chat)│      │                      │
   └───────────────┘      └───────────────┘      └──────────────────────┘
```

### Сервисы

| Сервис | Порт | Роль | Хранилище |
|--------|------|------|-----------|
| **api-gateway** | 8080 | единая точка входа, верификация JWT, rate-limit, denylist, санитайзинг заголовков | Redis |
| **user-service** | 8081 | auth, заявки, пользователи, 2FA, сессии, организации, инвайты | PostgreSQL `pravoos_users` |
| **ai-service** | 8082 | RAG, LLM, дела, клиенты, документы, дедлайны, КАД.Арбитр | PostgreSQL `pravoos_ai` + pgvector · MongoDB `pravoos_chat` |
| **notification-service** | 8083 | Kafka-consumer + Telegram-бот + email-fallback | stateless |
| **llm-service** | 8084 | единственный шлюз к OpenAI (internal-only, не в gateway) | stateless |
| **discovery-server** | 8761 | Eureka Server — реестр сервисов | in-memory |
| **config-server** | 8888 | Spring Cloud Config Server — общая конфигурация | `config-repo` в образе |

### Service discovery и централизованная конфигурация

**Eureka.** Прикладные сервисы регистрируются в `discovery-server` и находят друг друга по имени, а не по `host:port`.
Адрес вида `lb://<service-id>` (`USER_SERVICE_URL`, `AI_SERVICE_URL`, `LLM_SERVICE_BASE_URL`, `USER_SERVICE_BASE_URL`)
резолвится Spring Cloud LoadBalancer'ом: в gateway — штатно, в блокирующих клиентах — через `@LoadBalanced RestClient.Builder`.
`DiscoveryAwareRestClients.builderFor(...)` выбирает балансируемый билдер только для `lb://`, обычный http-URL идёт напрямую —
поэтому статический адрес остаётся рабочим escape hatch'ем, если Eureka выключена.

**Config Server.** `config-server` раздаёт `config-repo/` (native backend, запечён в образ): `application.yml` — общее для всех,
`<service>.yml` — по сервису. Секретов там нет: они приходят из env контейнера и имеют приоритет.
Клиенты подключаются через `spring.config.import: optional:configserver:...` с `fail-fast` и ретраями.

Оба механизма выключены по умолчанию (`EUREKA_ENABLED` / `CONFIG_SERVER_ENABLED` = `false`), чтобы одиночный
`mvn spring-boot:run` работал без поднятой инфраструктуры. В `docker-compose.yml` оба включены жёстко.

### Общие модули

- **`pravoos-common`** — `JwtVerifier` (RSA), нормализация телефонов, guard'ы конфигурации.
- **`pravoos-common-web`** — `RequestIdFilter`, `SecurityUtils`, `OrgContext`.
- **`pravoos-observability`** — общий logback + Sentry.
- **`pravoos-cloud`** — Eureka-клиент, клиент Config Server, `config/pravoos-cloud.yml`, `@LoadBalanced RestClient.Builder`.

### Внутренняя модульность (Spring Modulith)

Границы между пакетами внутри сервиса проверяются `ModularityTests.verify()` — **это CI-gate**, сборка падает при обходе границы. Наружу торчит только `api/` (`@NamedInterface`), внутренности спрятаны в `internal/`.

- **user-service:** `identity` → `collaboration` → `registration` (+ `shared` OPEN)
- **ai-service:** `shared` ← `llm` ← `document`; `core` ← (`llm::api`, `document::api`); `practice` ← (`core::api`, `document::api`)

Кросс-модульный вызов — только через `api`-фасад или событие. Обратная зависимость — через SPI, объявленный в потребителе.

---

## RAG Pipeline

```
Загрузка PDF/DOCX/TXT
   └─▶ антивирус (ClamAV) ─▶ шифрование AES-256-GCM ─▶ DocumentParser (текст)
        └─▶ TextChunker (512 слов, overlap 50)
             └─▶ text-embedding-3-small ─▶ document_chunks.embedding vector(1536), HNSW

Вопрос юриста
   └─▶ LegalDomainGuard (юр.запрос?) ─▶ embed ─▶ cosine top-K (порог по distance)
        └─▶ промпт = система + top-K чанков + история + вопрос
             └─▶ gpt-4o-mini ─▶ ответ
```

Устойчивость к внешним сбоям: retry+backoff+jitter, bulkhead (semaphore), дневная квота запросов/токенов, token-бюджет контекста, кэш guard'а. Детерминированный RAG (embed → top-K → 1 промпт), а не агент-в-цикле — предсказуемая стоимость и латентность.

---

## Ключевые возможности

- **Дела** — CRUD, канбан-статусы, дедлайны с напоминаниями (7/3/1 день), задачи-чеклисты, экспорт (DOCX/PDF с кириллицей).
- **Workflows** — структурированные сценарии (Bankruptcy, 10 шаблонов), драфты документов (5 типов), шаблоны с подстановкой `{{...}}`.
- **Клиенты (CRM)** — карточки, история контактов, каскадное удаление.
- **Организации** — фирмы/команды с шарингом дел, роли `OWNER`/`MANAGER`/`MEMBER`, инвайты по email.
- **КАД.Арбитр** — синхронизация дел через коммерческий API (parser-api.com), слушания в календаре.
- **Поиск** — по делам/беседам/документам + полнотекстовый внутри файлов (pg_trgm).
- **Dashboard** — pipeline, активные дела, задачи, дедлайны одним запросом.

---

## Безопасность

- **JWT RS256** — user-service подписывает приватным ключом, gateway и сервисы верифицируют публичным.
- **Refresh-токены** — opaque, httpOnly-cookie, ротация с reuse-detection, аудит сессий/устройств.
- **2FA (TOTP)** — RFC 6238 без внешних либ, обязателен для админов.
- **Парольная политика** — чёрный список + HIBP k-anonymity.
- **Brute-force / rate-limit** — Redis: per-email lockout, per-IP лимиты, denylist токенов на revoke.
- **Документы** — антивирус ClamAV, шифрование at-rest AES-256-GCM, audit trail доступа, secure-заголовки.
- **Transactional Outbox** — атомарность БД ↔ Kafka, идемпотентность, DLT на ошибках.
- **Origin lockdown** — ufw только с диапазонов Cloudflare, опциональный mTLS (Authenticated Origin Pull).

---

## Быстрый старт (локальная разработка)

```bash
cp .env.example .env
# заполни .env (см. ниже)

docker compose up -d
cd frontend && npm run dev
```

- API: `http://localhost:8080`
- Frontend: `http://localhost:3000`

### Обязательные переменные `.env`

| Переменная | Описание |
|------------|----------|
| `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` | RSA-пара (base64 DER), источник — `jwt.pem` |
| `OPENAI_API_KEY` | ключ OpenAI |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | первый администратор |
| `TELEGRAM_BOT_TOKEN` | токен Telegram-бота |
| `INTERNAL_SERVICE_SECRET` | секрет для `/internal/**` между сервисами |
| `REDIS_PASSWORD` | пароль Redis (обязателен в profile `docker`) |
| `FILE_ENCRYPTION_KEY` | base64, ровно 32 байта — шифрование документов |
| `RESEND_API_KEY` | отправка email |
| `ARBITR_API_KEY` | КАД.Арбитр (пусто → интеграция выключена) |

Генерация RSA-пары и ключей:
```bash
openssl genpkey -algorithm RSA -out jwt.pem -pkeyopt rsa_keygen_bits:2048
openssl rand -base64 32   # FILE_ENCRYPTION_KEY
```

---

## Инфраструктура

`docker compose` поднимает полный стек:

**Приложения:** api-gateway · user-service · ai-service · llm-service · notification-service · frontend (nginx)
**Платформа:** discovery-server (Eureka) · config-server
**Данные:** postgres (pgvector) · mongodb · redis · kafka
**Безопасность:** clamav
**Observability:** prometheus · grafana · tempo · loki · alloy · alertmanager

Compose соблюдает `depends_on: condition: service_healthy` — `up -d --wait` дожидается готовности зависимостей.

### Наблюдаемость

Три сигнала связаны между собой через `traceId`/`requestId` (сквозной `X-Request-Id` + MDC):

| Сигнал | Куда | Как смотреть |
|--------|------|--------------|
| Метрики | Prometheus → Grafana | дашборд «PravoOS — Overview», алерты `docker/prometheus/alerts.yml` |
| Трейсы | OTLP → Tempo | из лога — ссылка «Открыть трейс» по `traceId` |
| Логи | stdout (JSON) → Alloy → Loki | дашборд «PravoOS — Логи», Explore → Loki; из трейса — переход в логи |
| Ошибки | Sentry / GlitchTip | бэкенд: ERROR-логи и необработанные исключения; фронт: `ErrorBoundary` + `window.onerror` |

- **Логи.** В профиле `docker` сервисы пишут JSON (`logstash-logback-encoder`) в stdout; `alloy` читает docker-логи и грузит в `loki` (лейблы `app`/`level`, structured metadata `requestId`/`traceId`/`logger`). Retention — 14 дней. Loki-ruler шлёт алерты по всплеску ошибок в тот же alertmanager → Telegram. Вне docker (локальный запуск) формат остаётся человекочитаемым + файлы в `logs/`.
- **Ошибки.** Общий модуль `pravoos-observability` (зависимость всех сервисов) поднимает Sentry: PII вычищается (`PiiScrubber`: email/JWT/Bearer/телефон), 4xx не репортятся (`ServerErrorOnlyPolicy`), в событие проставляются теги `service`/`requestId`/`traceId`. Пустой `SENTRY_DSN` → SDK no-op, ничего не отправляется. DSN совместим с self-hosted GlitchTip.
- **Фронт.** `src/lib/observability` — сборщики (`sentrySink`, `beaconSink`), троттлинг и очистка PII; включается через `VITE_SENTRY_DSN` (пусто → выключено). Хост ingest'а нужно указать в `SENTRY_INGEST_ORIGIN` — он попадает в CSP `connect-src`.

### Продакшн

VPS + Docker Compose + Nginx + Let's Encrypt, за Cloudflare.

```bash
git clone <repo> /opt/pravoos && cd /opt/pravoos
sudo ./scripts/setup-server.sh    # Docker, ufw
cp .env.example .env && nano .env
./scripts/deploy.sh               # первичный bootstrap: сборка + SSL + запуск

# ручное обновление (fallback)
git pull && ./scripts/deploy.sh
```

Бэкапы: `scripts/backup.sh` (pg_dumpall + mongodump → GPG AES-256 → Telegram, crontab 02:00).

### CI/CD

После bootstrap регулярные релизы едут автоматически: PR гоняет `mvn verify` + фронт + bash-тесты,
merge в `main` собирает образы в GHCR и катит на **prod после ручного аппрува**, со smoke-тестом и
авто-откатом (топология — один VPS, staging добавляется позже). Подробно — **[docs/CICD.md](docs/CICD.md)**.

---

## Kafka-события

| Topic | Producer → Consumer | Назначение |
|-------|---------------------|------------|
| `application.submitted` | user → notification | новая заявка юриста |
| `lawyer.deleted` | user → ai | каскадная очистка данных юриста |
| `case.deadline.approaching` | ai → notification | напоминание о дедлайне |
| `case.hearing.updated` | ai → notification | обновление слушания (КАД) |

Все события идут через Transactional Outbox; на ошибках консьюмера — `<topic>.DLT`.

---

## Структура репозитория

```
PravoOS/
├── pom.xml                    # parent POM (com.pravoos:pravoos-parent)
├── pravoos-common/            # JwtVerifier, утилиты, guard'ы
├── pravoos-common-web/        # RequestIdFilter, SecurityUtils, OrgContext
├── pravoos-observability/     # JSON-логи (общий logback) + Sentry (PII-скраб, фильтр 4xx)
├── pravoos-cloud/             # Eureka-клиент + Config-клиент + lb:// RestClient
├── discovery-server/          # Eureka Server                   :8761
├── config-server/             # Spring Cloud Config Server      :8888
├── api-gateway/               # Spring Cloud Gateway            :8080
├── user-service/              # auth, заявки, орги, 2FA         :8081
├── ai-service/                # RAG, дела, клиенты, документы   :8082
├── notification-service/      # Telegram + Kafka consumer       :8083
├── frontend/                  # React + TypeScript + Vite
├── docker/                    # nginx, prometheus, grafana, tempo
├── scripts/                   # deploy, backup, lockdown-origin
├── docker-compose.yml         # dev-стек
├── docker-compose.prod.yml
├── ARCHITECTURE.md
└── DEPLOY.md
```
