# PravoOS — Legal AI Platform

B2B SaaS для юристов. AI отвечает на вопросы на основе загруженных правовых документов (RAG).
Регистрация заявочная: юрист подаёт заявку, администратор одобряет через веб-интерфейс или Telegram.

---

## Стек

| Слой | Технологии |
|------|-----------|
| Backend | Java 21, Spring Boot 3.3.5, Spring Cloud Gateway |
| Auth | JWT (JJWT 0.12.6), BCrypt, Spring Security |
| БД | PostgreSQL + pgvector, MongoDB |
| Миграции | Flyway |
| Очередь | Apache Kafka |
| LLM | OpenAI gpt-4o-mini (swappable via `LlmClient`) |
| Парсинг | Apache PDFBox 3.x, Apache POI 5.x |
| Уведомления | Telegram Bot |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, React Query |
| Инфра | Docker Compose (dev/VPS) |

---

## Структура монорепо

```
PravoOS/
├── pom.xml                    # parent POM (com.pravoos:pravoos-parent:1.0.0)
├── api-gateway/               # Spring Cloud Gateway — port 8080
├── user-service/              # auth, заявки, пользователи — port 8081
├── ai-service/                # RAG, LLM, база знаний — port 8082
├── notification-service/      # Telegram бот, Kafka consumer — port 8083
├── frontend/                  # React приложение
├── docker-compose.yml
├── docker-compose.prod.yml
├── .env.example
├── ARCHITECTURE.md
└── DEPLOY.md
```

---

## Роли пользователей

- **LAWYER** — юрист: задаёт вопросы AI, просматривает историю чатов
- **ADMIN** — администратор: управляет заявками, загружает документы в базу знаний

---

## RAG Pipeline

1. Админ загружает PDF/DOCX → `DocumentParser` извлекает текст
2. `TextChunker` разбивает на чанки ~512 токенов (50 токенов перекрытие)
3. `EmbeddingService` генерирует векторы через OpenAI Embedding API
4. Чанки + векторы сохраняются в `document_chunks` (pgvector)
5. Вопрос юриста → embed → cosine similarity → top-5 чанков
6. Промпт = системный контекст + top-5 чанков + история + вопрос
7. `LlmClient.complete()` → SSE стрим обратно клиенту

---

## Быстрый старт (локальная разработка)

```bash
cp .env.example .env
# Заполни .env (см. ниже)

docker compose up -d
cd frontend && npm run dev
```

- API: `http://localhost:8080`
- Frontend: `http://localhost:3000`

### Обязательные переменные `.env`

| Переменная | Описание |
|------------|----------|
| `JWT_SECRET` | Случайная строка ≥ 64 символов |
| `OPENAI_API_KEY` | API ключ OpenAI |
| `TELEGRAM_BOT_TOKEN` | Токен Telegram бота |
| `TELEGRAM_ADMIN_CHAT_ID` | Chat ID для уведомлений |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Первый администратор |
| `DB_PASSWORD` / `MONGO_PASSWORD` | Пароли БД |

Сгенерировать JWT_SECRET:
```bash
openssl rand -base64 64
```

---

## Деплой на VPS

Подробная инструкция: [DEPLOY.md](DEPLOY.md)

Краткий сценарий:
```bash
git clone <repo> /opt/pravoos && cd /opt/pravoos
sudo ./scripts/setup-server.sh   # Docker, ufw
cp .env.example .env && nano .env
./scripts/deploy.sh              # сборка + SSL + запуск
```

Обновление:
```bash
git pull && ./scripts/deploy.sh
```

---

## Kafka-события

| Topic | Producer | Consumer |
|-------|----------|----------|
| `application.submitted` | user-service | notification-service |

---

## Архитектура

Подробнее: [ARCHITECTURE.md](ARCHITECTURE.md)
