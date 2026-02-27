package com.vectornode.cortexdb.llm;

import com.vectornode.cortexdb.exceptions.LLMException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LLMProvider.
 * Tests initialization and validation only (real API calls require valid keys).
 */
class LLMProviderTest {

    @Test
    void constructor_gemini_succeeds() {
        LLMProvider llm = new LLMProvider("GEMINI", "test-key",
                "gemini-2.0-flash", "gemini-embedding-001", null);
        assertNotNull(llm);
    }

    @Test
    void constructor_openai_succeeds() {
        LLMProvider llm = new LLMProvider("OPENAI", "test-key",
                "gpt-4", "text-embedding-ada-002", null);
        assertNotNull(llm);
    }

    @Test
    void constructor_azure_succeeds() {
        LLMProvider llm = new LLMProvider("AZURE", "test-key",
                "gpt-4", "text-embedding-ada-002", "https://myresource.openai.azure.com/");
        assertNotNull(llm);
    }

    @Test
    void constructor_unsupportedProvider_throws() {
        assertThrows(IllegalArgumentException.class, () -> new LLMProvider("UNSUPPORTED", "test-key",
                "model", "embed-model", null));
    }

    @Test
    void constructor_caseInsensitive() {
        // Provider name should be normalized to uppercase
        LLMProvider llm = new LLMProvider("gemini", "test-key",
                "gemini-2.0-flash", "gemini-embedding-001", null);
        assertNotNull(llm);
    }

    @Test
    void getEmbedding_withInvalidKey_throwsLLMException() {
        LLMProvider llm = new LLMProvider("GEMINI", "invalid-key",
                "gemini-2.0-flash", "gemini-embedding-001", null);

        // Should throw LLMException because the API key is invalid
        assertThrows(LLMException.class, () -> llm.getEmbedding("test text"));
    }

    @Test
    void callLLM_withInvalidKey_throwsLLMException() {
        LLMProvider llm = new LLMProvider("GEMINI", "invalid-key",
                "gemini-2.0-flash", "gemini-embedding-001", null);

        // Should throw LLMException because the API key is invalid
        assertThrows(LLMException.class, () -> llm.callLLM("test prompt"));
    }
}
