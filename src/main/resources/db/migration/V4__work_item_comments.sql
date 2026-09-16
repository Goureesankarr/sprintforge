CREATE TABLE work_item_comments (
    id UUID PRIMARY KEY,
    work_item_id UUID NOT NULL REFERENCES work_items(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES app_users(id),
    body VARCHAR(4000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_work_item_comments_active_timeline
    ON work_item_comments(work_item_id, created_at ASC)
    WHERE deleted_at IS NULL;
