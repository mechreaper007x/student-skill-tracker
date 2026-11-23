-- Adds per-student OpenAI-compatible gateway configuration.
-- The project already provisions the students table outside Flyway on existing deployments.
ALTER TABLE students
    ADD COLUMN IF NOT EXISTS ai_base_url VARCHAR(1024);
