# PravoOS — Architecture

## Overview
Legal AI platform for lawyers. Users ask questions, AI answers based on uploaded legal documents (RAG).
Registration is application-based: lawyers submit an application, admins approve/reject via web or Telegram.
Telegram bot handles admin notifications. Single backend serves both web and Telegram.

---

## Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3, Spring Cloud Gateway, Spring Security, Hibernate/JPA |
| Auth | JWT |
| DB (relational) | PostgreSQL + pgvector extension |
| DB (documents) | MongoDB |
| Migrations | Flyway |
| Messaging | Apache Kafka |
| LLM (current) | DeepSeek-V3 (swappable via `LlmClient` interface) |
| Doc parsing | Apache PDFBox (PDF), Apache POI (DOCX) |
| Frontend | React 18 + TypeScript + Vite + Tailwind CSS + React Query |
| Infra | Docker Compose (dev/VPS) → Kubernetes (prod) |
| Java | 21 |

---

## Monorepo Structure (Maven Multi-Module)

```
PravoOS/
├── pom.xml                         # parent POM
├── api-gateway/                    # Spring Cloud Gateway (port 8080)
├── user-service/                   # auth, users, lawyer applications (port 8081)
├── ai-service/                     # RAG pipeline, LLM, knowledge base (port 8082)
├── notification-service/           # Telegram bot, Kafka consumer (port 8083)
├── frontend/                       # React app
├── docker-compose.yml
├── .env.example
└── logs/
    ├── api-gateway/
    ├── user-service/
    ├── ai-service/
    └── notification-service/
```

---

## Microservices

### api-gateway (port 8080)
- Spring Cloud Gateway + JWT validation at gateway level
- Routes: `/api/auth/**` → user-service, `/api/users/**` → user-service, `/api/ai/**` → ai-service
- Rate limiting per user (Redis-backed)
- No business logic

### user-service (port 8081)
**Responsibilities**: JWT auth, lawyer registration flow, admin management
**DB**: PostgreSQL (`pravoos_users`)

DB Tables:
- `users` — id, email, password_hash, role [LAWYER/ADMIN], status [PENDING/ACTIVE/REJECTED], created_at
- `lawyer_profiles` — user_id, full_name, bar_number, specialization, phone
- `lawyer_applications` — id, email, full_name, bar_number, specialization, phone, status, submitted_at, reviewed_at, reviewed_by

Package layout:
```
com.pravoos.user/
├── controller/   AuthController, ApplicationController, AdminController
├── service/      AuthService, ApplicationService, AdminService
├── repository/   UserRepository, ApplicationRepository
├── model/
│   ├── entity/   User, LawyerProfile, LawyerApplication
│   └── dto/      LoginRequest, RegisterApplicationRequest, ApplicationResponse, ...
├── exception/    UserNotFoundException, ApplicationAlreadyExistsException, ...
├── config/       SecurityConfig, JwtConfig
├── event/        ApplicationSubmittedEvent (Kafka producer)
└── security/     JwtTokenProvider, JwtAuthenticationFilter
```

Key API flows:
- `POST /api/auth/apply` → save application → publish `application.submitted` Kafka event
- `POST /api/auth/login` → validate credentials → return JWT
- `POST /api/admin/applications/{id}/approve` → activate user account
- `POST /api/admin/applications/{id}/reject` → reject application

### ai-service (port 8082)
**Responsibilities**: document management, RAG pipeline, LLM calls, conversation history
**DB**: PostgreSQL + pgvector (`pravoos_ai`), MongoDB (`pravoos_chat`)

PostgreSQL tables:
- `documents` — id, title, file_name, file_type, uploaded_by, uploaded_at, status
- `document_chunks` — id, document_id, content TEXT, chunk_index, embedding vector(1536), created_at

MongoDB collections:
- `conversations` — id, lawyer_id, created_at, title
- `messages` — conversation_id, role [USER/ASSISTANT], content, sources[], created_at

Package layout:
```
com.pravoos.ai/
├── controller/   ChatController, DocumentController
├── service/      ChatService, DocumentService, RagService, EmbeddingService
├── repository/   DocumentRepository, ChunkRepository (JPA), ConversationRepository (Mongo)
├── model/
│   ├── entity/   Document, DocumentChunk, Conversation, Message
│   └── dto/      ChatRequest, ChatResponse, DocumentUploadResponse, ...
├── exception/    DocumentProcessingException, LlmException, ...
├── config/       VectorDbConfig, MongoConfig, LlmConfig
├── llm/
│   ├── LlmClient.java              (interface — key swap point)
│   ├── DeepSeekLlmClient.java
│   └── dto/      LlmRequest, LlmResponse
└── pipeline/     DocumentParser, TextChunker, EmbeddingPipeline
```

RAG pipeline:
1. Admin uploads PDF/DOCX → `DocumentParser` extracts text
2. `TextChunker` splits into ~512-token chunks with 50-token overlap
3. `EmbeddingService` generates vectors via DeepSeek embedding API
4. Chunks + vectors stored in `document_chunks` (pgvector)
5. User question → embed → cosine similarity search → top-5 chunks
6. Prompt = system context + top-5 chunks + conversation history + user question
7. `LlmClient.complete()` → SSE stream back to client

LlmClient interface:
```java
public interface LlmClient {
    String complete(String systemPrompt, List<Message> history, String userMessage);
    float[] embed(String text);
}
```

### notification-service (port 8083)
**Responsibilities**: Telegram notifications to admins
**DB**: None (stateless)

Package layout:
```
com.pravoos.notification/
├── consumer/    ApplicationEventConsumer (Kafka)
├── service/     TelegramNotificationService
├── config/      TelegramBotConfig, KafkaConfig
└── bot/         PravoOsAdminBot
```

Kafka topics consumed:
- `application.submitted` → Telegram message to admin chat: "Новая заявка от [Name], [email]. Принять/отклонить в админке."

---

## Kafka Events

| Topic | Producer | Consumer | Payload |
|-------|----------|----------|---------|
| `application.submitted` | user-service | notification-service | applicationId, fullName, email, specialization |

---

## Frontend (React 18 + TypeScript + Vite + Tailwind)

Routes:
- `/login` — login for approved lawyers
- `/apply` — application form
- `/chat` — AI chat interface (protected, LAWYER role)
- `/admin/applications` — applications list with approve/reject (ADMIN)
- `/admin/knowledge-base` — document upload/management (ADMIN)
- `/admin/users` — active lawyers (ADMIN)

Libraries: React Query (server state), Zustand (auth), Tailwind CSS, React Router v6, SSE for streaming responses.

**All business logic on backend. Frontend only renders and calls API.**

---

## Security
- JWT issued by user-service, validated at api-gateway
- Roles: `ROLE_LAWYER`, `ROLE_ADMIN`
- Passwords: BCrypt
- All secrets in `.env` (template: `.env.example`)

---

## Logging
- SLF4J + Logback per service
- `logs/<service-name>/<service-name>.log` with daily rotation
- Logged: auth events, application status changes, document uploads, AI queries

---

## DB Migrations
- Flyway for PostgreSQL services
- Files: `src/main/resources/db/migration/V{n}__{description}.sql`

---

## Infrastructure (Docker Compose)

```yaml
services: postgres, mongodb, zookeeper, kafka,
          api-gateway, user-service, ai-service, notification-service,
          frontend (nginx)
```

---

## Implementation Order
1. Parent POM + module scaffolding
2. user-service (auth + applications)
3. notification-service (Telegram + Kafka)
4. ai-service (RAG pipeline)
5. api-gateway
6. Frontend
