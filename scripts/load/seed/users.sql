-- Нагрузочный сид для pravoos_users.
-- Идентификаторы детерминированы: md5('pravoos-load-...')::uuid, поэтому practice.sql
-- в другой базе получает те же lawyer_id и org_id без кросс-базовых запросов.
-- Переменные: :lawyers, :orgs, :password_hash

\set ON_ERROR_STOP on

BEGIN;

DELETE FROM organizations
WHERE owner_id IN (SELECT id FROM users WHERE email LIKE 'load-lawyer-%@load.pravoos.test');

DELETE FROM users WHERE email LIKE 'load-lawyer-%@load.pravoos.test';

INSERT INTO plans (id, code, name, price_kopecks, daily_requests, daily_tokens, seats, is_default, created_at)
VALUES (md5('pravoos-load-plan')::uuid, 'LOAD', 'Load profile', 0, 0, 0, 1000, FALSE, now())
ON CONFLICT (code) DO UPDATE SET daily_requests = 0, daily_tokens = 0, seats = 1000;

INSERT INTO users (id, email, password_hash, role, status, created_at)
SELECT md5('pravoos-load-lawyer-' || i)::uuid,
       'load-lawyer-' || i || '@load.pravoos.test',
       :'password_hash',
       'LAWYER',
       'ACTIVE',
       now() - (i || ' minutes')::interval
FROM generate_series(1, :lawyers) AS i;

INSERT INTO lawyer_profiles (user_id, full_name, specialization, phone)
SELECT md5('pravoos-load-lawyer-' || i)::uuid,
       'Нагрузочный Юрист ' || i,
       'Арбитраж',
       '+7900' || lpad(i::text, 7, '0')
FROM generate_series(1, :lawyers) AS i;

INSERT INTO organizations (id, name, owner_id, created_at)
SELECT md5('pravoos-load-org-' || o)::uuid,
       'Нагрузочная коллегия ' || o,
       md5('pravoos-load-lawyer-' || o)::uuid,
       now()
FROM generate_series(1, :orgs) AS o;

INSERT INTO organization_memberships (id, org_id, user_id, org_role, created_at)
SELECT md5('pravoos-load-membership-' || i)::uuid,
       md5('pravoos-load-org-' || ((i - 1) % :orgs + 1))::uuid,
       md5('pravoos-load-lawyer-' || i)::uuid,
       CASE WHEN i <= :orgs THEN 'OWNER' ELSE 'MEMBER' END,
       now()
FROM generate_series(1, :lawyers) AS i;

INSERT INTO subscriptions (id, user_id, plan_id, status, trial_end, current_period_end, version, created_at, updated_at)
SELECT md5('pravoos-load-subscription-' || i)::uuid,
       md5('pravoos-load-lawyer-' || i)::uuid,
       md5('pravoos-load-plan')::uuid,
       'ACTIVE',
       NULL,
       now() + interval '365 days',
       0,
       now(),
       now()
FROM generate_series(1, :lawyers) AS i;

COMMIT;

SELECT count(*) AS load_users FROM users WHERE email LIKE 'load-lawyer-%@load.pravoos.test';
