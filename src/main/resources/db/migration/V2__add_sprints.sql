CREATE TABLE sprints (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    goal VARCHAR(1000),
    status VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_sprint_dates CHECK (end_date >= start_date),
    CONSTRAINT uq_sprint_project_name UNIQUE (project_id, name)
);

CREATE INDEX idx_sprints_project_status ON sprints(project_id, status);

ALTER TABLE work_items
    ADD COLUMN sprint_id UUID REFERENCES sprints(id) ON DELETE SET NULL;

CREATE INDEX idx_work_items_sprint_status ON work_items(sprint_id, status);
