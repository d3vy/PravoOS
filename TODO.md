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

### 2. Дедлайны и напоминания ✅
- [x] Flyway V8 (ai-service): `filing_deadline DATE`, `next_hearing_date DATE`, `expires_at DATE` в `cases`
- [x] `CreateCaseRequest/UpdateCaseRequest/CaseResponse`: поля дат
- [x] Spring Scheduler: `DeadlineReminderService` (`@Scheduled`, пороги 7/3/1, Flyway V9 `case_deadline_reminders` для идемпотентности) → Kafka `case.deadline.approaching`
- [x] Telegram-бот шлёт напоминания юристу (notification-service consumer → resolve chatId → Telegram)
- [x] `telegram_chat_id` на `lawyer_profiles` (user-service V9) + команда `/start <код>` у бота для привязки
- [x] Email-напоминание как fallback если нет Telegram (notification → internal `POST /internal/notifications/deadline-email` → Resend в user-service)
- [x] Фронт: datepicker для дедлайнов (создание+редактирование дела), бейджи срочности (≤3 дн / просрочено)

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

### 7. Поиск по делам ✅
- [x] Backend: `GET /api/ai/cases?q=` — `ILIKE` по title/description + EXISTS по client name (комбинируется с `?status=`)
- [x] Фронт: поле поиска на `/cases` (debounce 300мс, работает в обоих видах)
- [x] Глобальный поиск: дела + беседы + документы — единый `GET /api/ai/search?q=` (страница `/search`)

### 8. Уведомления для юриста (Telegram) ✅
- [x] Flyway V9 (user-service): `telegram_chat_id BIGINT` на `lawyer_profiles` (не на `users`) + таблица `telegram_link_codes`
- [x] `GET /api/user/profile` отдаёт `telegramLinked`; `POST /profile/telegram/link-code` (код+deep-link), `DELETE /profile/telegram` (отвязка)
- [x] Telegram-команда `/start <код>` у `PravoOsAdminBot` → internal `POST /internal/telegram/bind` → привязка chat_id
- [x] `notification-service`: consumer `case.deadline.approaching` → `GET /internal/telegram/chat-id/{lawyerId}` → Telegram юристу

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
- [x] В выжимку входят статус дела (задача №1) и задачи по делу (задача №5)

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
