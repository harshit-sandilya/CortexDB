-- Update schema to use UUID instead of SERIAL
-- Drop existing tables and recreate with UUID

DROP TRIGGER IF EXISTS notify_context_created ON contexts;
DROP TRIGGER IF EXISTS notify_kb_created ON knowledge_bases;
DROP FUNCTION IF EXISTS notify_rag_event();

DROP TABLE IF EXISTS relations CASCADE;
DROP TABLE IF EXISTS entity_context_junction CASCADE;
DROP TABLE IF EXISTS entities CASCADE;
DROP TABLE IF EXISTS contexts CASCADE;
DROP TABLE IF EXISTS knowledge_bases CASCADE;

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- 1. Knowledge Base (Raw Input)
CREATE TABLE knowledge_bases (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    uid VARCHAR NOT NULL,
    converser VARCHAR NOT NULL,
    content TEXT NOT NULL,
    vector_embedding vector(3072),
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Contexts (Chunks)
CREATE TABLE contexts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    kb_id UUID REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    text_chunk TEXT NOT NULL,
    vector_embedding vector(3072) NOT NULL,
    chunk_index INT,
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. Entities (Extracted Concepts)
CREATE TABLE entities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity_name TEXT NOT NULL,
    entity_type TEXT,
    description TEXT,
    vector_embedding vector(3072),
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. Entity-Context Junction (Many-to-Many)
CREATE TABLE entity_context_junction (
    entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    context_id UUID REFERENCES contexts(id) ON DELETE CASCADE,
    PRIMARY KEY (entity_id, context_id)
);

-- 5. Relations (Graph Edges)
CREATE TABLE relations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    target_entity_id UUID REFERENCES entities(id) ON DELETE CASCADE,
    relation_type TEXT NOT NULL,
    edge_weight INT DEFAULT 1,
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (source_entity_id, target_entity_id, relation_type)
);

-- Indexes for performance
CREATE INDEX idx_kb_uid ON knowledge_bases(uid);
CREATE INDEX idx_kb_created ON knowledge_bases(created_at);
CREATE INDEX idx_contexts_kb_id ON contexts(kb_id);
CREATE INDEX idx_contexts_created_at ON contexts(created_at);
CREATE INDEX idx_entity_name ON entities(entity_name);
CREATE INDEX idx_relations_source ON relations(source_entity_id);
CREATE INDEX idx_relations_target ON relations(target_entity_id);

-- Vector indexes for similarity search
CREATE INDEX idx_kb_vector ON knowledge_bases USING ivfflat (vector_embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX idx_contexts_vector ON contexts USING ivfflat (vector_embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX idx_entities_vector ON entities USING ivfflat (vector_embedding vector_cosine_ops) WITH (lists = 100);

-- Trigger function for async notifications
CREATE OR REPLACE FUNCTION notify_rag_event()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM pg_notify('rag_events', json_build_object(
        'type', TG_ARGV[0],
        'id', NEW.id,
        'content', CASE WHEN TG_ARGV[0] = 'KB_CREATED' THEN NEW.content ELSE NULL END,
        'text_chunk', CASE WHEN TG_ARGV[0] = 'CONTEXT_CREATED' THEN NEW.text_chunk ELSE NULL END,
        'kb_id', CASE WHEN TG_ARGV[0] = 'CONTEXT_CREATED' THEN NEW.kb_id ELSE NULL END
    )::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger: Notify when new knowledge_base entry is created
CREATE TRIGGER notify_kb_created
    AFTER INSERT ON knowledge_bases
    FOR EACH ROW
    EXECUTE FUNCTION notify_rag_event('KB_CREATED');

-- Trigger: Notify when new context is created
CREATE TRIGGER notify_context_created
    AFTER INSERT ON contexts
    FOR EACH ROW
    EXECUTE FUNCTION notify_rag_event('CONTEXT_CREATED');
