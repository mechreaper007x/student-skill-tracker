-- Hibernate @Lob String fields are stored as PostgreSQL OIDs.
-- Reading those large objects after the repository transaction ends can fail
-- with "Large Objects may not be used in auto-commit mode". Convert the
-- contents into normal TEXT columns before the entity stops using @Lob.
DO $$
DECLARE
    target_column TEXT;
    target_type TEXT;
BEGIN
    FOREACH target_column IN ARRAY ARRAY[
        'rishi_memory_json',
        'rishi_study_plan',
        'emotion_log_json'
    ]
    LOOP
        SELECT c.data_type
          INTO target_type
          FROM information_schema.columns c
         WHERE c.table_schema = current_schema()
           AND c.table_name = 'students'
           AND c.column_name = target_column;

        IF target_type = 'oid' THEN
            EXECUTE format(
                'ALTER TABLE students ALTER COLUMN %I TYPE TEXT USING convert_from(lo_get(%I), ''UTF8'')',
                target_column,
                target_column
            );
        END IF;
    END LOOP;
END
$$;
