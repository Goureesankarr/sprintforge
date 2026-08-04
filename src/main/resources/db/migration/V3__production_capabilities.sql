CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by_hash VARCHAR(64)
);

CREATE INDEX idx_refresh_tokens_user_active
    ON refresh_tokens(user_id, expires_at)
    WHERE revoked_at IS NULL;

ALTER TABLE projects ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE work_items ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX idx_projects_active_owner
    ON projects(owner_id, updated_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_work_items_active_project_status
    ON work_items(project_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE attachments (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    work_item_id UUID REFERENCES work_items(id) ON DELETE CASCADE,
    uploaded_by UUID NOT NULL REFERENCES app_users(id),
    object_key VARCHAR(512) NOT NULL UNIQUE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_attachment_size CHECK (size_bytes > 0 AND size_bytes <= 26214400)
);

CREATE INDEX idx_attachments_project_created
    ON attachments(project_id, created_at DESC);
