package com.vectornode.cortexdb.models;

/**
 * Supported LLM API providers.
 * Mirrors the backend
 * {@code com.vectornode.memory.entity.enums.LLMApiProvider}.
 */
public enum LLMApiProvider {
    GEMINI,
    OPENAI,
    ANTHROPIC,
    AZURE,
    OPENROUTER
}
