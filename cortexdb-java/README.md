# CortexDB Java SDK

Lightweight Java client SDK for [CortexDB](https://github.com/harshit-sandilya/CortexDB) — wraps the REST API and provides native LLM functions.

## Requirements

- **Java 21+**
- **Maven 3.9+**

## Installation

```xml
<dependency>
    <groupId>com.vectornode</groupId>
    <artifactId>cortexdb-java</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

```java
import com.vectornode.cortexdb.CortexDBClient;
import com.vectornode.cortexdb.models.*;

CortexDBClient db = new CortexDBClient("http://localhost:8080");

// 1. Configure LLM provider
SetupResponse setup = db.setup().configure(
    LLMApiProvider.GEMINI, "your-api-key",
    "gemini-2.0-flash", "gemini-embedding-001"
);

// 2. Ingest a document
IngestResponse ingest = db.ingest().document(
    "user-1", ConverserRole.USER,
    "Quantum computing uses qubits to perform computations..."
);

// 3. Search contexts
QueryResponse results = db.query().searchContexts("What is quantum computing?");
for (SearchResult r : results.getResults()) {
    System.out.printf("%.2f — %s%n", r.getScore(), r.getContent());
}

// 4. Entity lookup
Entity entity = db.query().getEntityByName("Google");

// 5. Graph traversal
QueryResponse connections = db.query().getOutgoingConnections(entity.getId());
```

## API Overview

### Setup
```java
db.setup().configure(provider, apiKey, chatModel, embedModel);
db.setup().configure(provider, apiKey, chatModel, embedModel, baseUrl);
```

### Ingest
```java
db.ingest().document(uid, converser, content);
db.ingest().document(uid, converser, content, metadata);
```

### Query — Contexts
```java
db.query().searchContexts(query);
db.query().searchContexts(query, limit, minRelevance, filters);
db.query().getContextsByKb(kbId);
db.query().getRecentContexts(days);
db.query().searchRecentContexts(query, days);
db.query().getSiblingContexts(contextId);
```

### Query — Entities
```java
db.query().searchEntities(query);
db.query().getEntityByName(name);          // returns null if not found
db.query().getEntityByNameIgnoreCase(name);
db.query().getEntityIdByName(name);
db.query().disambiguateEntity(name, context);
db.query().getContextsForEntity(entityId);
db.query().getEntitiesForContext(contextId);
db.query().mergeEntities(sourceId, targetId);
```

### Query — History
```java
db.query().searchHistory(query);
db.query().getHistoryByUser(uid);
db.query().getRecentKbs(hours);
db.query().getKbsSince(isoTimestamp);
db.query().deleteUserData(uid); // GDPR
```

### Query — Graph
```java
db.query().getOutgoingConnections(entityId);
db.query().getIncomingConnections(entityId);
db.query().getTwoHopConnections(entityId);
db.query().getTopRelations(limit);
db.query().getRelationsBySource(sourceId);
db.query().getRelationsByTarget(targetId);
db.query().getRelationsByType(relationType);
```

### Query — Hybrid Search
```java
db.query().hybridSearch(query);
db.query().hybridSearch(query, limit, minRelevance);
```

## Native LLM Provider

Call LLMs directly from client-side without routing through the backend:

```java
import com.vectornode.cortexdb.llm.LLMProvider;

LLMProvider llm = new LLMProvider("GEMINI", "your-api-key",
    "gemini-2.0-flash", "gemini-embedding-001", null);

List<Float> embedding = llm.getEmbedding("Hello world");
String response = llm.callLLM("Explain quantum computing");
```

Supported providers: `GEMINI`, `OPENAI`, `AZURE`.

## Building

```bash
cd cortexdb-java
mvn clean compile    # compile
mvn test             # run tests
mvn package          # build JAR
```
