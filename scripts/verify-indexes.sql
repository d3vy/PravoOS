-- PravoOS — верификация индексов на горячих запросах (AUDIT_PLAN №25).
-- Запускать на pravoos_ai с реальным объёмом данных:
--   psql "$DB_AI_URL" -v lawyer_id="'<uuid>'" -v case_id="'<uuid>'" -f scripts/verify-indexes.sql
-- Ищем Index Scan / Bitmap Index Scan вместо Seq Scan на крупных таблицах.

\timing on

-- 1. Дела юриста (idx_cases_lawyer_id, idx_cases_lawyer_status)
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM cases WHERE lawyer_id = :lawyer_id ORDER BY created_at DESC LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM cases WHERE lawyer_id = :lawyer_id AND status = 'IN_PROGRESS' ORDER BY created_at DESC LIMIT 20;

-- 2. Клиенты и контакты (idx_clients_lawyer_id, idx_client_contacts_client)
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM clients WHERE lawyer_id = :lawyer_id ORDER BY created_at DESC LIMIT 20;

EXPLAIN (ANALYZE, BUFFERS)
SELECT cc.* FROM client_contacts cc
JOIN clients c ON c.id = cc.client_id
WHERE c.lawyer_id = :lawyer_id LIMIT 20;

-- 3. Документы и чанки дела (idx_documents_case_id, idx_document_chunks_document_id)
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM documents WHERE case_id = :case_id ORDER BY uploaded_at DESC LIMIT 20;

-- 4. Полнотекстовый поиск (trigram: idx_document_chunks_content_trgm, idx_documents_title_trgm)
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, title FROM documents WHERE title ILIKE '%банкрот%' LIMIT 20;

-- 5. Векторный поиск по базе знаний (HNSW: idx_document_chunks_embedding, vector_cosine_ops).
--    Подставьте реальный эмбеддинг (1536) вместо нулевого вектора, иначе план нерепрезентативен.
-- EXPLAIN (ANALYZE, BUFFERS)
-- SELECT id, 1 - (embedding <=> :query_embedding) AS score
-- FROM document_chunks
-- ORDER BY embedding <=> :query_embedding
-- LIMIT 5;

-- 6. Шаблоны юриста (idx_document_templates_lawyer)
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM document_templates WHERE lawyer_id = :lawyer_id ORDER BY created_at DESC LIMIT 20;

-- 7. Аудит фактического использования индексов (нулевой idx_scan = индекс не работает либо не нужен)
SELECT schemaname, relname, indexrelname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY idx_scan ASC, relname;

-- 8. Seq-scan-хотспоты: таблицы, где последовательное чтение преобладает над индексным
SELECT relname, seq_scan, idx_scan, n_live_tup
FROM pg_stat_user_tables
WHERE seq_scan > 0
ORDER BY seq_scan DESC;
