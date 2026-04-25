-- Fix trigger compatibility issues
-- This migration ensures triggers can be dropped even if they don't exist
-- without causing syntax errors in different PostgreSQL versions

-- Create a helper function to safely drop triggers
CREATE OR REPLACE FUNCTION drop_trigger_if_exists(p_trigger_name VARCHAR, p_table_name VARCHAR)
RETURNS VOID AS $$
DECLARE
    trigger_exists BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = p_trigger_name
        AND tgrelid = p_table_name::regclass
    ) INTO trigger_exists;

    IF trigger_exists THEN
        EXECUTE format('DROP TRIGGER %I ON %I', p_trigger_name, p_table_name);
        RAISE NOTICE 'Dropped trigger % ON %', p_trigger_name, p_table_name;
    ELSE
        RAISE NOTICE 'Trigger % does not exist on %, skipping', p_trigger_name, p_table_name;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Use the helper function to safely drop triggers
SELECT drop_trigger_if_exists('notify_context_created', 'contexts');
SELECT drop_trigger_if_exists('notify_kb_created', 'knowledge_bases');
