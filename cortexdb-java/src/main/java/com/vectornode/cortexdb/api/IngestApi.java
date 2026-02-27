package com.vectornode.cortexdb.api;

import com.vectornode.cortexdb.config.HttpClientWrapper;
import com.vectornode.cortexdb.models.ConverserRole;
import com.vectornode.cortexdb.models.IngestRequest;
import com.vectornode.cortexdb.models.IngestResponse;

import java.util.Map;

/**
 * Wraps the {@code /api/ingest} endpoints.
 */
public class IngestApi {

    private final HttpClientWrapper http;

    public IngestApi(HttpClientWrapper http) {
        this.http = http;
    }

    /**
     * Ingest a document into CortexDB.
     * The server will chunk the content, generate embeddings,
     * and extract entities/relations automatically.
     *
     * @param uid       User identifier.
     * @param converser Role of the converser (USER, AGENT, or SYSTEM).
     * @param content   The text content to ingest.
     * @return IngestResponse with the created KnowledgeBase and processing info.
     */
    public IngestResponse document(String uid, ConverserRole converser, String content) {
        return document(uid, converser, content, null);
    }

    /**
     * Ingest a document with optional metadata.
     *
     * @param uid       User identifier.
     * @param converser Role of the converser.
     * @param content   The text content to ingest.
     * @param metadata  Optional metadata map.
     * @return IngestResponse with the created KnowledgeBase and processing info.
     */
    public IngestResponse document(String uid, ConverserRole converser,
            String content, Map<String, Object> metadata) {
        IngestRequest request = new IngestRequest(uid, converser, content, metadata);
        return http.post("/api/ingest/document", request, IngestResponse.class);
    }

    /**
     * Ingest a document using a string converser role (convenience overload).
     */
    public IngestResponse document(String uid, String converser, String content) {
        return document(uid, ConverserRole.valueOf(converser.toUpperCase()), content);
    }
}
