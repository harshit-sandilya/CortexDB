#!/usr/bin/env python3
"""Test script to check actual embedding dimensions from Gemini."""

import os
from dotenv import load_dotenv
from llama_index.embeddings.gemini import GeminiEmbedding

# Load environment variables
load_dotenv()

# Get API key
api_key = os.getenv("LLM_API_KEY")
if not api_key:
    print("ERROR: LLM_API_KEY environment variable is required")
    exit(1)

print("Testing Gemini embedding dimensions...")

# Test with dimensionality constraint
try:
    embed_model = GeminiEmbedding(
        api_key=api_key,
        model_name="models/gemini-embedding-001",
        output_dimensionality=768,
    )

    # Generate a test embedding
    test_text = "This is a test sentence to check embedding dimensions."
    embedding = embed_model.get_text_embedding(test_text)

    print(f"Embedding generated successfully!")
    print(f"Number of dimensions: {len(embedding)}")
    print(f"First 5 values: {embedding[:5]}")

except Exception as e:
    print(f"Error generating embedding: {e}")
    print(f"Error type: {type(e).__name__}")
    import traceback
    traceback.print_exc()