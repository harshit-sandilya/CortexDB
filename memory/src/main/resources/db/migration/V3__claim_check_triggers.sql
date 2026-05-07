-- Claim Check Pattern: Remove content from pg_notify payloads
-- pg_notify has an 8000-byte hard limit on payloads.
-- Large text (e.g., HotpotQA Wikipedia paragraphs) exceeds this limit.
-- Instead, send only the record ID and let the listener fetch content by ID.

-- 1. Update KB trigger to send only ID + converser (no content)
CREATE OR REPLACE FUNCTION notify_kb_event()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM pg_notify('rag_events', json_build_object(
        'type', 'KB_CREATED',
        'id', NEW.id,
        'converser', NEW.converser
    )::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. Update Context trigger to send only ID + kb_id (no text_chunk)
CREATE OR REPLACE FUNCTION notify_context_event()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM pg_notify('rag_events', json_build_object(
        'type', 'CONTEXT_CREATED',
        'id', NEW.id,
        'kb_id', NEW.kb_id
    )::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
