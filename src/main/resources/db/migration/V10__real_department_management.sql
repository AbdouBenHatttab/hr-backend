CREATE TABLE departments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

WITH normalized_departments AS (
    SELECT BTRIM(department) AS trimmed_name
    FROM persons
    WHERE department IS NOT NULL
      AND BTRIM(department) <> ''
),
deduplicated_departments AS (
    SELECT MIN(trimmed_name) AS name
    FROM normalized_departments
    GROUP BY LOWER(trimmed_name)
)
INSERT INTO departments (name, description, active, created_at, updated_at)
SELECT name,
       'Migrated from legacy person.department values',
       TRUE,
       NOW(),
       NOW()
FROM deduplicated_departments;

CREATE UNIQUE INDEX uk_departments_name_lower ON departments (LOWER(name));

ALTER TABLE persons ADD COLUMN department_id BIGINT;

UPDATE persons p
SET department_id = d.id
FROM departments d
WHERE p.department IS NOT NULL
  AND BTRIM(p.department) <> ''
  AND LOWER(BTRIM(p.department)) = LOWER(d.name);

ALTER TABLE persons
    ADD CONSTRAINT fk_persons_department
        FOREIGN KEY (department_id) REFERENCES departments(id);

CREATE INDEX idx_persons_department_id ON persons (department_id);

ALTER TABLE persons DROP COLUMN department;
