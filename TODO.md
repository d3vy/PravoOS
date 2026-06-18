# PravoOS — Roadmap к CRM для юристов

Приоритеты расставлены по соотношению ценность/сложность.
Статусы: `[ ]` — не начато · `[~]` — в процессе · `[x]` — готово

---

## Tier 1 — Критично (без этого не CRM)

### 1. Статус дела + Pipeline ✅
- [x] Flyway V7: `cases.status VARCHAR(20) NOT NULL DEFAULT 'INTAKE'` + индекс `(lawyer_id, status)`
- [x] Java enum `CaseStatus`: `INTAKE → IN_PROGRESS → SUBMITTED → CLOSED_WON / CLOSED_LOST`
- [x] `PATCH /api/ai/cases/{id}/status` (гибкие переходы — валидация только enum) + статус в экспорте
- [x] `GET /api/ai/cases?status=` — фильтрация по статусу
- [x] Фронт: фильтр-чипы на `/cases`, цветные бейджи, смена статуса из карточки и из карточки дела
- [x] Фронт: Kanban-доска (переключатель Список/Доска, drag-drop смены статуса)

### 2. Дедлайны и напоминания
- [ ] Flyway-миграция: добавить `filing_deadline DATE`, `next_hearing_date DATE`, `expires_at DATE` в `cases`
- [ ] `CaseRequest/CaseResponse`: добавить поля дат
- [ ] Spring Scheduler: `@Scheduled` джоб — проверка дедлайнов за 7/3/1 день
- [ ] Расширить Telegram-бот на уведомления юристу (сейчас только admin)
- [ ] Поле `telegram_chat_id` в профиле юриста, команда `/start` у бота для привязки
- [ ] Email-напоминание как fallback если нет Telegram
- [ ] Фронт: datepicker для дедлайнов на странице дела, визуальная индикация срочных дел

### 3. Сущность «Клиент» ✅
- [x] Flyway-миграция V6: таблица `clients` + `ALTER TABLE cases ADD COLUMN client_id UUID REFERENCES clients ON DELETE SET NULL`
- [x] CRUD `/api/ai/clients/**` — создание, редактирование (PUT), удаление, список, карточка с делами
- [x] Gateway маршрут — покрыт общим `/api/ai/**` (отдельный не нужен)
- [x] Страница клиента: данные + все его дела
- [x] Удаление клиента: выбор юристом — отвязать дела (SET NULL) или каскад (удалить дела+доки)
- [x] Каскадная очистка клиентов при удалении юриста (`LawyerDataCleanupService`)
- [x] PATCH `/api/ai/cases/{id}` (редактирование дела + привязка клиента)
- [x] Фронт: `/clients`, `/clients/:id`, привязка клиента при создании/редактировании дела

### 4. Учёт времени и биллинг
- [ ] Flyway-миграция: таблица `time_entries` (id, case_id, lawyer_id, date, hours, rate, description)
- [ ] CRUD `/api/ai/cases/{id}/time-entries`
- [ ] `TimeEntryService`: суммирование по делу, итого к оплате
- [ ] Генерация счёта в .docx через Apache POI (уже есть `DocxExportService` — расширить)
- [ ] `GET /api/ai/cases/{id}/invoice/download` — скачать счёт
- [ ] Фронт: таймер «Старт/Стоп» на странице дела, список записей, кнопка «Сгенерировать счёт»

---

## Tier 2 — Важно (продукт становится удобным)

### 5. Задачи внутри дела ✅
- [x] Flyway-миграция V8: таблица `case_tasks` (id, case_id, text, due_date, done, created_at)
- [x] CRUD `/api/ai/cases/{id}/tasks` (+ PATCH toggle done, DELETE)
- [x] AI-генерация: `POST /api/ai/cases/{id}/tasks/generate` — прогон `DOCUMENT_CHECKLIST`, парс markdown-таблицы → задачи для missing/partial документов
- [x] Фронт: чеклист задач на странице дела, quick-add задачи, dueDate + done

### 6. Интеграция с КАД.Арбитр
- [ ] Поле `arbitr_case_number VARCHAR(50)` в таблице `cases`
- [ ] Spring Scheduler: polling kad.arbitr.ru по номеру дела (Playwright/HTTP) — обновление `next_hearing_date`
- [ ] Переработать `scripts/kad_arbitr.yaml` из разового краулера в фоновый polling-сервис
- [ ] Kafka topic `case.hearing.updated` — уведомить юриста при изменении даты заседания
- [ ] Фронт: отображение истории событий по делу из КАД, ссылка на карточку дела на kad.arbitr.ru

### 7. Поиск по делам
- [ ] Backend: `GET /api/ai/cases?q=` — `ILIKE` по title/description + JOIN по client name
- [ ] Фронт: поле поиска на `/cases` (аналогично поиску бесед в чате)
- [ ] Глобальный поиск: дела + беседы + документы — единый `/api/ai/search?q=`

### 8. Уведомления для юриста (Telegram)
- [ ] Flyway-миграция: `telegram_chat_id BIGINT` в таблице `users`
- [ ] `GET/PATCH /api/user/profile` — добавить `telegramChatId` в `ProfileResponse/UpdateProfileRequest`
- [ ] Telegram-команда `/start` у `PravoOsAdminBot` — привязка chat_id к аккаунту по коду
- [ ] `notification-service`: слушать Kafka topics с дедлайнами и слать юристу

---

## Tier 3 — Качество жизни

### 9. Dashboard юриста
- [ ] Backend: `GET /api/ai/dashboard` — активных дел по статусам, дедлайны на 7 дней, незакрытые задачи, сумма невыставленных часов
- [ ] Фронт: страница `/dashboard` как главная после логина (сейчас — `/chat`)
- [ ] Виджеты: pipeline воронка, ближайшие дедлайны, последние дела

### 10. Шаблоны документов
- [ ] Flyway-миграция: таблица `document_templates` (id, lawyer_id, name, content_with_placeholders, created_at)
- [ ] CRUD `/api/ai/templates`
- [ ] `TemplateService`: заполнение плейсхолдеров `{{client_name}}`, `{{case_number}}` данными из дела
- [ ] Фронт: библиотека шаблонов, применение шаблона к делу → черновик

### 11. Экспорт дела в PDF/DOCX ✅
- [x] `CaseExportService` + `CaseDocxWriter` (POI) и `CasePdfWriter` (PDFBox + DejaVu для кириллицы): клиент + документы + заключения AI + черновики
- [x] `GET /api/ai/cases/{id}/export?format=docx|pdf` — скачать выжимку по делу
- [x] Фронт: кнопки «Экспорт .docx» / «Экспорт .pdf» на странице дела
- (статус и задачи в выжимку не вошли — сущностей ещё нет, задачи №1/№5)

### 12. История коммуникаций с клиентом
- [ ] Flyway-миграция: таблица `client_contacts` (id, client_id, type ENUM звонок/встреча/письмо, date, notes)
- [ ] CRUD `/api/ai/clients/{id}/contacts`
- [ ] Фронт: лог на странице клиента — хронология контактов с заметками

---

## Технический долг (не новые фичи, но нужно)

- [ ] `Case` — добавить метрики для админа: Approval Rate, Avg. Time to Submission, Case Progression (зафиксировано в memory — нет данных)
- [ ] `TestLawyerSeeder` — добавить создание тестового клиента и дела для удобства проверки
- [ ] Мобильная адаптация: проверить `/cases` и `CaseDetailPage` на узких экранах
- [ ] PWA: добавить `manifest.json` + service worker для оффлайн-доступа к данным дел
