-- Enable extension if not exists
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Function to notify when a new KnowledgeBase entry is created
CREATE OR REPLACE FUNCTION notify_kb_insert() RETURNS TRIGGER AS $$
DECLARE
    payload json;
BEGIN
    payload = json_build_object('id', NEW.id, 'userId', NEW.user_id, 'queryText', NEW.query_text);
    PERFORM pg_notify('new_kb_entry', payload::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for KnowledgeBase
DROP TRIGGER IF EXISTS after_kb_insert ON knowledge_base;
CREATE TRIGGER after_kb_insert
AFTER INSERT ON knowledge_base
FOR EACH ROW EXECUTE FUNCTION notify_kb_insert();

-- Function to notify when a new Context entry is created
CREATE OR REPLACE FUNCTION notify_context_insert() RETURNS TRIGGER AS $$
DECLARE
    payload json;
BEGIN
    -- unique_context_id matches the field name in Entity
    payload = json_build_object('uniqueContextId', NEW.unique_context_id, 'contextData', NEW.context_data);
    PERFORM pg_notify('new_context_entry', payload::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for Context
DROP TRIGGER IF EXISTS after_context_insert ON contexts;
CREATE TRIGGER after_context_insert
AFTER INSERT ON contexts
FOR EACH ROW EXECUTE FUNCTION notify_context_insert();
