# Bugs — ревью фиче-коммитов (plan.md п.2/5/6/7/8/9/10 + вынос llm-service)

Статус: 🔴 не начато · 🟡 в работе · ✅ исправлено · ⚪ осознанно оставлено

---

## 🔴 Критичные

### 1. `CaseAnalyticsService.generateAnalysis` — LLM-вызов внутри `@Transactional` ✅
`ai-service/.../practice/internal/service/CaseAnalyticsService.java:76-94`
Метод `@Transactional` (write), внутри `legalAiPort.analyzeCase(...)` делает embedding→llm-service + `llmClient.complete` + `recordUsage` в Redis (`LegalAiPortImpl.analyzeCase:92-103`). DB-connection держится весь round-trip (10-30с).
**Правило (MEMORY):** внешний I/O — ВНЕ транзакции. Паттерн: read-tx → внешний вызов → write-tx.
**Фикс:** сбор данных в read-tx, LLM без tx, сохранение `CaseAnalysis` в отдельном write-tx (эталон — `WorkflowService.saveResponse`).

### 2. `DraftService.generate` — LLM-вызов внутри `@Transactional` ✅
`ai-service/.../practice/internal/service/DraftService.java:59-81`
`@Transactional` + `legalAiPort.answerForCase` (embedding+LLM+Redis) внутри. Рядом `refine()` (стр.120) сделан БЕЗ `@Transactional` правильно.
**Фикс:** привести `generate` к паттерну read → LLM (без tx) → write.

### 3. `case_analyses` не чистится при удалении юриста ✅
`LawyerDataCleanupService.purgeRelationalData:118-129` не трогает `case_analyses`. `CaseAnalysisRepository` без delete-метода, V27 без FK на cases → строки-сироты с `lawyer_id` + AI-контент. GDPR/purge-дыра.
**Фикс:** `deleteByLawyerId` (подзапрос по case_id как в `CasePartyRepository`) + вызов в purge.

### 4. `workflow_runs` не чистится при удалении юриста ✅
`WorkflowRunRepository` без delete, таблица с `lawyer_id`, в purge отсутствует. Сироты.
**Фикс:** `deleteByLawyerId` + вызов в purge.

### 5. `workflow_definitions` не чистится при удалении юриста ✅
`created_by = lawyerId`, пользовательские (не системные) процессы остаются после удаления автора.
**Фикс:** удалять несистемные definition'ы юриста в purge.

---

## 🟠 Средние

### 6. Регрессия таймаута guard-классификатора ✅
`LegalDomainGuard.classify:77` → `RemoteLlmClient.complete` с `READ_TIMEOUT=180s` (`RemoteLlmClient.java:34`). Раньше guard имел отдельный клиент 10с. Guard на КАЖДОМ сообщении синхронно → тормозящий OpenAI вешает запрос до 180с.
**Фикс:** короткий таймаут для guard (отдельный путь/профиль).

### 7. Гонка при генерации аналитики → 500 ✅
`CaseAnalyticsService.generateAnalysis:96-103` (`findByCaseId → update|new`). Двойной сабмит → оба INSERT → `uq_case_analyses_case` (V27) → `DataIntegrityViolationException` (500).
**Фикс:** `@Version`/обработка конфликта/upsert.

### 8. Множественные LLM-шаги workflow за одну проверку квоты ✅
`WorkflowExecutionService.run:57` — `assertWithinQuota` один раз, дальше N токеноёмких шагов. Пробивает дневной бюджет в N раз.
**Фикс:** проверять квоту перед каждым LLM-шагом.

### 9. `CaseAnalysis.touch()` — время в TZ сервера ✅
`CaseAnalysis.java:47-49`: `LocalDateTime.now()` без `ZoneOffset.UTC` (остальные сущности пишут UTC).
**Фикс:** `LocalDateTime.now(ZoneOffset.UTC)`.

### 10. Рассогласование таймаутов в стриме llm-service ✅
`OpenAiRestClientConfig.java:23` responseTimeout=120s (в т.ч. стрим), а SseEmitter=180s и `RemoteLlmClient` read=180s. Стрим может оборваться на 120с.
**Фикс:** согласовать бюджет таймаутов.

---

## 🟡 Мелкие / стилевые

### 11. `WorkflowExecutionService.run` без транзакции → застрявшие RUNNING ⚪ (отложено)
`WorkflowExecutionService.java:56-97`. Краш между save(RUNNING) и финальным save → run навсегда RUNNING. Плюс частичные сайд-эффекты при падении шага.
**Решение:** осознанно отложено — нужен отдельный scheduled-sweep «зависших» RUNNING. Per-step падения уже ловятся в `run()` и помечают FAILED; риск только при краше JVM между save. Заводим отдельной задачей.

### 12. `parseIsoDate` глотает `Exception` ✅
`ApiArbitrCaseProvider.java:159-163` — `catch (Exception)`.
**Фикс:** ловить `DateTimeParseException`.

### 13. `replaceParties` перетирает всех участников на каждом опросе ✅
`ArbitrSyncService.java:92-100` — delete+reinsert каждые 6ч даже без изменений.
**Фикс:** diff по (name, role).

### 14. `daysToNextHearing` в TZ сервера ✅
`CaseAnalyticsService.java:151` — `LocalDate.now()` без зоны.
**Фикс:** UTC.

### 15. Пустой ответ guard не кэшируется ⚪ (не баг)
`LegalDomainGuard.classify:79-83` — fail-open verdict не кладётся в кэш.
**Решение:** оставлено намеренно. Кэшировать fail-open verdict нельзя — это продлило бы обход guard на 1ч после кратковременного сбоя OpenAI. Повторная классификация после восстановления сервиса — верное поведение.
