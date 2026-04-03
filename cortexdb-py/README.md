# CortexDB Python SDK
[![PyPI version](https://img.shields.io/pypi/v/cortexdb-py.svg)](https://pypi.org/project/cortexdb-py/)
[![PyPI downloads](https://img.shields.io/pypi/dm/cortexdb-py.svg)](https://pypi.org/project/cortexdb-py/)
[![PyPI - Python Version](https://img.shields.io/pypi/pyversions/cortexdb-py.svg)](https://pypi.org/project/cortexdb-py/)

A Python client for the CortexDB RAG backend, providing easy access to ingestion, querying, and LLM orchestration.

## Features

- **Setup API**: Configure LLM providers (Gemini, OpenAI, Azure).
- **Ingest API**: Ingest documents with automatic embedding generation.
- **Query API**: Perform semantic search, entity retrieval, and graph traversals.
- **Query Intent Learning**: Adaptive query routing and intent statistics tracking.
- **Contradiction Detection**: Automatic identification and resolution of conflicting information.
- **Native LLM Integration**: Uses `google-genai` for native Gemini support.

## Installation
```bash
pip install cortexdb-py
```

Or visit the package on [PyPI](https://pypi.org/project/cortexdb-py/).

## Usage

See `main.py` for a complete example of setting up a medical chatbot.

```python
from cortexdb import CortexDB

db = CortexDB("http://localhost:8080")

# Setup
db.setup.configure(provider="GEMINI", api_key="...", chat_model="gemini-2.0-flash", embed_model="gemini-embedding-001")

# Ingest
db.ingest.document(uid="user-1", document_title="Test Doc", document_text="Hello world")
db.ingest.prompt(uid="user-1", converser="USER", text="What is this?")

# Query
results = db.query.search_contexts("Hello", limit=5)
```

## Advanced Features

### Query Intent Learning

Track and analyze query patterns to improve retrieval performance:

```python
# Get intent statistics for adaptive retrieval
intent_stats = db.query.get_intent_stats("550e8400-e29b-41d4-a716-446655440000")
print(f"Context: {intent_stats['contextId']}")
print(f"Retrievals: {intent_stats['totalRetrievals']}")
print(f"Success Rate: {intent_stats['weightedSuccesses']}")
print(f"Estimated Boost: {intent_stats['estimatedBoost']}")
```

### Contradiction Detection

Identify and manage conflicting information in your knowledge base:

```python
# Get all detected contradictions
contradictions = db.query.get_all_contradictions()
for contradiction in contradictions.results:
    print(f"⚡ {contradiction.content}")
    print(f"  Severity: {contradiction.metadata['severity']}")
    print(f"  Summary: {contradiction.metadata['summary']}")

# Get contradictions for a specific context
context_contradictions = db.query.get_contradictions_for_context(context_id)

# Mark a contradiction as resolved
db.query.resolve_contradiction("770e8400-e29b-41d4-a716-446655440000")
```

## Testing

```bash
pip install pytest respx
pytest tests/
```
