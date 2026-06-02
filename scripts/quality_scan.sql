\pset pager off
\timing off

\echo '==================== 1. ОБЗОР: статусы ===================='
SELECT status, count(*) AS docs
FROM documents
GROUP BY status
ORDER BY docs DESC;

\echo '==================== 2. ОБЗОР: тип файла (только глобальная база, case_id IS NULL) ===================='
SELECT file_type, count(*) AS docs
FROM documents
WHERE case_id IS NULL
GROUP BY file_type
ORDER BY docs DESC;

\echo '==================== 3. АНОМАЛИЯ: READY-документы без единого чанка ===================='
SELECT d.id, d.title, d.file_type
FROM documents d
LEFT JOIN document_chunks c ON c.document_id = d.id
WHERE d.status = 'READY'
GROUP BY d.id, d.title, d.file_type
HAVING count(c.id) = 0;

\echo '==================== 4. FAILED-документы ===================='
SELECT id, title, file_type, uploaded_at
FROM documents
WHERE status = 'FAILED'
ORDER BY uploaded_at DESC;

\echo '==================== 5. ПОДОЗРИТЕЛЬНО КОРОТКИЕ: суммарный текст < 400 символов ===================='
WITH doc_text AS (
    SELECT d.id, d.title, d.file_type,
           count(c.id)                              AS chunks,
           coalesce(sum(char_length(c.content)), 0) AS total_chars
    FROM documents d
    LEFT JOIN document_chunks c ON c.document_id = d.id
    WHERE d.case_id IS NULL AND d.status = 'READY'
    GROUP BY d.id, d.title, d.file_type
)
SELECT id, file_type, chunks, total_chars, left(title, 70) AS title
FROM doc_text
WHERE total_chars < 400
ORDER BY total_chars ASC;

\echo '==================== 6. МУСОРНЫЕ МАРКЕРЫ: captcha / paywall / nav / 404 в тексте ===================='
WITH doc_text AS (
    SELECT d.id, d.title, d.file_type,
           string_agg(c.content, ' ' ORDER BY c.chunk_index) AS full_text
    FROM documents d
    JOIN document_chunks c ON c.document_id = d.id
    WHERE d.case_id IS NULL
    GROUP BY d.id, d.title, d.file_type
)
SELECT id, file_type, left(title, 60) AS title,
       CASE
           WHEN full_text ILIKE '%captcha%'                THEN 'captcha'
           WHEN full_text ILIKE '%доступ заблокир%'        THEN 'доступ заблокирован'
           WHEN full_text ILIKE '%пробный доступ%'         THEN 'paywall consultant'
           WHEN full_text ILIKE '%включите javascript%'
             OR full_text ILIKE '%enable javascript%'      THEN 'js-заглушка'
           WHEN full_text ILIKE '%страница не найдена%'
             OR full_text ILIKE '%404 not found%'          THEN '404'
           WHEN full_text ILIKE '%версия для печати%'      THEN 'nav/print'
       END AS flag
FROM doc_text
WHERE full_text ILIKE '%captcha%'
   OR full_text ILIKE '%доступ заблокир%'
   OR full_text ILIKE '%пробный доступ%'
   OR full_text ILIKE '%включите javascript%'
   OR full_text ILIKE '%enable javascript%'
   OR full_text ILIKE '%страница не найдена%'
   OR full_text ILIKE '%404 not found%'
   OR full_text ILIKE '%версия для печати%'
ORDER BY flag;

\echo '==================== 7. БИТАЯ КОДИРОВКА: символ замены U+FFFD в тексте ===================='
SELECT d.id, d.file_type, left(d.title, 60) AS title,
       count(*) AS chunks_with_bad_chars
FROM documents d
JOIN document_chunks c ON c.document_id = d.id
WHERE d.case_id IS NULL
  AND position(chr(65533) IN c.content) > 0
GROUP BY d.id, d.file_type, d.title
ORDER BY chunks_with_bad_chars DESC;

\echo '==================== 8. ДУБЛИ: одинаковый текст у разных документов ===================='
WITH doc_hash AS (
    SELECT d.id, d.title, d.file_type,
           md5(string_agg(c.content, '' ORDER BY c.chunk_index)) AS body_hash
    FROM documents d
    JOIN document_chunks c ON c.document_id = d.id
    WHERE d.case_id IS NULL
    GROUP BY d.id, d.title, d.file_type
)
SELECT body_hash, count(*) AS copies,
       string_agg(left(title, 40), ' | ') AS titles
FROM doc_hash
GROUP BY body_hash
HAVING count(*) > 1
ORDER BY copies DESC;

\echo '==================== 9. ИТОГ: распределение размеров (sanity-check) ===================='
WITH doc_text AS (
    SELECT d.id,
           coalesce(sum(char_length(c.content)), 0) AS total_chars
    FROM documents d
    LEFT JOIN document_chunks c ON c.document_id = d.id
    WHERE d.case_id IS NULL AND d.status = 'READY'
    GROUP BY d.id
)
SELECT count(*)                       AS docs,
       min(total_chars)               AS min_chars,
       round(avg(total_chars))        AS avg_chars,
       percentile_cont(0.5) WITHIN GROUP (ORDER BY total_chars) AS median_chars,
       max(total_chars)               AS max_chars
FROM doc_text;
