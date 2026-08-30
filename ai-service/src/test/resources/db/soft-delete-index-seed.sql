INSERT INTO clients (id, lawyer_id, name, type, deleted_at, created_at)
SELECT ('00000000-0000-0001-0000-' || lpad(g::text, 12, '0'))::uuid,
       ('00000000-0000-0000-0000-' || lpad((g % 200)::text, 12, '0'))::uuid,
       'Клиент ' || g, 'INDIVIDUAL',
       CASE WHEN g % 10 = 0 THEN now() END,
       now() - (g || ' minutes')::interval
FROM generate_series(1, 20000) g;

INSERT INTO cases (id, lawyer_id, client_id, org_id, title, status, deleted_at, created_at)
SELECT ('00000000-0000-0003-0000-' || lpad(g::text, 12, '0'))::uuid,
       ('00000000-0000-0000-0000-' || lpad((g % 200)::text, 12, '0'))::uuid,
       ('00000000-0000-0001-0000-' || lpad((1 + g % 200)::text, 12, '0'))::uuid,
       ('00000000-0000-0002-0000-' || lpad((g % 50)::text, 12, '0'))::uuid,
       'Дело ' || g,
       (ARRAY['INTAKE','IN_PROGRESS','CLOSED'])[1 + g % 3],
       CASE WHEN g % 10 = 0 THEN now() END,
       now() - (g || ' minutes')::interval
FROM generate_series(1, 20000) g;

INSERT INTO documents (title, file_name, file_type, file_path, uploaded_by, case_id, size_bytes,
                       visible_to_client, document_kind, superseded, uploaded_at, status,
                       summary_status, deleted_at)
SELECT 'Документ ' || g, 'f.pdf', 'pdf', '/tmp/f.pdf',
       ('00000000-0000-0000-0000-' || lpad((g % 200)::text, 12, '0'))::uuid,
       ('00000000-0000-0003-0000-' || lpad((1 + g % 500)::text, 12, '0'))::uuid,
       10, FALSE, (ARRAY['GENERAL','LEGISLATION'])[1 + g % 2], FALSE,
       now() - (g || ' minutes')::interval, 'READY', 'NONE',
       CASE WHEN g % 10 = 0 THEN now() END
FROM generate_series(1, 20000) g;

INSERT INTO invoices (lawyer_id, client_id, number, issue_date, status, deleted_at, created_at)
SELECT ('00000000-0000-0000-0000-' || lpad((g % 200)::text, 12, '0'))::uuid,
       ('00000000-0000-0001-0000-' || lpad((1 + g % 200)::text, 12, '0'))::uuid,
       'INV-' || g, current_date, 'DRAFT',
       CASE WHEN g % 10 = 0 THEN now() END,
       now() - (g || ' minutes')::interval
FROM generate_series(1, 20000) g;

INSERT INTO document_templates (id, lawyer_id, name, content, deleted_at, created_at)
SELECT gen_random_uuid(),
       ('00000000-0000-0000-0000-' || lpad((g % 200)::text, 12, '0'))::uuid,
       'Шаблон ' || g, 'content',
       CASE WHEN g % 10 = 0 THEN now() END,
       now() - (g || ' minutes')::interval
FROM generate_series(1, 20000) g;

INSERT INTO saved_views (lawyer_id, org_id, scope, name, config, shared_with_team, deleted_at,
                         created_at, updated_at)
SELECT ('00000000-0000-0000-0000-' || lpad((g % 200)::text, 12, '0'))::uuid,
       ('00000000-0000-0002-0000-' || lpad((g % 50)::text, 12, '0'))::uuid,
       (ARRAY['CASES','CLIENTS','INVOICES'])[1 + g % 3],
       'Вид ' || g, '{}', g % 4 = 0,
       CASE WHEN g % 10 = 0 THEN now() END,
       now() - (g || ' minutes')::interval, now()
FROM generate_series(1, 20000) g;

INSERT INTO workflow_definitions (org_id, created_by, name, category, is_system, steps, deleted_at,
                                  created_at, updated_at)
SELECT ('00000000-0000-0002-0000-' || lpad((g % 50)::text, 12, '0'))::uuid,
       ('00000000-0000-0000-0000-' || lpad((g % 200)::text, 12, '0'))::uuid,
       'Процесс ' || g, (ARRAY['CUSTOM','BANKRUPTCY'])[1 + g % 2], g % 40 = 0, '[]'::jsonb,
       CASE WHEN g % 10 = 0 THEN now() END,
       now() - (g || ' minutes')::interval, now()
FROM generate_series(1, 20000) g;

ANALYZE cases, clients, documents, invoices, document_templates, saved_views,
        workflow_definitions;
