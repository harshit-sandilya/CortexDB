/**
 * Main CortexDB client — entry point for the JavaScript/TypeScript SDK.
 *
 * Equivalent to cortexdb-py/cortexdb/client.py
 *
 * @example
 * ```ts
 * import { CortexDB } from "cortexdb";
 *
 * const db = new CortexDB("http://localhost:8080");
 *
 * // Configure LLM
 * await db.setup.configure("GEMINI", "gemini-2.0-flash", "gemini-embedding-001", "your-api-key");
 *
 * // Ingest content
 * await db.ingest.document("user-1", "USER", "Hello world");
 *
 * // Query
 * const results = await db.query.searchContexts("greeting");
 * ```
 */

import { HttpClient } from "./httpWrapper.js";
import { SetupAPI } from "./setup.js";
import { IngestAPI } from "./ingest.js";
import { QueryAPI } from "./query.js";

export class CortexDB {
    /** Setup API — configure LLM providers. */
    public readonly setup: SetupAPI;
    /** Ingest API — ingest documents into CortexDB. */
    public readonly ingest: IngestAPI;
    /** Query API — search contexts, entities, history, and graph. */
    public readonly query: QueryAPI;

    private readonly http: HttpClient;

    /**
     * Initialize the CortexDB client.
     *
     * @param baseUrl  Base URL of the CortexDB server (default: http://localhost:8080).
     * @param timeout  Request timeout in milliseconds (default: 30 000).
     */
    constructor(baseUrl: string = "http://localhost:8080", timeout: number = 30_000) {
        this.http = new HttpClient(baseUrl, timeout);

        this.setup = new SetupAPI(this.http);
        this.ingest = new IngestAPI(this.http);
        this.query = new QueryAPI(this.http);
    }

    toString(): string {
        return `CortexDB(baseUrl="${this.http}")`;
    }
}
