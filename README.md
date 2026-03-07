# CortexDB

**A RAG-powered memory database with built-in knowledge graphs.**

CortexDB is an open-source backend that gives your AI applications persistent, structured memory. Ingest documents and CortexDB will automatically chunk text, generate vector embeddings, extract entities and relationships, and build a queryable knowledge graph — all powered by the LLM of your choice.

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20+%20pgvector-4169E1?logo=postgresql&logoColor=white)](https://github.com/pgvector/pgvector)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## Features

- **Vector Search** — Semantic similarity search using pgvector on contexts, entities, and history
- **Knowledge Graph** — Automatic entity/relation extraction with weighted edges and graph traversal (1-hop, 2-hop)
- **Hybrid Search** — Combines vector similarity with graph-based context for richer results
- **Multi-LLM Support** — Gemini, OpenAI, Anthropic, Azure, OpenRouter — switch providers with a single API call
- **Async Ingestion Pipeline** — Fire-and-forget architecture using PostgreSQL triggers for non-blocking document processing
- **GDPR Compliance** — Built-in endpoint to delete all user data
- **Official SDKs** — Python, JavaScript/TypeScript, and Java client libraries

---

## Quick Start

### 1. Clone & Run

```bash
git clone https://github.com/harshit-sandilya/CortexDB.git
cd CortexDB/memory
docker compose up -d
```

This starts 2 containers:
| Service | Port | Description |
|---------|------|-------------|
| **PostgreSQL** (pgvector) | 5432 | Vector-enabled database |
| **Backend** | 8080 | CortexDB REST API |

### 2. Configure an LLM Provider

```bash
curl -X POST http://localhost:8080/api/setup \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "GEMINI",
    "chatModelName": "gemini-2.0-flash",
    "embedModelName": "gemini-embedding-001",
    "apiKey": "YOUR_API_KEY"
  }'
```

### 3. Ingest a Document

```bash
curl -X POST http://localhost:8080/api/ingest/document \
  -H "Content-Type: application/json" \
  -d '{
    "uid": "user-1",
    "converser": "USER",
    "content": "Java uses garbage collection for automatic memory management. The JVM handles this process."
  }'
```

### 4. Query

```bash
curl -X POST http://localhost:8080/api/query/contexts \
  -H "Content-Type: application/json" \
  -d '{"query": "How does Java manage memory?", "limit": 5, "minRelevance": 0.7}'
```

---

## How It Works — End-to-End Pipeline

When you ingest a document, CortexDB processes it through a multi-stage async pipeline. Here's exactly what happens:

### Async Architecture

```
┌─────────────┐     ┌───────────────┐     ┌──────────────────────────┐
│  API Client  │────▶│ IngestService │────▶│  knowledge_bases table   │
│  (you)       │◀────│ (synchronous) │     │  + vector embedding      │
│              │ 200 │               │     │  + JSONB metadata        │
└─────────────┘     └───────────────┘     └────────────┬─────────────┘
                                                       │ PostgreSQL
                                                       │ NOTIFY trigger
                                          ┌────────────▼─────────────┐
                                          │ PostgresNotification     │
                                          │ Listener                 │
                                          │ (LISTEN rag_events)      │
                                          └────────────┬─────────────┘
                                                       │ @Async dispatch
                                          ┌────────────▼─────────────┐
                                          │ IngestionWorker          │
                                          │ (background thread)      │
                                          │                          │
                                          │ 1. Chunk text            │
                                          │ 2. Generate embeddings   │
                                          │ 3. Persist contexts ─────┼──▶ NOTIFY trigger
                                          │ 4. Extract entities      │    (CONTEXT_CREATED)
                                          │ 5. Extract relations     │
                                          │ 6. Build graph           │
                                          │ 7. Log to console        │
                                          └──────────────────────────┘
```

> **Key:** The API response returns immediately after step 1. All chunking, embedding, entity extraction, and graph building happens asynchronously in the background. Every persisted row is logged to console with structured tags (`KB_ROW`, `CONTEXT_ROW`, `ENTITY_ROW`, `JUNCTION_ROW`, `RELATION_NEW`, `RELATION_INCREMENT`).

### Data Flow Through the 5 Tables

**Example input:** *"Java uses garbage collection for automatic memory management. The JVM handles this process."*

#### Step 1 — `knowledge_bases` (raw input stored synchronously)

| id | uid | converser | content | vector_embedding | metadata | created_at |
|----|-----|-----------|---------|------------------|----------|------------|
| `a1b2...` | `user-1` | `USER` | *"Java uses garbage collection..."* | `[0.12, -0.45, ...]` (768d) | `{contentLength: 90, embeddingDimensions: 768, embeddingTimeMs: 120}` | `2025-01-15T10:00:00Z` |

→ **Response returned to client.** Everything below happens in the background.

#### Step 2 — `contexts` (text chunked + embedded)

| id | kb_id | text_chunk | vector_embedding | chunk_index | metadata | created_at |
|----|-------|------------|------------------|-------------|----------|------------|
| `c1...` | `a1b2...` | *"Java uses garbage collection for automatic memory management."* | `[0.08, ...]` (768d) | 0 | `{chunkLength: 62, embeddingDimensions: 768, chunkNumber: 1, totalChunks: 2}` | `2025-01-15T10:00:01Z` |
| `c2...` | `a1b2...` | *"The JVM handles this process."* | `[-0.23, ...]` (768d) | 1 | `{chunkLength: 30, embeddingDimensions: 768, chunkNumber: 2, totalChunks: 2}` | `2025-01-15T10:00:01Z` |

#### Step 3 — `entities` (concepts extracted via LLM)

| id | entity_name | entity_type | description | vector_embedding | metadata | created_at |
|----|-------------|-------------|-------------|------------------|----------|------------|
| `e1...` | Java | Technology | *"A programming language..."* | `[0.31, ...]` | `{extractedFrom: "context", contextId: "c1...", embeddingDimensions: 768}` | `2025-01-15T10:00:02Z` |
| `e2...` | Garbage Collection | Concept | *"Automatic memory reclamation..."* | `[-0.14, ...]` | `{extractedFrom: "context", contextId: "c1...", embeddingDimensions: 768}` | `2025-01-15T10:00:02Z` |
| `e3...` | JVM | Technology | *"Java Virtual Machine..."* | `[0.27, ...]` | `{extractedFrom: "context", contextId: "c2...", embeddingDimensions: 768}` | `2025-01-15T10:00:02Z` |

> If `Java` already exists from a previous ingestion, it is **not duplicated** — the existing entity is reused and linked.

#### Step 4 — `entity_context_junction` (many-to-many links)

| entity_id | context_id |
|-----------|------------|
| `e1...` (Java) | `c1...` (Chunk 1) |
| `e2...` (Garbage Collection) | `c1...` (Chunk 1) |
| `e3...` (JVM) | `c2...` (Chunk 2) |

#### Step 5 — `relations` (knowledge graph edges)

| id | source_entity_id | target_entity_id | relation_type | edge_weight | metadata | created_at |
|----|-------------------|-------------------|---------------|-------------|----------|------------|
| `r1...` | `e1...` (Java) | `e2...` (Garbage Collection) | `uses` | **1** | `{extractedFrom: "context", contextId: "c1..."}` | `2025-01-15T10:00:02Z` |
| `r2...` | `e3...` (JVM) | `e2...` (Garbage Collection) | `manages` | **1** | `{extractedFrom: "context", contextId: "c2..."}` | `2025-01-15T10:00:02Z` |

> **Edge weight increments:** If a second document also states *"Java uses garbage collection"*, the `edge_weight` on `r1` increments to **2**. Stronger connections = higher weight.

---

## Architecture

```
                    ┌──────────────────────────────────────────────┐
                    │              CortexDB Server                 │
                    │          (Spring Boot 3.5 + Java 21)         │
                    │                                              │
  SDK / curl ──────▶│  /api/setup    → SetupController             │
                    │  /api/ingest   → IngestController            │
                    │  /api/query    → QueryController (20+ routes)│
                    │                                              │
                    │  ┌──────────┐  ┌─────────────┐               │
                    │  │ LLM      │  │ Ingestion   │               │
                    │  │ Provider │  │ Worker      │               │
                    │  │ (chat +  │  │ (@Async)    │               │
                    │  │ embed)   │  │             │               │
                    │  └──────────┘  └─────────────┘               │
                    └───────────────────┬──────────────────────────┘
                                        │
                    ┌───────────────────▼──────────────────────────┐
                    │       PostgreSQL 16 + pgvector               │
                    │                                              │
                    │  knowledge_bases ──┐                         │
                    │  contexts ─────────┤ (vector + JSONB)        │
                    │  entities ─────────┤                         │
                    │  entity_context_junction                     │
                    │  relations ────────┘ (weighted graph)        │
                    │                                              │
                    │  Flyway migrations │ NOTIFY triggers         │
                    └──────────────────────────────────────────────┘
```

---

## Supported LLM Providers

| Provider | Chat | Embeddings | Setup Required |
|----------|------|------------|----------------|
| **Gemini** | ✅ | ✅ | API key |
| **OpenAI** | ✅ | ✅ | API key |
| **Anthropic** | ✅ | ✅ | API key |
| **Azure OpenAI** | ✅ | ✅ | API key + endpoint |
| **OpenRouter** | ✅ | ✅ | API key |

---

## Client SDKs

| SDK | Install | Docs |
|-----|---------|------|
| **Python** | `pip install cortexdb` | [cortexdb-py/README.md](cortexdb-py/README.md) |
| **JavaScript/TypeScript** | `npm install cortexdb` | [cortexdb-js/README.md](cortexdb-js/README.md) |
| **Java** | Maven dependency | [cortexdb-java/README.md](cortexdb-java/README.md) |

### Quick Example (Python)

```python
from cortexdb import CortexDB

db = CortexDB("http://localhost:8080")

db.setup.configure(provider="GEMINI", api_key="...",
                   chat_model="gemini-2.0-flash",
                   embed_model="gemini-embedding-001")

db.ingest.document(uid="user-1", converser="USER",
                   content="Your document text here...")

results = db.query.search_contexts("your question", limit=5)
```

---

## REST API Reference

Full API docs: [**📖 Documentation Site**](https://harshit-sandilya.github.io/CortexDB/)

| Group | Endpoints | Description |
|-------|-----------|-------------|
| **Setup** | `POST /api/setup` | Configure LLM provider |
| **Ingest** | `POST /api/ingest/document` | Ingest a document |
| **Contexts** | 6 endpoints | Semantic search, by KB, recent, date range, siblings |
| **Entities** | 8 endpoints | Search, lookup, disambiguate, merge |
| **History** | 4 endpoints | Search history, by user, recent, since timestamp |
| **Graph** | 7 endpoints | Outgoing, incoming, 2-hop, top relations, by source/target/type |
| **Hybrid** | `POST /api/query/hybrid` | Combined vector + graph search |
| **User Data** | `DELETE /api/query/user/{uid}` | GDPR deletion |

---

## Running Locally (Development)

```bash
# Start database
cd memory
docker compose up -d postgres

# Run the Spring Boot backend
./mvnw spring-boot:run

# Run tests
./mvnw test
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/mydatabase` | Database URL |
| `SPRING_DATASOURCE_USERNAME` | `myuser` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `secret` | DB password |

---

## Project Structure

```
CortexDB/
├── memory/                 # Spring Boot backend (the core)
│   ├── src/main/java/
│   │   └── com/vectornode/memory/
│   │       ├── config/          # LLM provider configuration
│   │       ├── entity/          # JPA entities (KnowledgeBase, Context, RagEntity, Relation)
│   │       ├── ingest/          # Ingestion pipeline (controller, service, worker, listener)
│   │       ├── query/           # Query endpoints (controller, service, repositories)
│   │       └── setup/           # LLM setup (controller, service)
│   ├── compose.yaml        # Docker Compose (Postgres + Backend)
│   └── Dockerfile
├── cortexdb-py/            # Python SDK
├── cortexdb-js/            # JavaScript/TypeScript SDK
├── cortexdb-java/          # Java SDK
└── docs/                   # Documentation site (GitHub Pages)
```

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.