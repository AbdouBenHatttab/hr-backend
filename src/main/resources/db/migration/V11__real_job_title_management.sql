CREATE TABLE job_titles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    default_salary NUMERIC(10, 3),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

WITH normalized_job_titles AS (
    SELECT BTRIM(job_title) AS trimmed_name
    FROM persons
    WHERE job_title IS NOT NULL
      AND BTRIM(job_title) <> ''
),
deduplicated_job_titles AS (
    SELECT MIN(trimmed_name) AS name
    FROM normalized_job_titles
    GROUP BY LOWER(trimmed_name)
)
INSERT INTO job_titles (name, description, active, default_salary, created_at, updated_at)
SELECT name,
       'Migrated from legacy person.job_title values',
       TRUE,
       NULL,
       NOW(),
       NOW()
FROM deduplicated_job_titles;

CREATE UNIQUE INDEX uk_job_titles_name_lower ON job_titles (LOWER(name));

ALTER TABLE persons ADD COLUMN job_title_id BIGINT;

UPDATE persons p
SET job_title_id = j.id
FROM job_titles j
WHERE p.job_title IS NOT NULL
  AND BTRIM(p.job_title) <> ''
  AND LOWER(BTRIM(p.job_title)) = LOWER(j.name);

ALTER TABLE persons
    ADD CONSTRAINT fk_persons_job_title
        FOREIGN KEY (job_title_id) REFERENCES job_titles(id);

CREATE INDEX idx_persons_job_title_id ON persons (job_title_id);

ALTER TABLE persons DROP COLUMN job_title;
