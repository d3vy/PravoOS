# Нагрузочный профиль

Проход E из `plans/bugs.md`: перевести гипотезы о пулах, индексах и SSE в цифры.
Всё лежит в `scripts/load/`, кода приложения проход не трогает.

## Что меряем

| Сценарий | Путь | Что должен вскрыть |
|---|---|---|
| `journey` | логин → `/api/ai/dashboard` → `/api/ai/cases` → `/api/ai/cases/{id}/tasks` | восемь параллельных запросов дашборда против 16 соединений Hikari; стоимость bcrypt(12) на входе |
| `search` | `/api/ai/search` в трёх режимах: с контентом, без контента, промах | веер из пяти источников, `LIKE` по `document_chunks` без триграммного индекса, N+1 на сниппетах |
| `chat-stream` | `POST /api/ai/chat/stream` ступенчатым ramp'ом | потолок SSE: `chatStreamExecutor` (AbortPolicy → 503), `llmStreamExecutor`, `pravoos.agent.streams.active` |

## Стенд

`docker-compose.yml` + оверлей `scripts/load/docker-compose.load.yml`. Поднимаются только
участники профиля — Grafana, Tempo, Loki, Alloy и Alertmanager не нужны и не стартуют.

Оверлей делает четыре вещи:

1. Подкладывает `scripts/load/.env.load` вторым `env_file` — профиль лимитеров и пулов.
2. Поднимает `mock-openai` и переключает на него `OPENAI_BASE_URL` у `llm-service`.
3. Публикует порт актуатора шлюза (8090) — с него снимаются метрики.
4. Монтирует сиды в `postgres`/`mongodb` и сценарии в `k6`.

```bash
cp scripts/load/.env.load.example scripts/load/.env.load
scripts/load/stack.sh up          # сборка + запуск, ждёт readiness шлюза
scripts/load/seed/seed.sh         # данные (идемпотентно)
scripts/load/run.sh journey
```

`stack.sh down` гасит стенд, `stack.sh logs ai-service` показывает логи.

## Почему мок вместо OpenAI

Реальный апстрим вносит в p99 чужую латентность и стоит денег на каждом прогоне.
`scripts/load/mock-openai/server.js` — 200 строк без зависимостей, отвечает на
`/v1/chat/completions` (потоком и без) и `/v1/embeddings` с управляемой задержкой:
`MOCK_TTFT_MS`, `MOCK_CHUNK_MS`, `MOCK_CHUNKS`, `MOCK_JITTER_PCT`.

Запрос с `max_tokens <= 8` мок распознаёт как вызов классификатора `LegalDomainGuard`
и отвечает `YES` — иначе guard резал бы все чат-запросы. `GET /stats` показывает,
сколько потоков было и сколько клиент оборвал (`aborted`) — прямая проверка того,
что отмена SSE доходит до апстрима.

## Данные

Сид детерминирован: `md5('pravoos-load-lawyer-' || N)::uuid`, поэтому `pravoos_users`
и `pravoos_ai` сходятся по `lawyer_id` и `org_id` без кросс-базовых запросов.

- `seed/users.sql` — юристы, профили, организации, членства, подписки на служебном
  тарифе `LOAD` (`daily_requests = 0`, `daily_tokens = 0` → `PlanLimits.quotaDisabled()`,
  квота LLM не мешает прогону).
- `seed/practice.sql` — клиенты, дела, задачи, документы, чанки, счета, списания времени;
  часть строк помечена `deleted_at`, чтобы работали партиальные индексы из V64/V67.
- `seed/conversations.js` — переписки и сообщения в Mongo.

Объёмы задаются в `.env.load` (`LOAD_CASES_PER_LAWYER` и соседние). Профиль по умолчанию:
50 юристов × 200 дел × 4 задачи ≈ 40 000 задач и 30 000 чанков — достаточно, чтобы
планировщик перестал выбирать seq scan там, где на пустой базе выбирал.

### UUID в Mongo

Spring Boot 3.5 пишет `java.util.UUID` как `BinData(3)` в java-legacy порядке байт
(каждая восьмёрка развёрнута). Сид повторяет это байт-в-байт. Если приложение когда-нибудь
переедет на `spring.data.mongodb.uuid-representation: standard`, сид упадёт с явной
ошибкой: он сверяет свой подтип с подтипом уже существующего документа приложения и
подсказывает `UUID_MODE=standard`. Молча наполнить базу невидимыми для приложения
переписками он не может.

## Лимитеры

Без правки профиль меряет Redis-лимитер шлюза, а не приложение. `.env.load` поднимает
`AUTH_RATE_*`, `AI_RATE_*`, `USER_RATE_*` до 2000 rps.

Одно место так не снимается: `AuthController.LOGIN_MAX_PER_IP = 30` за пять минут —
константа в коде, а весь k6 приходит с одного адреса (шлюз перезаписывает `X-Client-Ip`
реальным адресом, подменить его клиент не может). Поэтому `run.sh` во время прогона раз
в две секунды чистит ключи `ip_rate:login:*` в Redis. Это издержка стенда, но и
продуктовая находка: за одним корпоративным NAT тридцать первый вход за пять минут
получает 429.

## Съём метрик

`snapshot.sh sample` раз в пять секунд обходит актуаторы четырёх сервисов и пишет
`metrics.tsv` (TSV, а не CSV: в метках Prometheus встречаются запятые). `snapshot.sh
summary` сводит его в `metrics.md`. Прометей для этого не нужен и не поднимается.

Что попадает в срез: `hikaricp_connections_{pending,active,idle,timeout_total}`,
`tomcat_threads_*`, `executor_*` по каждому из восьми пулов, `http_server_requests_seconds`
с квантилями, `pravoos_agent_*`, `pravoos_login_*`, `process_cpu_usage`.

Квантили берутся готовыми: `.env.load` включает
`MANAGEMENT_METRICS_DISTRIBUTION_PERCENTILES_HTTP_SERVER_REQUESTS=0.95,0.99`,
и Micrometer публикует `quantile="0.95"` рядом с бакетами. Это скользящее окно
внутри инстанса, а не квантиль за весь прогон — для сравнения профилей между собой
годится, для SLA нужен Prometheus с `histogram_quantile`.

## Как читать результат

`run.sh` кладёт в `report/<дата>-<сценарий>/`: `k6-console.txt`, `k6-summary.json`,
`k6-metrics.csv`, `metrics.tsv`, `metrics.md`, `mock-openai-stats.json` и копию `env.load`.
Ненулевой код возврата означает невыдержанный порог из `options.thresholds`.

Клиентская и серверная стороны разделены намеренно:

- k6 меряет то, что видит пользователь. Для SSE `http_req_waiting` — время до первого
  события, `http_req_duration` — удержание соединения целиком.
- `metrics.md` отвечает, во что именно упёрлись. Порядок разбора: сначала
  `hikaricp_connections_pending` (ненулевой — упёрлись в соединения), затем
  `executor_queued_tasks` по конкретному пулу, затем `tomcat_threads_busy_threads`
  против `TOMCAT_THREADS_MAX`.

Прогон одного сценария при незаполненных данных бессмысленен: сначала `seed.sh`,
потом прогрев (первая минута ramp'а), только потом цифры.
