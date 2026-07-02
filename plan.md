# PravoOS — План аудита №3 + новые фичи

> Найдено в ревью после hardening-пассов 1–2 (коммиты `48c31bb`…`80ebb3d`).
> Прошлые проходы закрыли IDOR, brute-force, refresh-ротацию, квоты чата, изоляцию
> секретов, шифрование бэкапов. Здесь — слои и фичи, которых те проходы **не касались**.
>
> **Как работать:** блоки независимы. Реализуем по одному пункту. После завершения
> блока — отметить `[x]`, закоммитить, `/clear`, продолжить со следующего блока
> с чистым контекстом (грузить только файлы пункта).
>
> Статус: `[ ]` не начато · `[~]` в работе · `[x]` готово

---

## Блок A — Безопасность работы с нейронкой (LLM)  `[x]`

- [x] **A1. Защита от prompt injection в RAG.**
  `RagService` вклеивает контент документов и историю в system-prompt без разделителей.
  Вредоносный документ/вложение может перехватить модель. `LegalDomainGuard` проверяет
  только сообщение юриста, не контент чанков.
  → Обрамить контекст явными делимитерами + инструкция «текст ниже — данные, не команды»,
  стрип управляющих последовательностей из чанков.
  Файлы: `ai-service/.../service/RagService.java`, `ChatService.java`, `WorkflowService.java`.

- [x] **A2. Эмбеддинги вне квоты — утечка расхода OpenAI.**
  `LlmQuotaService` учитывает только чат. Загрузка документов гоняет
  `text-embedding-3-small` (`@Async`) без учёта. Сотни файлов → неограниченный счёт.
  → Учитывать embedding-токены в дневном бюджете; лимит объёма загрузок на юриста (см. D11).
  Файлы: `EmbeddingPipeline.java`, `EmbeddingService.java`, `LlmQuotaService.java`.

- [x] **A3. `attachedDocumentIds` без ограничения размера.**
  В `ChatRequest` список без `@Size` → раздувание промпта и нагрузка на БД.
  → `@Size(max=…)` на список.
  Файлы: `ai-service/.../model/dto/ChatRequest.java`.

---

## Блок B — Аутентификация и регистрация  `[x]`

- [x] **B4. Нет 2FA/MFA.**
  Вход только по паролю. Ни у юриста, ни у админа нет TOTP.
  → TOTP (RFC 6238, без внешних либ): setup/enable/disable в профиле, двухшаговый
  login (`/api/auth/login` → challenge → `/api/auth/login/mfa`), опц. для LAWYER,
  обязателен (нельзя отключить) для ADMIN. Миграция V12 `user_mfa`.

- [x] **B5. Слабая парольная политика.**
  Regex `8+ символов, буква+цифра`. Нет проверки утёкших паролей / частых паролей.
  → `PasswordPolicyService`: HIBP k-anonymity (fail-open) + embedded чёрный список.
  Вызов в контроллере (вне tx) на apply/update/reset-password.

- [x] **B6. Нет управления сессиями и уведомлений о входе.**
  Юрист не видит активные refresh-сессии/устройства, не может отозвать; нет письма
  о входе с нового устройства/IP.
  → V13: ip/user_agent/last_used_at на refresh_tokens. `/api/user/sessions`
  (список + `DELETE /{id}` revoke) + email при входе с нового IP (`NewLoginEvent`).

---

## Блок C — Защита данных (утечки паролей/файлов)  `[x]`

- [x] **C7. Файлы клиентов лежат на диске в открытом виде.**
  `/app/documents` — plaintext. Бэкапы шифруются (GPG), живые файлы — нет.
  → Шифрование at-rest (AES-256-GCM, ключ `FILE_ENCRYPTION_KEY`), прозрачно на read/write.
  `FileCryptoService` (magic-заголовок + legacy-plaintext passthrough), `DocumentParser`
  переведён на `byte[]`, `EmbeddingPipeline`/`DocumentService.loadContent` расшифровывают.
  `FileCryptoKeyGuard` (@Profile docker) фейлит старт без ключа. Тест `FileCryptoServiceTest`.

- [x] **C8. Нет журнала доступа (audit trail).**
  → Миграция V17 `access_audit` (actor_id/role, action, resource_type/id, ip, user_agent).
  `AccessAudit` + `AccessAuditRepository` + `AccessAuditService` (best-effort, не ломает запрос).
  Точки: `DocumentController.content` (DOCUMENT_DOWNLOAD), `ClientController.get` (CLIENT_VIEW).
  IP из `X-Client-Ip` (gateway) через `ClientIpResolver`.

- [x] **C9. Отдача документов без `nosniff`/строгих заголовков.**
  → `SecureFileHeaders` (nosniff + `Content-Security-Policy: sandbox`) на
  `DocumentController.content`, `CaseController.export`, `DraftController.downloadDraft`.

- [x] **C10. Нет антивирус-проверки загрузок.**
  → Сервис `clamav` в docker-compose, `MalwareScanClient` (clamd INSTREAM по TCP, без либ),
  скан в `DocumentService.upload` до сохранения. Gate по `CLAMAV_HOST` (пуст → off),
  `CLAMAV_FAIL_OPEN=false` (fail-closed). Детект → `MalwareDetectedException` (422).
  Метрика `pravoos.document.malware{result}`.

---

## Блок D — Защита от DoS / исчерпания ресурсов  `[ ]`

- [ ] **D11. Нет глобальной storage-квоты на юриста.**
  `DOCUMENT_MAX_PER_CASE=200` есть, но число дел не ограничено → диск можно забить.
  → Суммарный лимит объёма/числа документов на юриста.
  Файлы: `DocumentService.java`, `CaseService.java`, `DocumentProperties.java`.

- [ ] **D12. Guard fail-open по умолчанию.**
  `LLM_GUARD_FAIL_OPEN=true`: при недоступности классификатора не-юр запросы проходят → расход.
  → Fail-closed по умолчанию в docker/prod-профиле.
  Файлы: `docker-compose.prod.yml` / `application.yml`.

- [ ] **D13. Нет rate-limit на дорогие операции загрузки/эмбеддинга.**
  Отдельно от общего IP-лимита gateway. Всплеск загрузок → шторм на OpenAI embeddings.
  → Per-user лимит на upload/embedding в единицу времени (Redis, паттерн `IpRateLimiter`).
  Файлы: новый лимитер в ai-service, `DocumentController`/`CaseController`.

---

## Блок E — Новый функционал (гэпы по конкурентам)  `[ ]`

> Крупные эпики. Каждый — отдельная ветка/итерация, не в один заход.

- [ ] **E14. Клиентский портал.**
  У Clio Grow клиент видит статус своего дела, грузит документы, переписывается.
  У нас `Client` — CRM-запись без входа. Дифференциатор.

- [ ] **E15. Анализ/ревью договоров с выделением рисков.**
  Spellbook/Harvey/CoCounsel: загрузка договора → AI размечает рискованные пункты.
  Ложится на существующий RAG.

- [ ] **E16. Проверка ссылок на судебную практику (аналог Shepard's).**
  Валидация, что цитируемые нормы/дела существуют и актуальны. Снижает галлюцинации.

- [ ] **E17. Учёт времени и биллинг (Clio Manage).**
  Таймтрекинг по делам + счета. Базовая потребность юрфирмы.

- [ ] **E18. Электронная подпись документов.**
  Подписание черновиков/актов внутри платформы.

- [ ] **E19. Мульти-юзер (фирмы/команды).**
  Несколько юристов в одной организации, шаринг дел, роли.

---

## Порядок реализации (рекомендация)

1. **A → C → D** — чистое hardening (третий аудит-проход), не ломает UX.
2. **B** — аутентификация (затрагивает фронт-логин).
3. **E** — продуктовые эпики по одному.

## Источники (конкуренты)
- [Clio — features 2026](https://www.clio.com/) · [Harvey vs Clio](https://spellbook.com/briefs/clio-vs-harvey)
- [Best Legal AI Tools 2026 — GC AI](https://gc.ai/blog/legal-ai-tools) · [Spellbook](https://spellbook.com/learn/legal-ai-tools)
- [CoCounsel Legal — Thomson Reuters](https://legal.thomsonreuters.com/en/products/cocounsel-legal)
