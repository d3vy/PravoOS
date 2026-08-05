# План покрытия PravoOS тестами

Дата: 2026-08-03. Статус на момент написания:

| Модуль | Main-классов | Test-файлов | Testcontainers | Оценка покрытия |
|---|---|---|---|---|
| ai-service | 520 | 61 | нет | низкое (~10-15%) |
| user-service | 284 | 21 | есть | очень низкое (~5-7%) |
| notification-service | 53 | 5 | нет | очень низкое |
| llm-service | 29 | 2 | нет | почти нет |
| api-gateway | 12 | 2 | нет | почти нет |
| pravoos-common | 10 | 3 | нет | частичное |
| pravoos-common-web | 4 | 0 | нет | нет |
| pravoos-observability | 5 | 3 | нет | частичное |
| config-server / discovery-server | 1/1 | 0 | нет | не требуется (инфраструктурные) |
| frontend | — | 8 файлов | vitest | почти нет |

Общий вывод: реальное покрытие сейчас в районе 5-10% по бэкенду, фронтенд почти не покрыт. Задача большая, поэтому план разбит на модули и волны — реализуем по одному модулю за раз, после каждого — `/clear`.

---

## 0. Общая инфраструктура тестирования (сделать один раз, до модулей)

Это блокер для всего остального — без него каждый модуль будет тянуть свою конфигурацию заново.

- [ ] Добавить `testcontainers-bom` в родительский `pom.xml`, чтобы версии не расходились между модулями
- [ ] Вынести общий тестовый конфиг в `pravoos-common` (или новый `pravoos-common-test` модуль): базовый `@SpringBootTest`-класс с подключением Postgres/Mongo Testcontainers, переиспользуемый через `@Import` или наследование
- [ ] Настроить JaCoCo в родительском pom: агрегированный отчёт по всем модулям, порог покрытия для `verify` (например line coverage ≥ X%, поднимать постепенно)
- [ ] Прогнать `mvn spotless:apply` как часть CI перед тестами — форматирование не должно ломать diff тестов
- [ ] Зафиксировать соглашение: unit-тесты — `Mockito` + `JUnit5`, интеграционные — `@SpringBootTest` + `Testcontainers`, контрактные для REST — `MockMvc`/`WebTestClient`
- [ ] Добавить `ArchUnit`-тест в `pravoos-common` (или отдельно на каждый модуль) — проверка границ пакетов `api` / `internal`, если в проекте используется модульная структура (видно по `user.identity.api` / `user.identity.internal`)
- [ ] CI: шаг `mvn verify` должен реально гонять тесты по всем модулям (проверить, что сейчас не пропускается через `-DskipTests` где-то в pipeline)
- [ ] Frontend: проверить, что `vitest` уже настроен с покрытием (`vitest run --coverage`), добавить порог в `vitest.config` при необходимости

---

## 1. Приоритизация модулей

Порядок волн — по риску (PII, деньги, авторизация) и по размеру технического долга, не по алфавиту.

**Волна 1 — критичный домен (PII, безопасность, деньги):**
1. `user-service` (identity, privacy, consent — судя по незакоммиченным файлам `ConsentRequiredException`, `V25__personal_data_privacy.sql`, там сейчас активная разработка PII/consent-логики)
2. `pravoos-common` (`PiiCryptoHolder`, `PiiEncryptor` — сейчас в процессе переноса из ai-service, это shared security-код, критичен вдвойне)

**Волна 2 — основной бизнес-домен:**
3. `ai-service` (самый большой модуль, 520 классов — бить на суб-волны по пакетам)
4. `llm-service`

**Волна 3 — инфраструктурные/периферийные:**
5. `api-gateway`
6. `notification-service`
7. `pravoos-common-web`, `pravoos-observability`

**Волна 4:**
8. `frontend`

---

## 2. Волна 1 — user-service

Сейчас: 284 main-класса, 21 тест, есть testcontainers-зависимости (уже подключены, но, похоже, недоиспользуются).

### 2.1 Приоритет A — то, что сейчас незакоммичено (`git status`), значит свежий код без тестов вообще:
- [x] `UserSessionQuery` / `UserSessionSnapshot` / `UserSessionQueryAdapter` — unit-тесты на adapter (маппинг сессий, пустой список, порядок); `UserSessionQuery`/`UserSessionSnapshot` — чистый контракт/record без логики, отдельных тестов не требуют
- [x] `user/privacy/*` — `ConsentService`/`PersonalDataService` уже были покрыты ранее (`com.pravoos.user.service.ConsentServiceTest`/`PersonalDataServiceTest`, legacy-пакет); добавлены недостающие тесты на контроллеры: `PrivacyControllerTest` (mandatory-purpose guard в `revoke`, резолвинг client-ip в `grant`, `policy`), `PrivacyAdminControllerTest` (`complete` с null/пустым body). Исключения (`ConsentRequiredException`/`MandatoryConsentException`/`SubjectNotFoundException`) — плоские конструкторы без логики, отдельных тестов не требуют; их выброс покрыт через тесты вызывающего кода (`PrivacyController`, `ConsentService`)
  - [x] `ApplicationService.submitApplication` — новый `ApplicationServiceTest` (`user-service/src/test/java/com/pravoos/user/service/`): `ConsentRequiredException` при отсутствии personalData/crossBorder consent, `ApplicationAlreadyExistsException` при PENDING-заявке на тот же email, `EmailAlreadyExistsException` при уже зарегистрированном пользователе, успешный сабмит (маппинг полей, email нормализуется в lowercase, паблишится `ApplicationSubmittedSpringEvent` + outbox-событие), отдельный тест на то, что email-verification-токен хранится хэшированным (`TokenHasher.sha256Hex`), а status-токен — в сыром виде
- [x] Миграция `V25__personal_data_privacy.sql` — `PersonalDataPrivacyMigrationIT` (`user-service/src/test/java/com/pravoos/user/migration/`, Flyway + Testcontainers Postgres, каждый тест в своей изолированной schema): прогон всех 25 миграций без ошибок, `full_name`/`phone` в `lawyer_profiles`/`lawyer_applications` расширены до `TEXT`, новые consent-колонки `lawyer_applications` с дефолтами (`consent_cross_border`/`consent_marketing` = false), партиальный уникальный индекс `uq_user_consents_active` (нельзя два активных consent на одну `(user_id, purpose)`, можно после revoke), бэкфилл `user_consents` (`PERSONAL_DATA`/`1.0`/`LEGACY`) для существующих пользователей при накатывании V25 поверх V24, таблица `subject_requests` с дефолтным статусом `PENDING`

### 2.2 Identity/Auth
- [x] JWT-выпуск/валидация — unit на генерацию токена (`JwtTokenProviderTest`: claims, orgs/clients/plan-опциональность, issuedAt/expiration по TTL); просроченный/невалидный токен уже покрыт `JwtVerifierTest` (pravoos-common); refresh-flow уже покрыт `RefreshTokenServiceTest` (rotate: reuse-detection, expired, concurrent, revoke)
- [x] Login/logout endpoints — `AuthControllerTest` (standalone `MockMvc` + `GlobalExceptionHandler`, без полного Spring-контекста — контроллер не трогает БД/Redis напрямую, сервисы замоканы): успешный логин со 100%-cookie, MFA-challenge без cookie, 401 на неверные креды, 429+Retry-After на заблокированный аккаунт, 429 при превышении IP-rate-limit, 400 на невалидный body, refresh (успех/нет cookie/невалидный токен), logout (с cookie/без cookie, cookie всегда очищается)
- [x] Session-менеджмент — конкурентные сессии: `RefreshTokenServiceTest` дополнен тестами на `listActiveSessions` (маппинг, пустой список) и `revokeSession` (своя сессия/чужая/уже отозванная), `isKnownDevice` с null ip. Инвалидация при смене пароля: новый `PasswordResetServiceTest` — `resetPassword` отзывает весь refresh-token family (`RefreshTokenFamilyRevoker`) и денylist-ит access-токены (`TokenDenylistService`), плюс невалидный/использованный/просроченный токен сброса

### 2.3 Основные сущности домена
- [~] Repository-слой — интеграционные тесты через Testcontainers Postgres на реальные запросы (особенно кастомные `@Query`). Сделано: `UserRepositoryIT` (`user-service/src/test/java/com/pravoos/user/repository/`) — все кастомные `@Query`/derived-методы `UserRepository` (`findByEmail`, `findByEmailAndStatus`, `existsByEmail`, `existsByRole`, `countByRoleAndStatus[AndCreatedAtAfter]`, обе перегрузки `findByRoleAndStatusWithProfile`, `findByIdInWithProfile`, `findIdsByIdInAndDigestPushTrue`); `RefreshTokenRepositoryIT` — `findByTokenHash`, `existsByUserIdAndIpAddressAndRevokedAtIsNullAndExpiresAtAfter`, `findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc`, `findByIdAndUserId`, `revokeAllActiveByUserId` (bulk `@Modifying` update — понадобился `entityManager.clear()`, иначе первый-уровневый кеш возвращал устаревшие managed-сущности), `deleteByExpiresAtBefore`; `PasswordResetTokenRepositoryIT` — `findByTokenHash`, `invalidateActiveByUserId` (у этого `@Modifying` уже стоит `clearAutomatically = true`, так что доп. `entityManager.clear()` не понадобился), `deleteByExpiresAtBefore`; `LawyerApplicationRepositoryIT` (10 тестов) — `findByIdForUpdate` (pessimistic lock), обе order-by-submittedAt выборки (для детерминированного порядка submittedAt выставляется через reflection, т.к. это `@PrePersist`-поле без публичного сеттера), `countByStatus`, `existsByEmailAndStatus`, `findByEmailVerificationToken`, `findByStatusToken`, `findByEmailAndStatusAndEmailVerifiedFalse`, `clearExpiredVerificationTokens`, `deleteByEmail`. Остальные репозитории с `@Query` ещё не покрыты: `LawyerProfileRepository`, `ClientPortalInviteRepository`, `OrganizationInviteRepository`, `PushSubscriptionRepository`
- [~] Service-слой — unit с моками репозиториев на бизнес-правила. Сделано: `LawyerAccountEraserTest` (`user-service/src/test/java/com/pravoos/user/service/`) — `supports` (LAWYER/CLIENT/несуществующий пользователь), `erase` делегирует в `AdminService.deleteLawyer(userId, userId)` и не трогает `UserRepository`. `MfaServiceTest` — `status` (enabled/mandatory по роли ADMIN, не-mandatory для LAWYER, ProfileNotFoundException), `setup` (already-enabled guard, создание нового pending-секрета, переиспользование существующей pending-записи), `enable` (no-pending/already-enabled/invalid-code/success с confirmedAt), `disable` (mandatory-для-роли guard, not-enabled/invalid-code/success с удалением записи), `isMfaEnabled`, `verifyLoginCode` (нет записи/не включена/делегирование в TotpGenerator). `MfaChallengeServiceTest` — `createChallenge` (сохранение userId под токеном с TTL, разные токены), `resolve` (успех/`MFA_INVALID_CHALLENGE` при отсутствии), `registerFailedAttempt` (expire на первой попытке, инкремент без expire дальше, инвалидация challenge+attempts при достижении лимита), `invalidate` (удаление обоих ключей). `LoginAttemptServiceTest` — `remainingLockSeconds` (TTL есть/нет/null, fail-open/fail-closed при недоступности Redis), `recordFailure` (нормализация email в ключе, expiry на первой попытке и при отсутствии TTL, не сбрасывает expiry в пределах окна, ранний возврат при null от increment, блокировка аккаунта на maxAttempts с записью lock-ключа и удалением attempts-ключа, не блокирует ниже порога, fail-open/fail-closed при недоступности Redis), `reset` (удаление обоих ключей, проглатывание ошибки Redis). `OrganizationAccessGuardTest` — `requireMember` (найден/не найден → `NotOrganizationMemberException`), `requireManagerOrOwner` (MANAGER/OWNER проходят, MEMBER → `OrganizationAccessDeniedException`, не-участник → `NotOrganizationMemberException`), `requireOwner` (только OWNER проходит, MANAGER/MEMBER → `OrganizationAccessDeniedException`). `AdminServiceTest` — `getActiveLawyers` (маппинг полей профиля/null-профиль), `countActiveLawyers`, `deleteLawyer` (not-found/не-юрист → `LawyerNotFoundException` без побочных эффектов; успех: purge membership → delete+flush user → delete lawyer application по email → revoke access tokens → outbox `lawyer.deleted` с `orgCaseOwners`), `getClientStats`. `EmailVerificationServiceTest` — `generateToken` (64 hex-символа, уникальность), `tokenExpiry` (от конфигурации `verificationExpiryHours`), `verifyToken` (не найден/просрочен/expiry=null → `InvalidVerificationTokenException`; успех — verified=true, токен и expiry обнулены, save), `resendVerification` (no-op при rate-limit, no-op при отсутствии pending-неверифицированной заявки, успех — новый хэш-токен + expiry + save + publishEvent), `purgeExpiredVerificationTokens` (делегирование в repository, no-op при cleared=0). `IpRateLimiterTest`/`EmailRateLimiterTest` — оба: increment+expiry на первом запросе, без сброса expiry на последующих, true в пределах лимита, false при превышении, true при null от increment, fail-open при недоступности Redis; `EmailRateLimiterTest` дополнительно проверяет нормализацию email в ключе. Остальные сервисы без тестов: `PaymentService`, `SubscriptionProvisionerImpl`, `DeadlineNotificationEmailService`, `LawyerMembershipCleanupImpl`, `OrgMembershipProviderImpl`, `PortalAccessProviderImpl`, `TelegramLinkService`, `RefreshTokenFamilyRevoker`, `AiProcessingModeAdapter`, `OutboxPublisher`
- [ ] Controller-слой — `MockMvc` на валидацию входных DTO, коды ответов, маппинг исключений в HTTP-статусы

### 2.4 Кросс-cutting
- [ ] Обработчики глобальных исключений (`@ControllerAdvice`) — проверка маппинга каждого кастомного exception в правильный HTTP-код и тело ответа
- [ ] Границы модулей (`api` vs `internal`) — ArchUnit-тест, что `internal`-классы не импортируются извне пакета

---

## 3. Волна 1 — pravoos-common

Маленький модуль (10 классов), но именно сюда переехала PII-криптография — это shared-код, баг здесь бьёт по всем сервисам.

- [ ] `PiiEncryptor` — уже есть тест (`PiiEncryptorTest`, судя по git status он тоже переехал) — расширить edge cases: пустая строка, null, не-UTF8 данные, повторное шифрование одного значения (проверка на детерминированность/недетерминированность в зависимости от режима)
- [ ] `PiiCryptoHolder` — тест на инициализацию (правильный ключ), тест на ошибку при отсутствующем/некорректном ключе
- [ ] `PiiCryptoProperties` / `PiiCryptoException` (новые файлы, ещё без тестов) — валидация конфигурации, тест на выброс исключения при некорректных properties
- [ ] Тест на backward-compatibility: данные, зашифрованные до рефакторинга (перенос из ai-service), должны расшифровываться после переноса — это прямой риск при таком рефакторинге

---

## 4. Волна 2 — ai-service (520 классов — крупнейший модуль, делим на суб-волны)

61 тест уже есть — до начала работы прогнать `mvn -pl ai-service test` и зафиксировать baseline (какие пакеты уже покрыты), не дублировать.

Суб-волна 2a — ядро:
- [ ] `practice` domain (там уже есть `ClientPiiBackfillService` — раз он меняется сейчас, для него в первую очередь: unit на батчинг, идемпотентность бэкфилла, поведение при частичном сбое)
- [ ] `shared/security` (`PiiStringConverter`, `PiiCryptoConfig` — новый файл без теста) — конвертер JPA `AttributeConverter`: тест на round-trip шифрование/дешифрование через сущность, тест на null-поля

Суб-волна 2b — AI/LLM-интеграционная логика:
- [ ] Промпт-билдеры / оркестрация вызовов LLM — unit с моками клиента, без реальных вызовов к LLM API
- [ ] Обработка стриминга ответов (если есть) — тест на частичные chunks, обрыв соединения
- [ ] Ретраи/таймауты при вызове внешнего LLM — unit с фейковым клиентом, кидающим исключения

Суб-волна 2c — остальные пакеты по мере обнаружения (список уточнить после 2a/2b — 520 классов не разложить заранее без риска устареть).

---

## 5. Волна 2 — llm-service

29 классов, 2 теста.
- [ ] Контракт клиента к внешнему LLM-провайдеру — тест на сериализацию запроса/десериализацию ответа (без реального сетевого вызова — WireMock)
- [ ] Обработка ошибок провайдера (429, 5xx, timeout) — маппинг в доменные исключения
- [ ] Конфигурация моделей/роутинг между провайдерами, если есть

---

## 6. Волна 3 — api-gateway

12 классов, 2 теста.
- [ ] Роутинг-правила — тест на маршрутизацию запросов к нужному сервису по пути
- [ ] Фильтры (auth-forwarding, rate limiting, CORS) — unit на каждый фильтр
- [ ] Обработка недоступности downstream-сервиса (circuit breaker, если есть)

## 6.1 notification-service

53 класса, 5 тестов — второй по недопокрытости после ai-service/user-service в абсолютных числах.
- [ ] Шаблоны уведомлений — рендеринг, edge cases (отсутствующие переменные)
- [ ] Каналы доставки (email/push/sms — что реально используется) — unit с моками провайдера
- [ ] Идемпотентность отправки (повторная доставка одного события)
- [ ] Обработка событий из очереди (если Kafka/RabbitMQ) — тест на консьюмер с встроенным брокером (Testcontainers Kafka) или Embedded

## 6.2 pravoos-common-web, pravoos-observability

- [ ] `pravoos-common-web` (4 класса, 0 тестов) — вероятно общие web-конфиги/фильтры, покрыть unit-тестами на каждый компонент
- [ ] `pravoos-observability` — уже есть 3 теста, добить недостающее (метрики, трейсинг-конфигурация)

---

## 7. Волна 4 — frontend

vitest уже настроен, 8 тестовых файлов на весь фронт.
- [ ] `src/api` — тесты на клиентов (мокать fetch/axios), обработка ошибок сети
- [ ] `src/store` — unit-тесты стора (редьюсеры/экшены/селекторы)
- [ ] `src/hooks` — тесты кастомных хуков через `@testing-library/react`
- [ ] Ключевые страницы (`pages/cases`, `pages/clients`, `pages/billing`, `pages/portal`) — smoke-рендер + основные пользовательские сценарии
- [ ] Переиспользуемые компоненты `components/ui` — визуальные/поведенческие unit-тесты
- [ ] i18n — тест на отсутствие непереведённых ключей между локалями
- [ ] Рассмотреть E2E (Playwright) для 2-3 критичных сквозных сценариев (логин → создание дела → выставление счёта) — отдельное решение, не в рамках vitest

---

## 8. Метрики и критерии готовности

- Порог покрытия по строкам поднимать поэтапно: сейчас baseline ~5-10% → цель после волны 1: 40%+ на `user-service`/`pravoos-common` → после волны 2: 40%+ на `ai-service`/`llm-service` → итоговая цель по проекту: 70%+ line coverage на бэкенде, 60%+ на фронте
- Каждый PR с новым функционалом обязан приносить тесты на этот функционал (не откладывать в этот план)
- После каждого завершённого модуля — обновлять таблицу в начале файла и делать `/clear`

---

## 9. Как работаем по этому плану

1. Подтверждаешь порядок волн/модуль, с которого начинаем (по умолчанию: user-service, п.2.1, т.к. это активно меняющийся PII/consent-код)
2. Реализуем по одному пункту чек-листа за раз, не по всему модулю сразу
3. После завершения пункта — отмечаем чекбокс в этом файле
4. Каждые ~10 сообщений или по завершении модуля — `/clear` и продолжаем с чистого контекста, подгружая только нужные файлы
