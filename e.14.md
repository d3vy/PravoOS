# E14 — Клиентский портал

Дифференциатор (гэп по конкурентам, см. `plan.md`). У Clio Grow клиент видит статус дела,
грузит документы, переписывается. У нас `Client` был CRM-записью без входа — даём вход.

## Ключевое архитектурное решение

**Invite-based, НЕ открытая саморегистрация.** Клиент попадает в портал по ссылке-инвайту
от своего юриста (как Clio / MyCase / PracticePanther). Причина: самозарегавшийся клиент
не привязан ни к юристу, ни к делу → пустой аккаунт-сирота. Инвайт подтверждает не админ,
а юрист, который и так владеет этим `Client`. Без экстра-подтверждения админа.

### Где что живёт
- **Инвайт — в user-service** (не ai-service). Там вся отправка почты (Resend) и выпуск JWT.
  Калька с существующего `OrganizationInvite`. У ai-service почты нет вообще.
- **ai-service** владеет связкой `Client ↔ Case` (`Client.lawyerId`) и лишь **инициирует** инвайт
  (проверка `requireOwnedClient`), проксируя вызов в user-service.
- **Связка user↔client — через JWT-claim `clients`** (по аналогии с уже существующим claim `orgs`):
  на логине CLIENT-а user-service кладёт его `clientId` в токен; ai-service читает и скоупит дела.
  Ноль кросс-вызовов на чтение портала. **Реализуется в M3.**

### Модель данных
- `UserRole.CLIENT` — новая роль (было только LAWYER, ADMIN).
- `client_portal_invites` (user-service, V17): `id, client_id, lawyer_id, email, token_hash(unique),
  status, user_id, expires_at, accepted_at, created_at`.
  - `client_id` — **БЕЗ FK** (чужой домен: `clients` в БД ai-service).
  - `lawyer_id`, `user_id` — FK на `users`.
  - Accepted-строка = запись доступа (хранит `user_id`); purge её НЕ трогает.
- Связь: `Case.client_id` → `Client.id`. Клиент видит `cases WHERE client_id IN (его clientId из JWT)`.

---

## Модули (шиппабельные слои, между ними `/clear`)

### ✅ M1 — Регистрация клиента (СДЕЛАНО)
«Теперь можно регаться как клиенту». Backend end-to-end + тесты (8/8 зелёные).

**Flow:**
1. Юрист: `POST /api/ai/clients/{id}/portal/invite` (роль LAWYER) → `requireOwnedClient` +
   проверка наличия email → ai-service зовёт user-service `POST /internal/portal-invites`.
2. user-service: revoke pending по `client_id`, генерит токен (SecureRandom 32B, base64url),
   в БД только SHA-256 хэш, TTL 7д, статус PENDING → событие → письмо со ссылкой
   `/portal/accept?token=<rawToken>`.
3. Клиент: `GET /api/auth/portal/invite?token=` (превью email) →
   `POST /api/auth/portal/accept {token, password}` → валидация токена + парольной политики →
   создаётся `User(role=CLIENT, ACTIVE)`, инвайт → ACCEPTED (+ `user_id`) → **авто-логин**
   (токены + refresh-cookie как обычный `/login`).

**Файлы user-service:** `UserRole`(+CLIENT), `V17__client_portal_invites.sql`,
`ClientPortalInvite`, `ClientPortalInviteRepository`, `ClientPortalInviteService`,
`InternalPortalInviteController`, DTO `CreatePortalInviteRequest`/`PortalAcceptRequest`/
`PortalInvitePreviewResponse`, `ClientPortalInviteCreatedEvent`, `ClientPortalInviteEmailSender`,
метод `ResendEmailClient.sendClientPortalInviteEmail`, правки `AuthController`
(+`/portal/invite`, `/portal/accept`) и `AuthService.issueTokensForUser`.
**Файлы ai-service:** `UserServiceClient.createPortalInvite`, `ClientService.invitePortal`,
эндпоинт в `ClientController`, исключения `ClientEmailRequiredException`/`PortalInviteException`.
**Тесты:** `ClientPortalInviteServiceTest` (create+rate-limit, accept happy/invalid/expired/
already-accepted/email-exists, preview).

Гейтвей-маршрут не нужен — подпути существующих `/api/auth/**`, `/api/ai/**`.

### ✅ M2 — Фронт: приглашение и приём инвайта (СДЕЛАНО)
- Кнопка «Пригласить в портал» в карточке клиента (`ClientPortalSection`): статус
  (приглашён / есть доступ / нет), «Отправить повторно» / «Отозвать» для pending,
  disabled если у клиента нет email.
- Публичная страница `/portal/accept?token=` (`PortalAcceptPage`): превью email
  (`GET /api/auth/portal/invite`), форма «задать пароль» → `POST /api/auth/portal/accept`
  → авто-логин (роль CLIENT в сессии) → редирект на `/portal` (плейсхолдер `PortalHomePage`,
  заменяется в M3).
- Статус/отзыв: user-service `GET /internal/portal-invites/status?clientId=` +
  `DELETE /internal/portal-invites?clientId=` (revoke pending по clientId); ai-service проксирует
  с `requireOwnedClient` через `GET`/`DELETE /api/ai/clients/{id}/portal/invite`.
- `PortalAccessStatus{NONE,PENDING,ACCEPTED}`; `UserRole` на фронте +CLIENT; ProtectedRoute/
  catch-all/Login роутят CLIENT на `/portal`.
- Тесты: `ClientPortalInviteServiceTest` +status(accepted/pending/expired/none)+revoke (13/13).

### ✅ M3 — Портал: список своих дел + карточка (read-only) (СДЕЛАНО)
- **JWT-claim `clients`:** `JwtTokenProvider.generateToken(+clientIds)` кладёт claim `clients`;
  `AuthService.issueTokens` грузит их только для роли CLIENT
  (`ClientPortalInviteRepository.findAcceptedClientIdsByUserId`).
  common-web: `OrgContext(orgIds, clientIds)` + `SecurityUtils.currentClientIds`;
  ai-service `JwtAuthenticationFilter` извлекает `clients` (общий `extractUuidList`).
- ai-service portal-эндпоинты под роль CLIENT (`PortalCaseController` `/api/ai/portal/cases`):
  `GET /` (свои дела), `GET /{id}` — скоуп строго по `clientId` из JWT в `PortalCaseService`
  (IDOR-guard: `CaseNotFoundException` если clientId дела не в скоупе / null).
  `CaseRepository.findByClientIdInOrderByCreatedAtDesc`; SecurityConfig `/api/ai/portal/**` hasRole CLIENT
  (до `/api/ai/cases/**`). Gateway-маршрут не нужен — `/api/ai/**` без requiredRole.
- DTO `PortalCaseResponse` (список) / `PortalCaseDetailResponse` (+hearings) — без ownerId/orgId.
- Фронт: `PortalLayout` (хедер/логаут), `PortalCasesPage` (`/portal`),
  `PortalCaseDetailPage` (`/portal/cases/:caseId`); `api/portal.ts`; заглушка `PortalHomePage` удалена.
- Тесты: `PortalCaseServiceTest` (scope/IDOR/no-client/missing, 6/6). Починен `ClientServiceOwnershipTest`
  (M1 добавил `UserServiceClient` в конструктор `ClientService`) и mock `generateToken` в `AuthServiceTest`.

**Отзыв доступа + инвалидация claim `clients` (M3-hardening):**
- Проблема: claim `clients` — снапшот на момент выпуска токена; принятый доступ раньше нельзя было
  отозвать вообще (refresh вечно переиздавал старый claim).
- Решение — **denylist-on-revoke + reload-on-refresh** (переиспользован `TokenDenylistService`
  → `auth:revoked_after:<userId>`, который уже читают gateway/user/ai-фильтры):
  `ClientPortalInviteService.revokeAccess(clientId)` = revoke PENDING **и** ACCEPTED
  (`revokeAcceptedByClientId`), затем `revokeAccessTokensFor(userId)` для каждого затронутого
  (`findAcceptedUserIdsByClientId`). Токен клиента отклоняется на след. запросе → axios-интерцептор
  делает refresh → `issueTokens` перечитывает clientIds из БД (уже без отозванного). Другие клиенты
  того же юзера сохраняются; refresh-токены НЕ трогаем (отзыв одного клиента ≠ полный логаут).
- Defense-in-depth: даже при сбое Redis-записи денилиста ACCEPTED→REVOKED уже в БД → refresh уронит
  доступ в пределах TTL access (15 мин).
- DELETE `/internal/portal-invites?clientId=` теперь = revokeAccess (было revokePending); ai-service
  проксирует без изменений (`requireOwnedClient`). Фронт: кнопка «Отозвать доступ» на ACCEPTED в
  `ClientPortalSection` (с confirm). Тесты: revokeAccess pending-only / accepted+denylist (14/14).

### ✅ M4 — Документы клиента (СДЕЛАНО)
- **Флаг видимости:** `documents.visible_to_client` (V21, default FALSE + частичный индекс
  `WHERE visible_to_client = TRUE`). Юрист сам решает, что показать клиенту; документ, загруженный
  клиентом, помечается видимым автоматически. `Document.visibleToClient`, `DocumentResponse.visibleToClient`.
- **Портал (роль CLIENT), `PortalDocumentController` `/api/ai/portal/cases/{caseId}/documents`:**
  `GET /` (только видимые дела в скоупе), `POST /` (клиент грузит → `visibleToClient=true`, реюз
  `DocumentService.upload` с квотами/rate-limit), `GET /{documentId}/content` (скачивание + аудит).
  `PortalDocumentService` сначала `PortalCaseService.requireClientCase` (IDOR-скоуп по clientId из JWT),
  затем документ-операция. `DocumentService.loadClientContent(docId, caseId)` — double-check:
  `caseId` совпал И `visibleToClient` → иначе `DocumentNotFoundException`. Аудит `DOCUMENT_DOWNLOAD`
  (actorRole=CLIENT), реюз `AccessAuditService`.
- **Юрист:** `PATCH /api/ai/cases/{caseId}/documents/{documentId}/visibility` (`DocumentVisibilityRequest`)
  → `CaseService.setDocumentVisibility` (requireVisibleCase + `DocumentService.setClientVisibility`,
  проверка `doc.caseId == caseId`).
- **Фронт:** секция «Документы» в `PortalCaseDetailPage` (список/скачивание blob/загрузка);
  тумблер «Виден клиенту / Скрыт» на документе в юристской `DocumentsSection` (CaseDetailPage);
  `portalApi.listCaseDocuments/uploadCaseDocument/downloadCaseDocument`, `casesApi.setDocumentVisibility`.
- **Тесты:** `PortalDocumentServiceTest` (IDOR-скоуп list/download/upload через реальный
  `PortalCaseService`, 6/6), `DocumentClientVisibilityTest` (loadClientContent visible/чужое дело,
  setClientVisibility flag/чужое дело, 4/4). Всего 10/10.
- Гейтвей-маршрут не нужен — подпути существующих `/api/ai/portal/**` и `/api/ai/cases/**`.

### ✅ M5 — Переписка клиент ↔ юрист по делу (СДЕЛАНО)
- **Модель (ai-service):** `CaseMessage(caseId, authorUserId, authorRole, body, createdAt)` + `MessageAuthorRole{LAWYER,CLIENT}`,
  V22 `case_messages` (FK `case_id`→`cases` ON DELETE CASCADE, индекс `(case_id, created_at)`).
  `CaseMessageRepository.findByCaseIdOrderByCreatedAtAsc`.
- **Эндпоинты (ai-service), общий `CaseMessageService`:**
  юрист — `CaseMessageController` `/api/ai/cases/{caseId}/messages` (GET/POST, `requireVisibleCase`);
  клиент — `PortalMessageController` `/api/ai/portal/cases/{caseId}/messages` (GET/POST, `requireClientCase` — IDOR-скоуп по clientId из JWT).
  `SendMessageRequest{@NotBlank @Size(max=5000) body}`, `CaseMessageResponse` (без orgId/ownerId).
- **Уведомления (outbox → Kafka `case.message.created` → notification-service):** при отправке
  `CaseMessageService.enqueueNotification` кладёт `CaseMessageCreatedKafkaPayload` (получатель = противоположная сторона:
  юрист→клиент, клиент→юрист; если у дела нет клиента — не шлём). `notification-service` `CaseMessageConsumer`
  (dedup по `messageId` через `ProcessedEventGuard`) → `TelegramNotificationService.notifyCaseMessage` →
  зовёт user-service `POST /internal/notifications/case-message` (тот резолвит получателя, шлёт email по флагу
  `caseMessageEmail` и возвращает `telegramChatId` если `caseMessageTelegram` включён и Telegram привязан) →
  notification-service шлёт telegram. DLT-топик `case.message.created.DLT`, контейнер-фабрика `caseMessageKafkaListenerContainerFactory`.
- **Настройки уведомлений:** `users.case_message_email`(default TRUE)/`case_message_telegram`(default FALSE) (V18),
  `User` +getters, `NotificationSettingsResponse`/`UpdateNotificationSettingsRequest` +2 поля, `UserService` перечитан на `UserRepository`.
- **Фронт:** общий `CaseMessageThread` (пузыри «своё/чужое» по `viewerRole`, Ctrl/⌘+Enter, автоскролл) —
  в юристской `CaseDetailPage` (`viewerRole=LAWYER`) и `PortalCaseDetailPage` (`viewerRole=CLIENT`);
  `casesApi.listMessages/sendMessage`, `portalApi.listCaseMessages/sendCaseMessage`;
  в `SettingsPage` вторая группа «Сообщения по делу» (email/telegram).
- **Тесты:** `CaseMessageServiceTest` (lawyer→client / client→lawyer notify, no-client → без нотификации, IDOR-делегация, 4/4);
  `CaseMessageNotificationServiceTest` (user-service). Все backend-модули зелёные, фронт `tsc --noEmit` чистый.
- Гейтвей-маршрут не нужен — подпути существующих `/api/ai/cases/**`, `/api/ai/portal/**`, `/internal/notifications/**`.

---

## Конвенции проекта (не потерять)
- Constructor injection, без Lombok, без комментариев в коде, кастомные исключения.
- UTC везде: `LocalDateTime.now(ZoneOffset.UTC)`. Токены — хэш в БД, не сырьё.
- Тесты: JUnit 5 + Mockito, `@BeforeEach setUp()`, обязательно IDOR/ownership на portal-эндпоинты.
- Gateway-маршрут обязателен на каждый **новый** `/api/<prefix>/**` (для M1–M5 префиксы уже есть).
- ai-service репозитории: JPA→`repository/jpa/`, Mongo→`repository/mongo/`.
- `INTERNAL_SERVICE_SECRET` fail-closed; новые `/internal/**` автоматически под `InternalSecretFilter`.

## Открытые вопросы (решены)
- ✅ **Мульти-клиент на один email** (физлицо + его ООО). `accept` больше не падает
  `EmailAlreadyExistsException`: если email нет — создаём CLIENT и задаём пароль (валидация
  парольной политики перенесена из `AuthController` в `ClientPortalInviteService.createClientAccount`);
  если email принадлежит **активному CLIENT** — поле `password` трактуется как ВХОД:
  `passwordEncoder.matches` против текущего хэша (пароль НЕ меняется) → привязка `clientId` +
  авто-логин с обоими `clientId`; неверный пароль → `InvalidCredentialsException` (401);
  email принадлежит не-CLIENT/неактивному → `PortalAccountConflictException` (409).
  `preview` отдаёт `accountExists`; `PortalAcceptPage` показывает «войти и привязать» vs «задать пароль».
  Тесты `ClientPortalInviteServiceTest`: link happy/wrong-pass/non-client/inactive + create (17/17).
- ✅ list/revoke pending-инвайтов — сделано в M2.
- ✅ Разграничение видимости документов клиенту — сделано в M4.
