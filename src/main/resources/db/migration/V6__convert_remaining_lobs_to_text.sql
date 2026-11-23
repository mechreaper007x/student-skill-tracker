-- Remove PostgreSQL large-object OID mappings from remaining String fields.
-- These fields are ordinary application text/JSON, so store them as TEXT and
-- avoid auto-commit LOB stream access during normal entity reads.
DO $$
DECLARE
    target_table TEXT;
    target_column TEXT;
    target_type TEXT;
BEGIN
    FOR target_table, target_column IN
        SELECT * FROM (VALUES
            ('skill_data', 'ai_advice'),
            ('rishi_practice_tasks', 'details'),
            ('rishi_practice_tasks', 'metadata_json'),
            ('rishi_code_change_events', 'meta_json'),
            ('rishi_compile_attempt_logs', 'source_code_snapshot')
        ) AS columns_to_convert(table_name, column_name)
    LOOP
        SELECT c.data_type
          INTO target_type
          FROM information_schema.columns c
         WHERE c.table_schema = current_schema()
           AND c.table_name = target_table
           AND c.column_name = target_column;

        IF target_type = 'oid' THEN
            EXECUTE format(
                'ALTER TABLE %I ALTER COLUMN %I TYPE TEXT USING convert_from(lo_get(%I), ''UTF8'')',
                target_table,
                target_column,
                target_column
            );
        END IF;

        target_type := NULL;
    END LOOP;
END
$$;
