# PravoOS — Контекст для Claude Code

## Что уже построено

### Backend (Spring Boot микросервисы, работают в prod через Docker Compose)
- **auth-service** (порт 8081): регистрация, логин, JWT + refresh tokens, Redis для blacklist/rate-limit
- **lawyer-service** (порт 8082): заявки юристов, кабинет, дела (cases), документы (metadata only), community posts, feedback, профиль
- **ai-service** (порт 8083): подключён DeepSeek API, 5 bankruptcy workflows, prompt injection защита
- **common**: JWT валидация, AuthInterceptor, CORS, rate limiting, MDC фильтры

### Frontend (React SPA, Vite)
- Лэндинг, регистрация/логин, кабинет юриста, AI workflows UI, профиль, сообщество
- Дизайн-система: `frontend/ui/tokens.jsx`, `frontend/ui/lawyer.jsx`, `frontend/ui/auth.jsx`, `frontend/ui/admin.jsx`
- API клиент: `frontend/api.js`

### Инфраструктура
- `docker-compose.prod.yml`: nginx + certbot (SSL), PostgreSQL, Redis, все сервисы
- Сайт уже задеплоен на сервере
- DeepSeek API ключ есть в `.env`

### БД миграции (Flyway, схема `lawyer`)
- Миграции V1–V9: `lawyer_applications`, `lawyer_cases`, `lawyer_documents`, `feedback`, `community_posts`, `lawyer_profiles`
- Следующая миграция: V10

### Что НЕ реализовано (это нужно строить)
1. Документы сохраняются только как metadata — нет загрузки файлов и извлечения текста
2. Нет RAG: pgvector не подключён, embeddings не делаются, база практики пустая
3. AI workflows работают без контекста документов дела и без базы практики
4. AI ответы не сохраняются в БД, не привязаны к делу — нет истории
5. Оценка AI ответа (👍👎) в UI есть, но не сохраняется per-response
6. Prod .env не содержит REDIS_PASSWORD, APP_DOMAIN, VITE_AUTH_URL, VITE_LAWYER_URL, VITE_AI_URL

## Технические правила (соблюдать всегда)

- Java Spring Boot, PostgreSQL, Hibernate, Flyway, JWT — уже используется
- Без Lombok — нигде в проекте не используется, не добавлять
- Constructor injection везде (не @Autowired на поле)
- Кастомные исключения, никаких проглоченных catch
- При изменении существующего кода — показывать только изменённый фрагмент + 2–3 строки контекста
- Flyway миграции: следующий номер — V10 и далее

## Структура проекта

```
PravoOS/
├── backend/
│   ├── pom.xml                  (parent POM)
│   ├── auth-service/
│   ├── lawyer-service/
│   ├── ai-service/
│   └── common/
├── frontend/
│   ├── ui/
│   │   ├── tokens.jsx
│   │   ├── lawyer.jsx
│   │   ├── auth.jsx
│   │   ├── admin.jsx
│   │   └── landing.jsx
│   ├── api.js
│   └── app.jsx
├── nginx/
├── scripts/
├── .env
├── docker-compose.yml           (dev)
└── docker-compose.prod.yml      (prod)
```

---

# MVP План — шаги с моделями

## Шаг 1. Prod .env и деплой-чеклист | МОДЕЛЬ: sonnet

Привести конфигурацию к продакшн-готовому виду.
- Добавить в `.env.example`: REDIS_PASSWORD, APP_DOMAIN, VITE_AUTH_URL, VITE_LAWYER_URL, VITE_AI_URL
- Проверить docker-compose.prod.yml — все сервисы корректно читают переменные
- Написать `scripts/deploy.sh`: git pull → docker-compose up --build -d → health check
- Проверить nginx: `/api/auth/` → auth-service:8081, `/api/` → lawyer-service:8082, `/ai/` → ai-service:8083
- Frontend строится с правильными VITE_* переменными

## Шаг 2. Загрузка файлов и извлечение текста | МОДЕЛЬ: sonnet

Юрист загружает реальный файл (PDF, DOCX, TXT) — текст извлекается и сохраняется.
- `lawyer-service`: новый эндпоинт `POST /api/documents/upload` — multipart/form-data
- Apache Tika для извлечения текста из PDF/DOCX/TXT
- Миграция V10: добавить `extracted_text TEXT` в `lawyer_documents`; добавить `file_data BYTEA` для хранения файла в БД (MVP — не нужен S3)
- Ограничение: макс 10MB, только разрешённые форматы
- Frontend: кнопка загрузки файла в карточке дела

## Шаг 3. RAG инфраструктура — pgvector + embeddings | МОДЕЛЬ: sonnet

- Миграция V11: `CREATE EXTENSION vector` + таблица `practice_chunks`:
  ```sql
  id uuid PRIMARY KEY,
  source_url text,
  doc_type text,        -- court_decision | law | review | efrsb
  chunk_text text NOT NULL,
  embedding vector(1536),
  metadata jsonb,
  created_at timestamptz DEFAULT now()
  ```
  + индекс HNSW на embedding
- Python-скрипт `scripts/embeddings/embed.py`: читает текст → нарезает на chunks → делает embeddings через DeepSeek/OpenAI → сохраняет в practice_chunks
- Java `EmbeddingService` в ai-service: поиск top-K похожих chunks по cosine similarity

## Шаг 4. Сбор базы практики | МОДЕЛЬ: sonnet

Наполнить practice_chunks — 100–500 записей для первого теста юристами.

Скрипты в `scripts/data-collection/`:
1. `collect_127fz.py` — скачать и нарезать текст 127-ФЗ по статьям
2. `collect_kad.py` — парсинг kad.arbitr.ru: банкротные дела, определения судов
3. `collect_vsrf.py` — обзоры практики по банкротству с vsrf.ru

Формат metadata для каждого chunk:
```json
{
  "source_url": "...",
  "doc_type": "court_decision|law|review",
  "case_number": "...",
  "court": "...",
  "date": "YYYY-MM-DD",
  "legal_norms": ["127-ФЗ ст.61.2"],
  "issue_type": "..."
}
```

## Шаг 5. RAG в AI workflows — ответы с источниками | МОДЕЛЬ: opus (кросс-сервисная интеграция)

При запуске workflow: документы дела + релевантная практика → DeepSeek → ответ с источниками.

В ai-service:
1. `POST /ai/analyze` принимает caseNumber + workflow
2. Internal call → lawyer-service: получить extracted_text всех документов дела
3. Embedding запроса → top-10 chunks из practice_chunks
4. Prompt = system + documents + chunks + query
5. Ответ DeepSeek + список источников

Новый DTO:
```java
record AiResponse(String result, List<SourceReference> sources, String workflowId, String caseNumber)
record SourceReference(String url, String docType, String fragment)
```

Миграция V12: таблица `ai_responses`:
```sql
id uuid PRIMARY KEY,
case_number text,
workflow_id text,
email text,
result text,
sources jsonb,
rating smallint,          -- 1 / -1 / null
rating_comment text,
created_at timestamptz DEFAULT now()
```

Новый эндпоинт `POST /api/ai-responses/{id}/rate` — сохраняет rating + comment.

Frontend: источники под ответом, кнопки 👍👎 → отправить рейтинг.

## Шаг 6. Админка — заявки + AI метрики | МОДЕЛЬ: sonnet

- Проверить что `admin.jsx` корректно показывает список заявок юристов
- Добавить эндпоинт `GET /api/admin/ai-stats`: count responses, avg rating, breakdown по workflows
- Добавить `GET /api/admin/ai-responses`: последние N ответов с рейтингами (только ADMIN)
- Добавить в admin.jsx вкладку "AI метрики"

---

## Что должно работать к первому показу юристам (done = инвестиционный proof)

1. Регистрация → заявка → одобрение ADMIN → вход
2. Создать дело → загрузить PDF → запустить workflow → получить ответ с источниками
3. Поставить оценку ответу (👍👎)
4. ADMIN видит заявки и статистику AI

## Moat который нужно показать инвесторам

- База практики растёт с каждым загруженным делом
- Feedback юристов = размеченные примеры для будущего fine-tuning
- Закрытая сеть юристов-банкротчиков
- Workflows заточены под реальные задачи, а не generic chatbot
