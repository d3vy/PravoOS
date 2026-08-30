ALTER TABLE saved_views ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_saved_views_deleted_at ON saved_views (deleted_at)
    WHERE deleted_at IS NOT NULL;
