-- Нагрузочный сид для pravoos_ai.
-- lawyer_id / org_id совпадают с users.sql: md5('pravoos-load-lawyer-' || i)::uuid.
-- Переменные: :lawyers, :orgs, :clients_per_lawyer, :cases_per_lawyer, :tasks_per_case,
--             :docs_per_case, :chunks_per_doc, :invoices_per_lawyer, :time_entries_per_case

\set ON_ERROR_STOP on

BEGIN;

CREATE TEMP TABLE load_lawyer ON COMMIT DROP AS
SELECT i AS n,
       md5('pravoos-load-lawyer-' || i)::uuid AS lawyer_id,
       md5('pravoos-load-org-' || ((i - 1) % :orgs + 1))::uuid AS org_id
FROM generate_series(1, :lawyers) AS i;

DELETE FROM documents WHERE uploaded_by IN (SELECT lawyer_id FROM load_lawyer);
DELETE FROM time_entries WHERE lawyer_id IN (SELECT lawyer_id FROM load_lawyer);
DELETE FROM invoices WHERE lawyer_id IN (SELECT lawyer_id FROM load_lawyer);
DELETE FROM cases WHERE lawyer_id IN (SELECT lawyer_id FROM load_lawyer);
DELETE FROM clients WHERE lawyer_id IN (SELECT lawyer_id FROM load_lawyer);

INSERT INTO clients (id, lawyer_id, name, type, phone, email, inn, notes, created_at, deleted_at)
SELECT md5('pravoos-load-client-' || l.n || '-' || c)::uuid,
       l.lawyer_id,
       (ARRAY['ООО «Ремстрой»', 'ООО «Севертранс»', 'ИП Кузнецов А.В.', 'АО «Промлизинг»',
              'ООО «Гарант-Актив»', 'Петров Сергей Иванович', 'ООО «Медиастар»',
              'ЗАО «Энергосбыт»'])[1 + (c % 8)] || ' №' || c,
       CASE WHEN c % 3 = 0 THEN 'INDIVIDUAL' ELSE 'COMPANY' END,
       '+7910' || lpad(((l.n * 1000 + c) % 10000000)::text, 7, '0'),
       'client-' || l.n || '-' || c || '@load.pravoos.test',
       lpad(((l.n * 7919 + c * 104729) % 1000000000)::text, 10, '0'),
       'Нагрузочный клиент для профиля E',
       now() - (c || ' hours')::interval,
       CASE WHEN c % 20 = 0 THEN now() - interval '3 days' ELSE NULL END
FROM load_lawyer l, generate_series(1, :clients_per_lawyer) AS c;

INSERT INTO cases (id, lawyer_id, org_id, client_id, title, description, status, court_system,
                   court_case_number, judge_name, filing_deadline, next_hearing_date, expires_at,
                   default_hourly_rate, created_at, deleted_at)
SELECT md5('pravoos-load-case-' || l.n || '-' || k)::uuid,
       l.lawyer_id,
       l.org_id,
       md5('pravoos-load-client-' || l.n || '-' || (1 + (k % :clients_per_lawyer)))::uuid,
       (ARRAY['Банкротство', 'Взыскание неустойки', 'Договор подряда', 'Аренда помещения',
              'Корпоративный спор', 'Оспаривание сделки', 'Возмещение убытков',
              'Защита деловой репутации'])[1 + (k % 8)] || ' — дело ' || l.n || '/' || k,
       'Нагрузочное дело. Спор по обязательству, статья 309 ГК РФ, претензионный порядок соблюдён.',
       (ARRAY['INTAKE', 'IN_PROGRESS', 'SUBMITTED', 'CLOSED_WON', 'CLOSED_LOST'])[1 + (k % 5)],
       'ARBITR',
       'А40-' || (100000 + l.n * 1000 + k) || '/2026',
       'Судья Морозова Т.А.',
       CASE WHEN k % 4 = 0 THEN current_date + ((k % 14) - 3) ELSE NULL END,
       CASE WHEN k % 3 = 0 THEN current_date + ((k % 21) - 5) ELSE NULL END,
       CASE WHEN k % 9 = 0 THEN current_date + (k % 30) ELSE NULL END,
       3000 + (k % 5) * 500,
       now() - (k || ' hours')::interval,
       CASE WHEN k % 25 = 0 THEN now() - interval '2 days' ELSE NULL END
FROM load_lawyer l, generate_series(1, :cases_per_lawyer) AS k;

INSERT INTO case_tasks (id, case_id, text, due_date, done, created_at)
SELECT md5('pravoos-load-task-' || c.id || '-' || t)::uuid,
       c.id,
       (ARRAY['Подготовить отзыв на иск', 'Направить претензию', 'Собрать первичные документы',
              'Запросить выписку ЕГРЮЛ', 'Подготовить ходатайство', 'Согласовать позицию с клиентом'])[1 + (t % 6)],
       current_date + ((t * 3 + length(c.title)) % 21) - 7,
       (t % 3 = 0),
       now() - (t || ' hours')::interval
FROM cases c, generate_series(1, :tasks_per_case) AS t
WHERE c.lawyer_id IN (SELECT lawyer_id FROM load_lawyer);

INSERT INTO documents (id, title, file_name, file_type, uploaded_by, uploaded_at, status, case_id,
                       file_path, size_bytes, visible_to_client, document_kind, deleted_at)
SELECT md5('pravoos-load-doc-' || c.id || '-' || d)::uuid,
       (ARRAY['Договор подряда', 'Претензия', 'Исковое заявление', 'Отзыв на иск',
              'Акт сверки', 'Доверенность'])[1 + (d % 6)] || ' по делу ' || left(c.title, 40),
       'load-' || d || '.pdf',
       'application/pdf',
       c.lawyer_id,
       now() - (d || ' hours')::interval,
       'READY',
       c.id,
       '/app/documents/load/' || d || '.pdf',
       120000 + d * 1024,
       (d % 4 = 0),
       'GENERAL',
       CASE WHEN d % 30 = 0 THEN now() - interval '1 day' ELSE NULL END
FROM cases c, generate_series(1, :docs_per_case) AS d
WHERE c.lawyer_id IN (SELECT lawyer_id FROM load_lawyer);

INSERT INTO document_chunks (id, document_id, content, chunk_index, embedding, created_at)
SELECT md5('pravoos-load-chunk-' || doc.id || '-' || ch)::uuid,
       doc.id,
       'Пункт ' || ch || '. ' ||
       (ARRAY['Стороны согласовали неустойку в размере 0,1% за каждый день просрочки.',
              'Обязательства должны исполняться надлежащим образом согласно статье 309 ГК РФ.',
              'Односторонний отказ от исполнения обязательства не допускается.',
              'Претензионный порядок урегулирования спора является обязательным.',
              'Подрядчик обязан устранить недостатки работ в разумный срок.',
              'Арендатор вправе требовать соразмерного уменьшения арендной платы.'])[1 + (ch % 6)] ||
       ' Настоящий пункт относится к документу ' || left(doc.title, 60) || '.',
       ch,
       NULL,
       now()
FROM documents doc, generate_series(1, :chunks_per_doc) AS ch
WHERE doc.uploaded_by IN (SELECT lawyer_id FROM load_lawyer);

INSERT INTO invoices (id, lawyer_id, client_id, number, status, issue_date, due_date, currency,
                      subtotal, total, vat_rate, vat_amount, notes, version, created_at, deleted_at)
SELECT md5('pravoos-load-invoice-' || l.n || '-' || v)::uuid,
       l.lawyer_id,
       md5('pravoos-load-client-' || l.n || '-' || (1 + (v % :clients_per_lawyer)))::uuid,
       'LOAD-' || l.n || '-' || lpad(v::text, 5, '0'),
       (ARRAY['DRAFT', 'ISSUED', 'ISSUED', 'PAID', 'CANCELED'])[1 + (v % 5)],
       current_date - (v % 90),
       current_date - (v % 90) + 14,
       'RUB',
       50000 + v * 137,
       60000 + v * 164,
       20.00,
       10000 + v * 27,
       'Нагрузочный счёт',
       0,
       now() - (v || ' hours')::interval,
       CASE WHEN v % 40 = 0 THEN now() - interval '5 days' ELSE NULL END
FROM load_lawyer l, generate_series(1, :invoices_per_lawyer) AS v;

INSERT INTO time_entries (id, case_id, client_id, lawyer_id, description, activity_date, minutes,
                          hourly_rate, billable, running, started_at, invoice_id, created_at)
SELECT md5('pravoos-load-time-' || c.id || '-' || e)::uuid,
       c.id,
       c.client_id,
       c.lawyer_id,
       'Работа по делу: анализ документов и подготовка позиции',
       current_date - (e % 60),
       30 + (e % 8) * 15,
       coalesce(c.default_hourly_rate, 3000),
       TRUE,
       FALSE,
       NULL,
       NULL,
       now() - (e || ' hours')::interval
FROM cases c, generate_series(1, :time_entries_per_case) AS e
WHERE c.lawyer_id IN (SELECT lawyer_id FROM load_lawyer);

COMMIT;

ANALYZE clients;
ANALYZE cases;
ANALYZE case_tasks;
ANALYZE documents;
ANALYZE document_chunks;
ANALYZE invoices;
ANALYZE time_entries;

SELECT 'clients' AS table_name, count(*) FROM clients
UNION ALL SELECT 'cases', count(*) FROM cases
UNION ALL SELECT 'case_tasks', count(*) FROM case_tasks
UNION ALL SELECT 'documents', count(*) FROM documents
UNION ALL SELECT 'document_chunks', count(*) FROM document_chunks
UNION ALL SELECT 'invoices', count(*) FROM invoices
UNION ALL SELECT 'time_entries', count(*) FROM time_entries;
