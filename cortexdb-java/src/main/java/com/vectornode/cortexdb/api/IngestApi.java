package com.vectornode.cortexdb.api;

import com.vectornode.cortexdb.config.HttpClientWrapper;
import com.vectornode.cortexdb.models.ConverserRole;
import com.vectornode.cortexdb.models.IngestDocumentRequest;
import com.vectornode.cortexdb.models.IngestPromptRequest;
import com.vectornode.cortexdb.models.IngestResponse;

import java.util.Map;

/**
 * Wraps the {@code /api/v1/memory/ingest} endpoints.
 */
public class IngestApi {

    private final HttpClientWrapper http;

    public IngestApi(HttpClientWrapper http) {
        this.http = http;
    }

    /**
     * Ingest a prompt payload into CortexDB.
     * The server will perform semantic compression and online synthesis.
     *
     * @param uid       User identifier.
     * @param converser Role of the converser (USER, AGENT, or SYSTEM).
     * @param text      The text content to ingest.
     * @return IngestResponse with the created KnowledgeBase and processing info.
     */
    public IngestResponse prompt(String uid, ConverserRole converser, String text) {
        return prompt(uid, converser, text, null);
    }

    /**
     * Ingest a prompt with optional metadata.
     *
     * @param uid       User identifier.
     * @param converser Role of the converser.
     * @param text      The text content to ingest.
     * @param metadata  Optional metadata map.
     * @return IngestResponse with the created KnowledgeBase and processing info.
     */
    public IngestResponse prompt(String uid, ConverserRole converser, String text, Map<String, Object> metadata) {
        IngestPromptRequest request = new IngestPromptRequest(uid, converser, text, metadata);
        return http.post("/api/v1/memory/ingest/prompt", request, IngestResponse.class);
    }

    /**
     * Ingest a prompt using a string converser role (convenience overload).
     */
    public IngestResponse prompt(String uid, String converser, String text) {
        return prompt(uid, ConverserRole.valueOf(converser.toUpperCase()), text);
    }

    /**
     * Ingest a large document into CortexDB.
     * The server will extract a hierarchical page index (document tree).
     *
     * @param uid           User identifier.
     * @param documentTitle Title of the document.
     * @param documentText  The full text content of the document.
     * @return IngestResponse with the created KnowledgeBase and processing info.
     */
    public IngestResponse document(String uid, String documentTitle, String documentText) {
        IngestDocumentRequest request = new IngestDocumentRequest(uid, documentTitle, documentText);
        return http.post("/api/v1/memory/ingest/document", request, IngestResponse.class);
    }
}
