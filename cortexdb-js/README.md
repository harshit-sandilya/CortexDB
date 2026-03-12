# CortexDB JavaScript/TypeScript SDK

Lightweight TypeScript client SDK for [CortexDB](https://github.com/harshit-sandilya/CortexDB) — wraps the REST API with full type safety.

## Requirements

- **Node.js 18+**
- **npm 9+** (or any compatible package manager)

## Installation

```bash
npm install cortexdb
```

## Quick Start

```ts
import { CortexDB, ConverserRole } from "cortexdb";

const db = new CortexDB("http://localhost:8080");

// 1. Configure LLM provider
await db.setup.configure("GEMINI", "gemini-2.0-flash", "gemini-embedding-001", "your-api-key");

// 2. Ingest structured document or prompt
await db.ingest.document(
  "user-1", "Quantum Computing",
  "Quantum computing uses qubits to perform computations..."
);
await db.ingest.prompt("user-1", ConverserRole.USER, "What are qubits?");

// 3. Search contexts
const results = await db.query.searchContexts("What is quantum computing?");
for (const r of results.results) {
  console.log(`${r.score?.toFixed(2)} — ${r.content}`);
}

// 4. Entity lookup
const entity = await db.query.getEntityByName("Google");

// 5. Graph traversal
const connections = await db.query.getOutgoingConnections(entity!.id!);
```

## API Overview

### Setup

```ts
await db.setup.configure(provider, chatModel, embedModel);
await db.setup.configure(provider, chatModel, embedModel, apiKey);
await db.setup.configure(provider, chatModel, embedModel, apiKey, baseUrl);
```

Supported providers: `GEMINI`, `OPENAI`, `ANTHROPIC`, `AZURE`, `OPENROUTER`.

### Ingest

```ts
await db.ingest.document(uid, documentTitle, documentText);
await db.ingest.document(uid, documentTitle, documentText, metadata);
await db.ingest.prompt(uid, converser, text);
await db.ingest.prompt(uid, converser, text, metadata);
```

Converser roles: `USER`, `AGENT`, `SYSTEM`.

### Query — Contexts

```ts
await db.query.searchContexts(query);
await db.query.searchContexts(query, limit, minRelevance, filters);
await db.query.getContextsByKb(kbId);
await db.query.getRecentContexts(days);
await db.query.searchRecentContexts(query, days);
await db.query.getSiblingContexts(contextId);
```

### Query — Entities

```ts
await db.query.searchEntities(query);
await db.query.getEntityByName(name);               // returns null if not found
await db.query.getEntityByNameIgnoreCase(name);
await db.query.getEntityIdByName(name);
await db.query.disambiguateEntity(name, context);
await db.query.getContextsForEntity(entityId);
await db.query.getEntitiesForContext(contextId);
await db.query.mergeEntities(sourceId, targetId);
```

### Query — History

```ts
await db.query.searchHistory(query);
await db.query.getHistoryByUser(uid);
await db.query.getRecentKbs(hours);
await db.query.getKbsSince(isoTimestamp);
await db.query.deleteUserData(uid);                  // GDPR
```

### Query — Graph

```ts
await db.query.getOutgoingConnections(entityId);
await db.query.getIncomingConnections(entityId);
await db.query.getTwoHopConnections(entityId);
await db.query.getTopRelations(limit);
await db.query.getRelationsBySource(sourceId);
await db.query.getRelationsByTarget(targetId);
await db.query.getRelationsByType(relationType);
```

### Query — Hybrid Search

```ts
await db.query.hybridSearch(query);
await db.query.hybridSearch(query, limit, minRelevance);
```

## TypeScript Types

All models are fully typed and exported from the package:

```ts
import {
  // Enums
  LLMApiProvider,
  ConverserRole,
  // Types
  type SetupRequest,
  type SetupResponse,
  type IngestRequest,
  type IngestResponse,
  type KnowledgeBase,
  type QueryRequest,
  type QueryResponse,
  type SearchResult,
  type Entity,
  type Relation,
} from "cortexdb";
```

## Configuration

| Parameter  | Default                  | Description                        |
|------------|--------------------------|------------------------------------|
| `baseUrl`  | `http://localhost:8080`  | CortexDB server URL               |
| `timeout`  | `30000`                  | Request timeout in milliseconds    |

```ts
const db = new CortexDB("https://cortex.example.com", 60_000);
```

## Building

```bash
cd cortexdb-js
npm install          # install dependencies
npm run build        # compile TypeScript
npm test             # run unit tests
npm run test:e2e     # run end-to-end tests
npm run test:all     # run all tests
```
