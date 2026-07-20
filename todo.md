# PravoOS — TODO / Roadmap

Идеи по усилению конкурентоспособности и UX. Инфраструктура под большинство пунктов уже есть — переиспользуем, а не строим с нуля.

Легенда усилий: **S** = дни · **M** = 1–2 недели · **L** = больше.

---

## Быстрые UX-победы (S)

- [x] **1. ⌘K Command Palette.** ✅ Сделано. Глобальный поиск + действия: прыжок в дело/беседу/документ (live-поиск через `searchApi.global`), навигация по всем разделам, «Новое дело», смена темы. Хоткей ⌘K/Ctrl+K, стрелки/Enter/Esc, триггер-пилюля в навбаре.
  Файлы: `store/commandPaletteStore.ts`, `components/command/CommandPalette.tsx`, монтаж в `LawyerLayout`, триггер в `Navbar`.

- [x] **2. Дайджест на Dashboard.** ✅ Сделано (фронт-виджет). Приветствие по времени суток + дата + фокус дня («сегодня к сроку N дедлайнов» / «на неделе M») + пилюли активных дел/задач. Всё из существующего `DashboardResponse`.
  Файл: `pages/dashboard/DashboardPage.tsx` (`DigestBanner`).
  ⏳ Осталось (отдельно, backend): `@Scheduled` утренний **push**-дайджест через `PushMessageFactory`/`NotificationDispatcher`.

- [x] **3. Bulk-действия и сохранённые виды на «Делах».** ✅ Сделано. Мультивыбор (чекбоксы + «выбрать все на странице»), плавающая панель «Сменить статус» (batch через `updateStatus`), сохранённые виды (статус+поиск+организация) в localStorage с активной подсветкой.
  Файл: `pages/cases/CasesPage.tsx`.

### Доделать по быстрым победам (что осознанно не вошло)

- [ ] **Backend push-дайджест (#2).** `@Scheduled` утренняя рассылка «Сегодня: N заседаний, M дедлайнов, K неоплаченных счетов» через `NotificationDispatcher`/`PushMessageFactory`. Нужен агрегат по всем юристам (не только текущий `DashboardController`, он per-request) + прежпочтения (в матрицу prefs добавить `digest_push`).
- [ ] **Клиенты и счета в ⌘K и глобальном поиске.** Сейчас `SearchController`/`searchApi.global` ищет только дела/беседы/документы. Добавить клиентов (и, по желанию, счета) → палитра сможет прыгать и на них. Backend: расширить `SearchService`.
- [ ] **Действие «Начать таймер» в ⌘K.** Было в исходном плане пункта #1. Требует выбора дела (таймер привязан к делу) — либо двухшаговая команда, либо запуск на активном деле из карточки.
- [ ] **Bulk + сохранённые виды на «Клиентах».** План #3 был про «дела/клиентов» — сделаны только дела. `ClientsPage` — тот же паттерн (мультивыбор, bulk-удаление с `?cascade`, сохранённые виды).
- [ ] **Bulk «назначить/архивировать» (#3).** Сейчас массово меняется только статус. Массовое «сменить организацию» (`changeOrg`) / «передать владельца» (`transferOwner`) — переиспользуют готовые эндпоинты.
- [ ] **Vitest на новые компоненты.** Покрыть `CommandPalette` (фильтрация действий, клавиатура, скоуп поиска) и логику `DigestBanner`/`plural`. Фронт-тесты в проекте уже есть (`vitest`).
- [ ] **⌘K на мобильных / для админа.** Палитра монтируется только в `LawyerLayout` и хоткей недоступен на тач-устройствах. При желании — мобильный триггер и версия для `AdminLayout`.

## Углубление AI (M — главный дифференциатор)

- [ ] **4. Чат в контексте дела («Спросить по делу»). [ПРИОРИТЕТ]** Чат в карточке дела, который автоматически видит все документы дела + хронологию КАД + чеклист.
  Реюз: `ChatService.prepareContext` уже умеет `attachedChunks` — надо скоупить контекст по `caseId` (~80% механики есть).
  Ценность: killer-feature vs общих ассистентов.

  ### План реализации

  Что уже готово (переиспользуем, не строим):
  - `DocumentRetrieval.retrieveForCase(query, topK, caseId)` — векторный поиск по документам конкретного дела уже реализован (`DocumentRetrievalImpl`, используется в `LegalAiPortImpl.answerForCase`).
  - `CaseAccessProvider.assertCaseVisible(caseId, lawyerId, orgIds)` — проверка доступа к делу.
  - `CaseAnalyticsService.prepareAnalysis(caseId, lawyerId, orgIds)` → `AnalysisInputs` уже собирает `caseContext` (карточка дела + стороны) и `timelineText` (хронология заседаний КАД). Чеклист — через `ChecklistTableParser`.
  - `ChatService.prepareContext` / `chat` / `chatStream` — весь SSE-стриминг, история, квоты, `FollowUpParser`.

  Шаги:

  1. **DTO.** В `ChatRequest` добавить `UUID caseId` (nullable — обычный чат остаётся). Валидация: если `caseId != null`, `attachedDocumentIds` игнорируем или разрешаем как доп. вложения — контекст и так скоуплен по делу.

  2. **Модель беседы.** В `Conversation` (Mongo) добавить поле `UUID caseId` + конструктор `Conversation(lawyerId, title, caseId)`. Добавить в `ConversationRepository` метод `findByLawyerIdAndCaseIdOrderByCreatedAtDesc(lawyerId, caseId, pageable)` — чтобы в карточке дела показывать только беседы этого дела.

  3. **Доступ.** В `ChatService.chat`/`chatStream`: если `request.caseId() != null` — до всего вызвать `caseAccessProvider.assertCaseVisible(caseId, lawyerId, orgIds)`. `orgIds` пробросить из `Authentication` в контроллере (как в остальных case-эндпоинтах). `resolveConversation` — прокинуть `caseId` в новую `Conversation`; при существующей беседе проверить, что её `caseId` совпадает с запросом.

  4. **Сборка контекста.** Отдельный путь в `prepareContext` при наличии `caseId`:
     - вместо `retrieveKnowledgeBase(...)` использовать `retrieveForCase(request.message(), topK, caseId)` — документы дела;
     - плюс параллельно оставить `retrieveKnowledgeBase(...)` для законодательства (в деле его нет), объединить чанки;
     - подмешать в начало системного промпта `caseContext` + `timelineText` (+ чеклист) из `CaseAnalyticsService.prepareAnalysis` — вынести доступ через порт `CaseContextProvider` в `core.api`, реализация в `practice` (как `CaseAccessProvider`), чтобы не тянуть practice-сервис в core напрямую;
     - `sources`: к меткам добавить «Материалы дела» / названия документов дела.

  5. **RagService.** Новый метод `buildCaseSystemPrompt(caseContext, timelineText, checklist, caseChunks, legislationChunks, legislationPresent)` — инструкция «отвечай строго по материалам этого дела, если данных нет — скажи прямо». Не переиспользовать общий `buildSystemPrompt` вслепую.

  6. **Контроллер.** `ChatController.chat`/`chatStream` уже принимают `ChatRequest` — `caseId` придёт в теле. Добавить проброс `orgIds` (через `SecurityUtils`). Отдельный GET `/conversations?caseId=...` (или переиспользовать `getConversations` с параметром) для списка бесед дела.

  7. **Фронт.** Вкладка/панель «Спросить по делу» в карточке дела: тот же чат-компонент, что и глобальный, но с зашитым `caseId` и фильтром бесед по делу. Заголовок первой беседы — из первого вопроса (`TITLE_MAX_LENGTH` уже есть).

  8. **Тесты.**
     - `ChatService`: доступ к чужому делу → `assertCaseVisible` кидает (по аналогии с `ChatAttachmentOwnershipTest`); беседа с `caseId` не подхватывает документы другого дела.
     - контекст: при `caseId` вызывается `retrieveForCase`, а не только `retrieveKnowledgeBase`.
     - несовпадение `caseId` беседы и запроса → ошибка.

  Порядок: DTO+модель+доступ (S) → сборка контекста+промпт (S) → фронт (S). Миграции БД не нужны (Mongo). Отдельный эндпоинт не обязателен — хватает поля в `ChatRequest`.

- [ ] **5. «Чат с документом» + авто-саммари при загрузке.** При загрузке (`DocumentService.completeProcessing`) генерировать саммари + ключевые пункты; в карточке дока — Q&A.
  Реюз: chunks/embeddings уже считаются, LLM-шлюз готов.
  Ценность: нет «задать вопрос к этому PDF» и «краткое содержание» — есть только сравнение/review.

- [ ] **6. Из чата → черновик документа одной кнопкой.** Ответ AI по делу → «Создать черновик» (жалоба/ходатайство) с предзаполнением из дела.
  Реюз: `DraftController` + `DraftType` (5 типов).
  Ценность: замыкает связку «анализ → документ».

## Дожать начатое (M)

- [ ] **7. Завершить клиентский портал (M3–M5).** Дела/документы/переписка для клиента + JWT-claim `clients` (запланирован, ещё не реализован).
  Статус: M1 готов, M2 фронт. Половина работы сделана.
  Ценность: прямой пункт сравнения с Clio/MyCase в тендерах.

- [ ] **8. Авто-синхронизация заседаний КАД → календарь + авто-задачи.** `ArbitrPollingService` пишет `case_hearing_events` и шлёт в Telegram, но не заводит события в календаре и задачи «подготовиться за 3 дня».
  Реюз: `CalendarController`/`case_tasks`, дедлайн-пороги 7/3/1.
  Ценность: убирает ручной перенос дат.

## Новые фичи с юридической спецификой (M–L)

- [ ] **9. Проверка конфликта интересов при добавлении клиента/дела.** Проверка нового клиента/оппонента по всем существующим сторонам («контрагент уже проходит по делу X как оппонент»).
  Реюз: `ClientContact`, pg_trgm-поиск, данные дел.
  Ценность: обязательная процедура в юрфирмах; почти никто из СНГ-конкурентов не делает.

- [ ] **10. Онлайн-оплата счетов клиентом + напоминания.** Платёжная ссылка в счёте, страница оплаты в портале, авто-напоминания о просрочке.
  Реюз: `InvoiceService`, `BillingController`, YooKassa-клиент (уже подключён для подписок), `NotificationDispatcher`.
  Ценность: прямо влияет на деньги юриста → сильный аргумент удержания.

---

## Тех-долг / инфраструктура

- [x] **Заглушить Eclipse null-analysis шум (459 warnings).** ✅ Сделано. Категория `unchecked conversion to @NonNull from non-annotated type` — артефакт неаннотированных JDK/Spring, не баг в коде. В каждый из 8 модулей добавлен `.settings/org.eclipse.jdt.core.prefs` с `nullUncheckedConversion=ignore`; в `.gitignore` whitelist на этот файл (`.settings/*` + `!.../org.eclipse.jdt.core.prefs`). Реальные проверки (`nullSpecViolation`, `potentialNullReference`) остались активны.
  ⏳ В Eclipse один раз: F5 → Project → Clean; при необходимости включить «Enable project specific settings» в Java Compiler у модуля.

- [ ] **Enforced null-safety на сборке: JSpecify + NullAway.** Перенести null-контроль из IDE в Maven/CI — «как в проде». Подключить `org.jspecify:jspecify`, Error Prone + NullAway в `maven-compiler-plugin`, разметить пакеты `@NullMarked`. Внедрять по одному модулю (декомпозиция), после `.settings`-фикса выше. NullAway по умолчанию доверяет неаннотированным библиотекам — того шума не будет, ловит реальные NPE на билде.

---

## Порядок запуска (рекомендация)

1. **#1, #2, #4** — максимум «вау» на минимум усилий, всё на готовой инфре.
2. **#7 + #8** — дожать заявленное, закрыть сравнение с Clio.
3. **#5 / #6 / #10** — углубление AI и монетизация.
4. **#9** — маркетинговый дифференциатор.

Старт: **#4 (чат по делу)** — наибольший отрыв от конкурентов при готовой на 80% механике в `ChatService`.
