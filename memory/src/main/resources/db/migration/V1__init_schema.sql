-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- 1. The Raw Input (User Queries/Docs)
CREATE TABLE knowledge_base (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR NOT NULL,  -- Managed externally by developers (kept as VARCHAR per user preference)
    content TEXT,
    vector vector(1536),
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. The Chunks (Splitting the input)
CREATE TABLE contexts (
    id SERIAL PRIMARY KEY,
    kb_id INT REFERENCES knowledge_base(id) ON DELETE CASCADE,
    context_data TEXT,
    vector vector(1536),
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. The Concepts (Extracted Entities)
-- Note: Using metadata JSONB for entity type instead of explicit 'type' column
CREATE TABLE entities (
    id SERIAL PRIMARY KEY,
    entity_name TEXT,
    vector vector(1536),
    metadata JSONB,  -- Store 'type' here for polysemy (e.g., {"type": "Company"})
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. The Junction (Many-to-Many: Which entities appear in which chunk?)
CREATE TABLE entity_contexts (
    entity_id INT REFERENCES entities(id) ON DELETE CASCADE,
    context_id INT REFERENCES contexts(id) ON DELETE CASCADE,
    PRIMARY KEY (entity_id, context_id)
);

-- 5. The Graph (How entities relate)
CREATE TABLE relations (
    source_id INT REFERENCES entities(id),
    target_id INT REFERENCES entities(id),
    relation_type TEXT,
    edge_weight INT DEFAULT 1,
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (source_id, target_id, relation_type)
);

-- Indexes for performance
CREATE INDEX idx_kb_user_id ON knowledge_base(user_id);
CREATE INDEX idx_kb_created_at ON knowledge_base(created_at);
CREATE INDEX idx_contexts_kb_id ON contexts(kb_id);
CREATE INDEX idx_contexts_created_at ON contexts(created_at);
CREATE INDEX idx_entities_name ON entities(entity_name);
CREATE INDEX idx_relations_source ON relations(source_id);
CREATE INDEX idx_relations_target ON relations(target_id);

-- Vector indexes for similarity search (IVFFlat for performance)
CREATE INDEX idx_kb_vector ON knowledge_base USING ivfflat (vector vector_cosine_ops) WITH (lists = 100);
CREATE INDEX idx_contexts_vector ON contexts USING ivfflat (vector vector_cosine_ops) WITH (lists = 100);
CREATE INDEX idx_entities_vector ON entities USING ivfflat (vector vector_cosine_ops) WITH (lists = 100);

-- Trigger function for async notifications (RAG pipeline)
CREATE OR REPLACE FUNCTION notify_rag_event()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM pg_notify('rag_events', json_build_object(
        'type', TG_ARGV[0],
        'id', NEW.id
    )::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger: Notify when new knowledge_base entry is created
CREATE TRIGGER notify_kb_created
    AFTER INSERT ON knowledge_base
    FOR EACH ROW
    EXECUTE FUNCTION notify_rag_event('KB_CREATED');

-- Trigger: Notify when new context is created
CREATE TRIGGER notify_context_created
    AFTER INSERT ON contexts
    FOR EACH ROW
    EXECUTE FUNCTION notify_rag_event('CONTEXT_CREATED');
