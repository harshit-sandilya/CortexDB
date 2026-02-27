package com.vectornode.cortexdb;

import com.vectornode.cortexdb.api.IngestApi;
import com.vectornode.cortexdb.api.QueryApi;
import com.vectornode.cortexdb.api.SetupApi;
import com.vectornode.cortexdb.config.HttpClientWrapper;

/**
 * Main entry point for the CortexDB Java SDK.
 *
 * <p>
 * Usage:
 * 
 * <pre>{@code
 * CortexDBClient db = new CortexDBClient("http://localhost:8080");
 *
 * // Configure LLM
 * db.setup().configure(LLMApiProvider.GEMINI, "api-key",
 *         "gemini-2.0-flash", "gemini-embedding-001");
 *
 * // Ingest content
 * db.ingest().document("user-1", ConverserRole.USER, "Hello world");
 *
 * // Query
 * QueryResponse results = db.query().searchContexts("greeting");
 * }</pre>
 */
public class CortexDBClient {

    private final HttpClientWrapper http;
    private final SetupApi setupApi;
    private final IngestApi ingestApi;
    private final QueryApi queryApi;

    /**
     * Create a CortexDBClient with default timeout (30 seconds).
     *
     * @param baseUrl Base URL of the CortexDB server (e.g.
     *                "http://localhost:8080").
     */
    public CortexDBClient(String baseUrl) {
        this(baseUrl, 30);
    }

    /**
     * Create a CortexDBClient with a custom timeout.
     *
     * @param baseUrl        Base URL of the CortexDB server.
     * @param timeoutSeconds Request timeout in seconds.
     */
    public CortexDBClient(String baseUrl, long timeoutSeconds) {
        this.http = new HttpClientWrapper(baseUrl, timeoutSeconds);
        this.setupApi = new SetupApi(http);
        this.ingestApi = new IngestApi(http);
        this.queryApi = new QueryApi(http);
    }

    /** Access the Setup API ({@code /api/setup}). */
    public SetupApi setup() {
        return setupApi;
    }

    /** Access the Ingest API ({@code /api/ingest}). */
    public IngestApi ingest() {
        return ingestApi;
    }

    /** Access the Query API ({@code /api/query}). */
    public QueryApi query() {
        return queryApi;
    }

    /**
     * Access the underlying HTTP wrapper (advanced usage).
     */
    public HttpClientWrapper http() {
        return http;
    }

    @Override
    public String toString() {
        return "CortexDBClient{baseUrl='" + http + "'}";
    }
}
