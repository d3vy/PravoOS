# PravoOS — ревью новых фич (шифрование PII, RAG/табличный разбор, saved views, таймер, DataTable)

> Дата ревью: 2026-07-24. Охват: последние коммиты по `plan.md` / `inter.md`.
> Изменения в `user-service` — только форматирование (переносы, порядок импортов), функциональных правок нет.
> Сортировка — по серьёзности.

## Шифрование PII (ai-service)

### 1. [HIGH] Поиск дел по имени клиента полностью сломан — ✅ ИСПРАВЛЕНО
`CaseRepository.java:129,143,153,169,181` — `LOWER(cl.name) LIKE :pattern`, но `clients.name` теперь шифруется рандомным IV (`Client.java:22`, `PiiEncryptor`). Hibernate не применяет конвертер к `LIKE`, а из-за GCM+random IV шифртекст каждый раз разный → совпадение невозможно. Поиск по клиенту тихо перестал находить. Поиск по `title`/`description` работает (ветки `OR`), поэтому регрессия незаметна.

**Решение (реализовано):** совпадения по имени резолвятся в app-слое, где JPA-конвертер расшифровывает имена.
- Новый util `shared/util/ClientNameMatch` — регистронезависимый подстрочный матч по карте `id→name`, sentinel `UUID(0,0)` при отсутствии совпадений (паттерн как у `orgIdsOrSentinel`).
- В `CaseRepository.search` (List+Page) и `findVisible` ветка `EXISTS(... cl.name LIKE)` заменена на `c.clientId IN :clientIds`.
- `CaseService.findByLawyer` грузит имена своих клиентов (`ownClientNames`) и передаёт совпавшие id; `SearchService.searchCases` переиспользует уже загруженную карту `clientNamesFor`.
- Охват — только клиенты текущего юриста (по решению): орг-дела чужого юриста по имени его клиента не находятся (приватнее; глобальный поиск и так скрывал чужие имена).
- Сохранён подстрочный UX, новой колонки/backfill не требуется.
- Тесты: `ClientNameMatchTest` (6) + существующие `CaseServiceOwnershipTest`/`CaseServiceVisibilityTest` — зелёные.

### 2. [MED] Тихий откат в plaintext при отсутствии ключа — ✅ ИСПРАВЛЕНО
`PiiEncryptor.java` — если `pii.crypto.key` не задан, `encrypt()` писал открытым текстом, только `log.warn`.
**Решение:** флаг `pii.crypto.required` (env `PII_ENCRYPTION_REQUIRED`, default false). При `required=true` и отсутствии активного ключа `PiiEncryptor` кидает `IllegalStateException` на старте (приложение не поднимается). При `false` — прежнее поведение (warn + plaintext) для дев-режима. Тест: `failsFastWhenRequiredButNoKeyConfigured`.

### 3. [MED] Нет версионирования/ротации ключа — ✅ ИСПРАВЛЕНО
`decrypt()` при смене ключа кидал `DocumentProcessingException` на обычном чтении сущности → падали любые `SELECT` клиентов.
**Решение:** мультиключевой `PiiEncryptor` с key-id в маркере.
- Новый формат: `pii2:<keyId>:<base64(iv+ct)>`. Ключи задаются как `pii.crypto.keys.<id>`, активный — `pii.crypto.active-key-id` (env `PII_ACTIVE_KEY_ID`).
- `decrypt` выбирает ключ по id из маркера → одновременно живут старый и новый ключи, ротация без даунтайма. Легаси `pii1:` читается через ключ `legacy` (или единственный/активный). Одиночный `pii.crypto.key` маппится в id `legacy` — обратная совместимость с текущим `.env`.
- Отсутствие ключа для сохранённого id → явная `DocumentProcessingException` с понятным сообщением, а не тихое падение.
- Тесты: `tagsCiphertextWithActiveKeyId`, `rotatedKeyStillDecryptsDataEncryptedWithOldKey`, `failsWhenKeyForStoredKeyIdIsMissing`, `failsWhenActiveKeyIdHasNoMatchingKey`.

### 4. [MED] Миграция V37 не бэкфиллит данные — ✅ ИСПРАВЛЕНО
`V37` менял только тип колонок на TEXT; существующие строки оставались в открытом виде.
**Решение:** `ClientPiiBackfillService` — по флагу `pii.crypto.backfill-on-start` (env `PII_BACKFILL_ON_START`) на `ApplicationReadyEvent` дошифровывает строки `clients` (name/phone/email/notes).
- Через native SQL (JPA-конвертер не годится: dirty-check идёт по расшифрованному атрибуту и UPDATE не триггерит).
- Идемпотентно: строки, уже зашифрованные активным ключом (`isEncryptedWithActiveKey`), пропускаются; plaintext и `pii1:`/старый ключ → `encrypt(decrypt(...))` активным ключом. Т.е. заодно завершает ротацию из #3.
- Пропускается, если шифрование выключено (нет ключа). Транзакция через self-invocation (`@Lazy self`), как в `LawyerDataCleanupService`.

### 5. [MED] INN не шифруется
`Client.java:38-39` — `inn` оставлен открытым, хотя для ИП/физлиц это персональные данные того же уровня, что имя/телефон. Непоследовательное покрытие PII.

### 6. [LOW] Гонка инициализации холдера
`PiiCryptoHolder` + JPA-конвертер: если любая сущность `Client` грузится до `@PostConstruct register()` (Flyway-callback, `@PostConstruct` другого бина, прогрев кэша) → `IllegalStateException: PiiEncryptor is not initialized`. Лучше лениво резолвить бин из контекста.

## RAG / табличный разбор

### 7. [MED] Квота LLM проверяется один раз на весь разбор
`TabularReviewService.create():70` вызывает `assertWithinQuota` единожды, а `TabularReviewRunner` затем гоняет до 15 документов × запрос к LLM, списывая токены пост-фактум. Один разбор пробивает лимит насквозь. Нужна проверка квоты перед каждым документом внутри процессора.

### 8. [MED] `budget()` отбрасывает самые релевантные фрагменты
`TabularReviewDocumentProcessor.java:89-91` сортирует фрагменты по `chunkIndex` (порядок в документе), затем режет по `contextMaxChars`, сохраняя первые. Высокорелевантные чанки (высокий score), стоящие ближе к концу документа, выкидываются. Обрезать надо по score, а по chunkIndex — только упорядочивать уже отобранное.

### 9. [MED] N запросов ретривала на разбор
`retrieveFragments():79-81` вызывает `retrieveInDocument` отдельно на каждый вопрос → вопросов×документов эмбеддинг-поисков (до 8×15=120), причём токены эмбеддингов в квоту не пишутся (`recordUsage` только за completion). Стоимость/латентность недооценены.

### 10. [LOW] Наивный `extractJson`
`TabularReviewDocumentProcessor.java:217-222` берёт первую `{` и последнюю `}`. Любой префикс/суффикс модели с фигурными скобками захватит мусорный диапазон → `TabularReviewFailedException`. Лучше парсить с fallback по code-fence.

### 11. [LOW] `filledCells` вводит в заблуждение
`TabularReviewDto.java:37` считает `cells.size()`, но `NOT_FOUND`-ячейки тоже сохраняются как cells → прогресс-бар покажет «заполнено», хотя ответов нет. Для FAILED-документов ячейки не создаются вовсе → `filledCells < totalCells` навсегда, даже у завершённого PARTIAL-разбора прогресс не дойдёт до 100%.

### 12. [LOW] Тихая деградация confidence
`ReviewAnswerConfidence.fromString():default -> LOW` — неизвестное значение («CERTAIN», «Unknown») молча становится LOW. Стоит хотя бы логировать неожиданные значения.

### 13. [LOW] Данные документа в system-роли
`TabularReviewPrompt.SYSTEM_PROMPT` вставляет содержимое документа прямо в системный промпт (`answerCells():117` — `complete(systemPrompt, List.of(), USER_MESSAGE)`). Фенсы есть, но помещать недоверенный текст в system, а не в user-сообщение, ослабляет защиту от prompt injection.

## Тайм-трекинг / новые фичи

### 14. [MED] Пассивный сбор пишет ставку 0
`usePassiveTimeCapture.ts:73` создаёт запись с `hourlyRate: 0`. `TimeEntryService.create():54` сохраняет как есть (дефолтной ставки юриста нет). «Деньги на столе» (`DashboardService.buildMoneyOnTable`) и счета не учитывают это время — фича собирает часы, которые нельзя выставить.

### 15. [MED] Та же проблема у хоткея/таймера
`useHotkeys.ts:65` и `GlobalTimer` стартуют таймер с `hourlyRate: 0`. Корень — `TimeEntryService.startTimer():112` не подставляет дефолтную ставку. Нужна ставка по умолчанию (профиль юриста/дела) при 0/`null`.

## Frontend DataTable

### 16. [LOW] Пустые значения всплывают наверх при сортировке по убыванию
`DataTable.tsx:61-68` — `null/undefined` всегда возвращает `+1` («в конец»), но при `desc` результат инвертируется (`:92 -result`) → пустые ячейки оказываются сверху. Обычно ждут, что пустые всегда снизу независимо от направления.

### 17. [LOW] Хоткеи ловят клавиши слишком широко
`useHotkeys.ts:103` — `n` → `/cases?new=1` срабатывает на любой странице (не только на списке дел); `g`-префикс глотает следующую немаппленную клавишу без индикации.

## SavedView

### 18. [LOW] Гонка на уникальном имени → 500 вместо 409
`SavedViewService.create():41` проверяет `existsBy...Name`, но при параллельном создании двух видов с одним именем сработает уникальный констрейнт БД (`uq_saved_views_owner_scope_name`) → `DataIntegrityViolationException` (500), а не `SavedViewNameTakenException` (409). Нужна обёртка на нарушение констрейнта.

### 19. [LOW] Повторный шаринг требует явный orgId у мультиorg-юриста
`SavedViewService.applySharing():78` — `singleOrgOrNull` возвращает `null`, если у юриста >1 организации и `orgId` не передан → `OrganizationAccessException`. Если UI при `sharedWithTeam=true` не всегда шлёт `orgId`, шаринг падает. Стоит явно валидировать на уровне DTO.

### 20. [LOW] Длина имени вида проверяется только в DTO
`SavedView.name` — `length=80`, `SavedViewService.update()` делает `request.name().trim()` без обрезки; полагается целиком на `@Size` в `UpdateSavedViewRequest`. Дублирующий guard в сервисе не помешает.

---

## Приоритеты
- **Срочно:** №1 (поиск по клиенту сломан сейчас), №2–4 (шифрование PII не защищает старые данные и хрупко к ротации ключа), №7 (обход квоты LLM), №14–15 (таймер копит небиллируемое время).
- **Крупный блок под отдельную ветку:** PII-шифрование целиком — blind index для поиска + backfill-миграция + key-id в маркер.
