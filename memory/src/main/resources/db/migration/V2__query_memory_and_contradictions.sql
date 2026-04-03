-- ============================================================
-- V2: Query Intent Memory + Contradiction Detection
-- ============================================================

-- ========================
-- Feature 1: Query Intent Memory
-- ========================

-- Stores every query for intent-based re-ranking
CREATE TABLE query_logs (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    uid                  VARCHAR,
    query_text           TEXT NOT NULL,
    query_embedding      vector(768) NOT NULL,
    retrieved_context_ids TEXT,
    session_continued    BOOLEAN NOT NULL DEFAULT FALSE,
    follow_up_count      INT NOT NULL DEFAULT 0,
    last_follow_up_at    TIMESTAMPTZ,
    created_at           TIMESTAMPTZ DEFAULT NOW()
);

-- Vector similarity index for finding past queries similar to the current one
CREATE INDEX idx_query_logs_vector
    ON query_logs USING hnsw (query_embedding vector_cosine_ops);

-- For follow-up detection: find recent queries by the same user
CREATE INDEX idx_query_logs_uid_time
    ON query_logs (uid, created_at DESC);

-- ========================
-- Feature 2: Contradiction Detection
-- ========================

-- Stores context-level contradictions (chunk vs chunk)
CREATE TABLE context_contradictions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    context_id_a UUID REFERENCES contexts(id) ON DELETE CASCADE,
    context_id_b UUID REFERENCES contexts(id) ON DELETE CASCADE,
    contradiction_summary TEXT,
    severity VARCHAR(20) DEFAULT 'MODERATE',
    resolved BOOLEAN DEFAULT FALSE,
    resolution_context_id UUID REFERENCES contexts(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (context_id_a, context_id_b)
);

CREATE INDEX idx_contradictions_a ON context_contradictions(context_id_a);
CREATE INDEX idx_contradictions_b ON context_contradictions(context_id_b);
