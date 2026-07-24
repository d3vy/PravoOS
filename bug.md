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

### 5. [MED] INN не шифруется — ✅ ИСПРАВЛЕНО
`Client.java` — `inn` оставлен открытым, хотя для ИП/физлиц это персональные данные того же уровня, что имя/телефон.
**Решение:** `inn` помечен `@Convert(PiiStringConverter)`, колонка → TEXT (миграция `V40__clients_inn_pii_encryption.sql`). `ClientPiiBackfillService` дошифровывает `inn` наравне с name/phone/email/notes. INN нигде не участвует в `WHERE`/поиске (только чтение/экспорт) — регрессии поиска нет.

### 6. [LOW] Гонка инициализации холдера — ✅ ИСПРАВЛЕНО
`PiiCryptoHolder` + JPA-конвертер: если любая сущность `Client` грузилась до `@PostConstruct register()` → `IllegalStateException`.
**Решение:** `PiiCryptoHolder` реализует `ApplicationContextAware` и резолвит `PiiEncryptor` лениво через `getBean` (с кэшированием), `@PostConstruct register()` в `PiiEncryptor` удалён. `getBean` форсирует создание бина по требованию — окно гонки сжато до захвата ссылки на контекст.

## RAG / табличный разбор

### 7. [MED] Квота LLM проверяется один раз на весь разбор — ✅ ИСПРАВЛЕНО
`TabularReviewService.create()` проверял квоту единожды, а раннер гонял до 15 документов.
**Решение:** `assertWithinQuota(lawyerId)` вызывается в начале `process()` для каждого документа — до ретривала (эмбеддинги №9 тоже не тратятся при исчерпанной квоте) и до LLM-запроса. Превышение → документ помечается FAILED, `recordUsage`/`complete` не вызываются. Тест `exceededQuotaFailsDocumentBeforeSpendingRetrieval`.

### 8. [MED] `budget()` отбрасывает самые релевантные фрагменты — ✅ ИСПРАВЛЕНО
`budget()` сортировал по `chunkIndex` и резал первые, выкидывая высокорелевантные чанки из конца документа.
**Решение:** сначала сортировка по `score` (убыв.) и обрезка по `contextMaxChars`, затем отобранное упорядочивается по `chunkIndex` (для читаемого контекста и стабильной нумерации цитат).

### 9. [MED] N запросов ретривала на разбор — ✅ ИСПРАВЛЕНО
`retrieveInDocument` вызывался отдельно на каждый вопрос, токены эмбеддингов не учитывались.
**Решение:** сигнатура → `retrieveInDocument(List<queries>, topK, documentId)`, возвращает `DocumentChunkMatches(matches, embeddingTokens)`. Один батч-эмбеддинг всех вопросов (`EmbeddingService.embedBatch`), поиск переиспользует готовый вектор (`HybridSearchService.search(query, embedding, …)`), дедуп чанков по максимальному score. Эмбеддинг-токены пишутся в квоту (`recordTokenUsage`). Пустые/бланк-вопросы отфильтровываются.

### 10. [LOW] Наивный `extractJson` — ✅ ИСПРАВЛЕНО
Первая `{` / последняя `}` захватывали мусорный диапазон при обрамляющей прозе со скобками.
**Решение:** сначала распаковка code-fence ```` ```json ```` (regex, DOTALL), затем скан первого сбалансированного `{…}`-объекта с учётом строковых литералов и экранирования (`balancedObject`); fallback на весь ответ, если во фрагменте фенса объекта нет. Тест `extractsJsonWrappedInCodeFenceWithSurroundingProse`.

### 11. [LOW] `filledCells` вводит в заблуждение — ✅ ИСПРАВЛЕНО
`TabularReviewDto.java` считал `cells.size()`, но `NOT_FOUND`-ячейки тоже сохраняются как cells → прогресс-бар «заполнено», хотя ответов нет; для FAILED-документов ячеек нет вовсе.
**Решение:** `filledCells` считает только реально отвеченные ячейки (`confidence != NOT_FOUND`) — честная метрика покрытия. NOT_FOUND и провалившиеся документы дают 0 и не раздувают счётчик; `totalCells` остаётся `documentCount*questionCount`. Тесты: `TabularReviewDtoTest` (отвеченные vs NOT_FOUND, все документы упали → 0).

### 12. [LOW] Тихая деградация confidence — ✅ ИСПРАВЛЕНО
`ReviewAnswerConfidence.fromString():default -> LOW` глотал неизвестное значение («CERTAIN», «Unknown»).
**Решение:** явная ветка `LOW`/`НИЗКАЯ`, а неизвестный вход логируется `log.warn` перед fallback на LOW. Тест: `ReviewAnswerConfidenceTest`.

### 13. [LOW] Данные документа в system-роли — ✅ ИСПРАВЛЕНО
`TabularReviewPrompt.SYSTEM_PROMPT` вставлял содержимое документа прямо в системный промпт.
**Решение:** промпт разделён — `buildSystemPrompt()` содержит только инструкции и JSON-схему; `buildUserMessage(title, fragments, questions)` собирает недоверенный контент документа (с фенсами и sanitize) и уходит user-ролью в `complete(system, [], userMessage)`. Тест: `sendsDocumentContentInUserMessageNotSystemPrompt` — контент в user, не в system.

## Тайм-трекинг / новые фичи

### 14. [MED] Пассивный сбор пишет ставку 0
`usePassiveTimeCapture.ts:73` создаёт запись с `hourlyRate: 0`. `TimeEntryService.create():54` сохраняет как есть (дефолтной ставки юриста нет). «Деньги на столе» (`DashboardService.buildMoneyOnTable`) и счета не учитывают это время — фича собирает часы, которые нельзя выставить.

### 15. [MED] Та же проблема у хоткея/таймера
`useHotkeys.ts:65` и `GlobalTimer` стартуют таймер с `hourlyRate: 0`. Корень — `TimeEntryService.startTimer():112` не подставляет дефолтную ставку. Нужна ставка по умолчанию (профиль юриста/дела) при 0/`null`.

## Frontend DataTable

### 16. [LOW] Пустые значения всплывают наверх при сортировке по убыванию — ✅ ИСПРАВЛЕНО
`DataTable.tsx:61-68` — `null/undefined` всегда возвращает `+1` («в конец»), но при `desc` результат инвертируется (`:92 -result`) → пустые ячейки оказываются сверху. Обычно ждут, что пустые всегда снизу независимо от направления.
**Решение:** пустые значения (`null`/`undefined`) обрабатываются в `sortRows` до применения направления сортировки — сравниваются между собой отдельно (`isEmpty`) и всегда уходят в конец, направление `asc/desc` инвертирует только сравнение непустых значений. `compareValues` больше не занимается null-логикой. Тест: `always keeps empty values last regardless of sort direction`.

### 17. [LOW] Хоткеи ловят клавиши слишком широко — ✅ ИСПРАВЛЕНО
`useHotkeys.ts:103` — `n` → `/cases?new=1` срабатывает на любой странице (не только на списке дел); `g`-префикс глотает следующую немаппленную клавишу без индикации.
**Решение:** `n` теперь проверяет `CASES_LIST_ROUTE` (`/cases` или `/cases/`) перед навигацией — на других страницах клавиша не перехватывается. При входе в `g`-префикс показывается `toast.info(t('hotkeys.gPrefixActive'))` («Нажмите d, c или k…») как индикация ожидания второй клавиши.

## SavedView

### 18. [LOW] Гонка на уникальном имени → 500 вместо 409 — ✅ ИСПРАВЛЕНО
`SavedViewService.create():41` проверяет `existsBy...Name`, но при параллельном создании двух видов с одним именем сработает уникальный констрейнт БД (`uq_saved_views_owner_scope_name`) → `DataIntegrityViolationException` (500), а не `SavedViewNameTakenException` (409). Нужна обёртка на нарушение констрейнта.
**Решение:** `save()` в `create()`/`update()` обёрнут в `saveOrThrowNameTaken()` — перехватывает `DataIntegrityViolationException` и перебрасывает как `SavedViewNameTakenException(name)` (409, тот же код `SAVED_VIEW_NAME_TAKEN`, что и при обычной пред-проверке). Тест: `concurrentNameRaceIsReportedAsNameTaken`.

### 19. [LOW] Повторный шаринг требует явный orgId у мультиorg-юриста — ✅ ИСПРАВЛЕНО
`SavedViewService.applySharing():78` — `singleOrgOrNull` возвращает `null`, если у юриста >1 организации и `orgId` не передан → `OrganizationAccessException`. Если UI при `sharedWithTeam=true` не всегда шлёт `orgId`, шаринг падает. Стоит явно валидировать на уровне DTO.
**Решение:** случай «orgId не передан и однозначно не выводится» (мультиorg-юрист) отделён от случая «orgId передан, но недоступен» — первый теперь кидает новый `SavedViewOrgRequiredException` (400, `SAVED_VIEW_ORG_REQUIRED`, «Укажите организацию: юрист состоит в нескольких организациях»), второй по-прежнему `OrganizationAccessException` (403). Фолбэк на единственную организацию юриста (без orgId в запросе) не тронут — это штатный путь, покрытый `sharedViewFallsBackToTheSingleOrganizationOfTheCaller`. Тест: `sharingWithAmbiguousOrganizationRequiresExplicitOrgId`.

### 20. [LOW] Длина имени вида проверяется только в DTO — ✅ ИСПРАВЛЕНО
`SavedView.name` — `length=80`, `SavedViewService.update()` делает `request.name().trim()` без обрезки; полагается целиком на `@Size` в `UpdateSavedViewRequest`. Дублирующий guard в сервисе не помешает.
**Решение:** `create()`/`update()` вызывают общий `truncateName()` (trim + обрезка до `MAX_NAME_LENGTH = 80`) вместо голого `.trim()` — сервис не полагается только на bean-валидацию DTO.

---

## План: осталось 5 пунктов (16–20) — 1 сессия (✅ ГОТОВО — все пункты закрыты)

Готово: №1–6 (PII), №7–13 (RAG-ядро + RAG-качество), №14–15 (таймер). Ниже — оставшееся,
по модулям, чтобы каждая сессия шла с `/clear` и минимальным набором файлов.

### Сессия A — RAG-ядро: ретривал + квота (№7, 8, 9, 10) — ✅ ГОТОВО
Один слой (`ai-service`, табличный разбор), пункты связаны по коду.
- **№9 [MED] + №8 [MED] — уже в работе (незакоммичено):** батч-ретривал
  `retrieveInDocument(List<queries>)` + учёт эмбеддинг-токенов в квоту; `budget()` режет
  по `score`, упорядочивает по `chunkIndex`. Доделать: обновить/дописать тесты
  (`TabularReviewDocumentProcessorTest`, ретривал), прогнать `spotless:check` + `mvn verify`.
- **№7 [MED] — обход квоты:** `assertWithinQuota` вызывается один раз на весь разбор, а
  прогоняется до 15 документов. Проверять квоту перед каждым документом внутри процессора.
- **№10 [LOW] — наивный `extractJson`:** первая `{` / последняя `}` ловит мусор. Fallback по
  code-fence ```` ```json ````, затем баланс скобок.
Файлы: `TabularReviewDocumentProcessor`, `DocumentRetrieval(Impl)`, `HybridSearchService`,
`TabularReviewRunner/Service` (квота), их тесты.

### Сессия B — RAG-качество: прогресс, confidence, prompt-injection (№11, 12, 13) — ✅ ГОТОВО
- **№11 [LOW] — `filledCells` врёт:** `cells.size()` включает `NOT_FOUND`; FAILED-документы
  ячеек не создают → 100% недостижим. Считать по реально отвеченным / завести знаменатель
  корректно для PARTIAL.
- **№12 [LOW] — тихая деградация confidence:** `fromString():default -> LOW` глотает
  неизвестные значения. Логировать неожиданный вход.
- **№13 [LOW] — данные документа в system-роли:** контент документа уходит в системный
  промпт. Перенести недоверенный текст в user-сообщение (`complete`).
Файлы: `TabularReviewDto`, `ReviewAnswerConfidence`, `TabularReviewPrompt`,
`TabularReviewDocumentProcessor`, их тесты.

### Сессия C — Frontend + SavedView (№16, 17, 18, 19, 20) — ✅ ГОТОВО
Мелкие независимые правки на стыке FE и `SavedViewService`.
- **№16 [LOW]:** `DataTable.tsx` — пустые значения всегда вниз независимо от `asc/desc`.
- **№17 [LOW]:** `useHotkeys.ts` — `n` ограничить страницей списка дел; индикация `g`-префикса.
- **№18 [LOW]:** `SavedViewService.create()` — ловить нарушение uq-констрейнта →
  `SavedViewNameTakenException` (409) вместо 500.
- **№19 [LOW]:** шаринг у мультиorg-юриста — валидировать `orgId` на уровне DTO при
  `sharedWithTeam=true`.
- **№20 [LOW]:** `SavedViewService.update()` — дублирующий guard длины имени (trim + обрезка).
Файлы: `DataTable.tsx`, `useHotkeys.ts`, `SavedViewService`, DTO SavedView, их тесты.
