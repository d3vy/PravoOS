DROP INDEX CONCURRENTLY IF EXISTS idx_subscriptions_current_period_end;
CREATE INDEX CONCURRENTLY idx_subscriptions_current_period_end
    ON subscriptions (current_period_end);

DROP INDEX CONCURRENTLY IF EXISTS idx_subscription_status;
DROP INDEX CONCURRENTLY IF EXISTS idx_subscriptions_status_period_end;
CREATE INDEX CONCURRENTLY idx_subscriptions_status_period_end
    ON subscriptions (status, current_period_end);

DROP INDEX CONCURRENTLY IF EXISTS idx_applications_status;
DROP INDEX CONCURRENTLY IF EXISTS idx_lawyer_applications_status_submitted;
CREATE INDEX CONCURRENTLY idx_lawyer_applications_status_submitted
    ON lawyer_applications (status, submitted_at DESC);

DROP INDEX CONCURRENTLY IF EXISTS idx_lawyer_applications_submitted_at;
CREATE INDEX CONCURRENTLY idx_lawyer_applications_submitted_at
    ON lawyer_applications (submitted_at DESC);

DROP INDEX CONCURRENTLY IF EXISTS idx_users_role_status;
DROP INDEX CONCURRENTLY IF EXISTS idx_users_role_status_created;
CREATE INDEX CONCURRENTLY idx_users_role_status_created
    ON users (role, status, created_at DESC);

DROP INDEX CONCURRENTLY IF EXISTS idx_payments_user_plan_status;
CREATE INDEX CONCURRENTLY idx_payments_user_plan_status
    ON payments (user_id, plan_id, status);

DROP INDEX CONCURRENTLY IF EXISTS idx_refresh_tokens_user_active;
CREATE INDEX CONCURRENTLY idx_refresh_tokens_user_active
    ON refresh_tokens (user_id, created_at DESC)
    WHERE revoked_at IS NULL;
