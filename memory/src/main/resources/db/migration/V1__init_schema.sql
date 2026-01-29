-- Enable pgvector extension for vector similarity search
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================
-- TABLES (Matching Java Entities)
-- ============================================

-- knowledge_bases: Source documents
CREATE TABLE knowledge_bases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid VARCHAR(255) NOT NULL,
    converser VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    vector_embedding vector(1536),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_kb_uid ON knowledge_bases(uid);
CREATE INDEX idx_kb_created ON knowledge_bases(created_at);

-- contexts: Chunked text with embeddings
CREATE TABLE contexts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kb_id UUID NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    text_chunk TEXT NOT NULL,
    vector_embedding vector(1536) NOT NULL,
    chunk_index INTEGER,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_context_kb ON contexts(kb_id);

-- entities: Extracted knowledge nodes
CREATE TABLE entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(100),
    description TEXT,
    vector_embedding vector(1536),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_entity_name ON entities(entity_name);

-- relations: Graph edges between entities
CREATE TABLE relations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_entity_id UUID NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    target_entity_id UUID NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    relation_type VARCHAR(100) NOT NULL,
    edge_weight INTEGER DEFAULT 1,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_source_target ON relations(source_entity_id, target_entity_id);

-- entity_context_junction: Many-to-Many link
CREATE TABLE entity_context_junction (
    entity_id UUID NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    context_id UUID NOT NULL REFERENCES contexts(id) ON DELETE CASCADE,
    PRIMARY KEY (entity_id, context_id)
);

-- ============================================
-- TRIGGERS FOR ASYNC PIPELINE (LISTEN/NOTIFY)
-- ============================================

-- Function: Notify application when new KB is inserted
CREATE OR REPLACE FUNCTION notify_kb_created()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM pg_notify('rag_events', json_build_object(
        'type', 'KB_CREATED',
        'id', NEW.id::text
    )::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function: Notify application when new Context is inserted
CREATE OR REPLACE FUNCTION notify_context_created()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM pg_notify('rag_events', json_build_object(
        'type', 'CONTEXT_CREATED',
        'id', NEW.id::text
    )::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger: After Insert on knowledge_bases
CREATE TRIGGER kb_insert_trigger
AFTER INSERT ON knowledge_bases
FOR EACH ROW
EXECUTE FUNCTION notify_kb_created();

-- Trigger: After Insert on contexts
CREATE TRIGGER context_insert_trigger
AFTER INSERT ON contexts
FOR EACH ROW
EXECUTE FUNCTION notify_context_created();
