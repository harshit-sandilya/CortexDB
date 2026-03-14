# CortexDB Python SDK
[![PyPI version](https://img.shields.io/pypi/v/cortexdb-py.svg)](https://pypi.org/project/cortexdb-py/)
[![PyPI downloads](https://img.shields.io/pypi/dm/cortexdb-py.svg)](https://pypi.org/project/cortexdb-py/)
[![PyPI - Python Version](https://img.shields.io/pypi/pyversions/cortexdb-py.svg)](https://pypi.org/project/cortexdb-py/)

A Python client for the CortexDB RAG backend, providing easy access to ingestion, querying, and LLM orchestration.

## Features

- **Setup API**: Configure LLM providers (Gemini, OpenAI, Azure).
- **Ingest API**: Ingest documents with automatic embedding generation.
- **Query API**: Perform semantic search, entity retrieval, and graph traversals.
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

## Testing

```bash
pip install pytest respx
pytest tests/
```
