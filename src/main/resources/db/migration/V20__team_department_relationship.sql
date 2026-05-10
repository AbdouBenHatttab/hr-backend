ALTER TABLE teams
    ADD COLUMN department_id BIGINT;

ALTER TABLE teams
    ADD CONSTRAINT fk_teams_department
        FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL;

CREATE INDEX idx_teams_department_id ON teams(department_id);
