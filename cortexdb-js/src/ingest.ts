/**
 * Ingest API wrapper for CortexDB.
 *
 * Equivalent to cortexdb-py/cortexdb/ingest.py
 */

import { HttpClient } from "./httpWrapper.js";
import {
    ConverserRole,
    type IngestRequest,
    type IngestResponse,
} from "./models.js";

export class IngestAPI {
    /** @internal */
    constructor(private readonly http: HttpClient) { }

    /**
     * Ingest a document into CortexDB.
     *
     * The server will chunk the content, generate embeddings,
     * and extract entities/relations automatically.
     *
     * @param uid        User identifier.
     * @param converser  Role of the converser ("USER", "AGENT", or "SYSTEM").
     * @param content    The text content to ingest.
     * @param metadata   Optional metadata dictionary.
     * @returns IngestResponse with the created KnowledgeBase and processing info.
     */
    async document(
        uid: string,
        converser: string | ConverserRole,
        content: string,
        metadata?: Record<string, any>,
    ): Promise<IngestResponse> {
        // Normalise string converser to enum value
        const resolvedConverser =
            typeof converser === "string"
                ? (converser.toUpperCase() as ConverserRole)
                : converser;

        const request: IngestRequest = {
            uid,
            converser: resolvedConverser,
            content,
            ...(metadata !== undefined && { metadata }),
        };

        return this.http.post<IngestResponse>("/api/ingest/document", request);
    }
}
