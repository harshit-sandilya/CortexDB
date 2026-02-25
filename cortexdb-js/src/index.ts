/**
 * CortexDB JavaScript/TypeScript SDK — A RAG-powered memory database client.
 *
 * Equivalent to cortexdb-py/cortexdb/__init__.py
 */

// ── Main client ──────────────────────────────────────────────────────
export { CortexDB } from "./client.js";

// ── Sub-API classes ──────────────────────────────────────────────────
export { SetupAPI } from "./setup.js";
export { IngestAPI } from "./ingest.js";
export { QueryAPI } from "./query.js";

// ── HTTP layer ───────────────────────────────────────────────────────
export { HttpClient } from "./httpWrapper.js";

// ── Models & Enums ───────────────────────────────────────────────────
export {
    // Enums
    LLMApiProvider,
    ConverserRole,
    // Setup
    type SetupRequest,
    type SetupResponse,
    // Ingest
    type IngestRequest,
    type KnowledgeBase,
    type IngestResponse,
    // Query
    type QueryRequest,
    type SearchResult,
    type QueryResponse,
    // Entity / Relation
    type Entity,
    type Relation,
} from "./models.js";
