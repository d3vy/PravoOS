# Маршрут изучения PravoOS

> Не читай код подряд. Проект — монорепо из 4 сервисов + 3 общих либы.
> Иди сверху вниз: сначала «что и зачем», потом «как течёт запрос», потом один вертикальный срез до кода.

## Шаг 0. Картина целиком (без кода)
1. **`README.md`** — продукт, роли (`LAWYER`/`ADMIN`/`CLIENT`), стек, топология, RAG-пайплайн, безопасность.
2. **`memory/MEMORY.md`** — самое ценное: «почему так сделано». Решения по Modulith, вынос `llm-service`, JWT RS256, refresh-ротация, outbox, гибридный поиск. Читай медленно.
3. **`plan.md`** + **`todo.md`** — что сделано и что в работе.

## Шаг 1. Как устроена система физически
4. **`docker-compose.yml`** — все компоненты разом: 4 приложения + postgres/mongo/redis/kafka + observability. Порты, `depends_on`, переменные окружения.
5. **`.env.example`** — какие секреты/настройки есть (JWT-пара, OpenAI, internal-secret и т.д.).

## Шаг 2. Путь запроса (backbone)
Пройди один HTTP-запрос от браузера до БД:

```
Browser → api-gateway (:8080) → user-service / ai-service → PostgreSQL
```

6. **`api-gateway`** — начни с `application.yml` (маршруты `/api/**`) и фильтров (`GlobalErrorWebExceptionHandler`, JWT-верификация, sanitizing заголовков). ⚠️ WebFlux (реактивный): фильтры — `GlobalFilter`, не сервлетные.
7. **`pravoos-common`** — маленький, но фундаментальный: `JwtVerifier`, `RsaKeyLoader`. Через него верифицируется каждый запрос.

## Шаг 3. Один сервис вглубь — `user-service`
8. Структура Spring Modulith: модули `identity` / `registration` / `collaboration` / `shared`. Открой `package-info.java` в каждом — там объявлены границы.
9. Flow логина: `/api/auth/login` → `AuthService` → JWT-выпуск → refresh-cookie. Ядро безопасности.

## Шаг 4. Главная фича — `ai-service` + RAG
10. Модули `llm` / `core` / `practice` / `document` / `shared`.
11. RAG-пайплайн: загрузка документа → чанки → эмбеддинги → гибридный поиск (вектор + FTS + RRF + реранкер) → ответ LLM.
12. **`llm-service`** — единственный шлюз к OpenAI (:8084, internal-only).

## Шаг 5. Периферия (по мере надобности)
13. **`notification-service`** — Kafka-consumer + Telegram.
14. **`frontend`** — React + TS + Vite, если фронт в зоне ответственности.

## Принципы, которые надо усвоить сразу
- **Границы Modulith — CI-gate.** Нельзя импортировать `*.internal.*` чужого модуля. Только через `api`-фасад или Kafka-событие. Сборка падает при нарушении.
- **JWT RS256:** user-service подписывает приватным ключом, все остальные верифицируют публичным.
- **Transactional Outbox:** Kafka не шлётся напрямую — сначала запись в `outbox_events`, потом релей.

## Практика
После Шага 1 подними стек локально (`docker compose up -d`), открой Grafana-дашборд и Swagger каждого сервиса — так документация станет живой, будешь видеть реальные запросы/трейсы, пока читаешь код.
