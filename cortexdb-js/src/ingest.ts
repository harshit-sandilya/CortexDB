/**
 * Ingest API wrapper for CortexDB.
 *
 * Equivalent to cortexdb-py/cortexdb/ingest.py
 */

import { HttpClient } from "./httpWrapper.js";
import {
    ConverserRole,
    type IngestPromptRequest,
    type IngestDocumentRequest,
    type IngestResponse,
} from "./models.js";

export class IngestAPI {
    /** @internal */
    constructor(private readonly http: HttpClient) { }

    /**
     * Ingest a prompt payload into CortexDB.
     * The server will perform semantic compression and online synthesis.
     *
     * @param uid        User identifier.
     * @param converser  Role of the converser ("USER", "AGENT", or "SYSTEM").
     * @param text       The text content to ingest.
     * @param metadata   Optional metadata dictionary.
     * @returns IngestResponse with the created KnowledgeBase and processing info.
     */
    async prompt(
        uid: string,
        converser: string | ConverserRole,
        text: string,
        metadata?: Record<string, any>,
    ): Promise<IngestResponse> {
        // Normalise string converser to enum value
        const resolvedConverser =
            typeof converser === "string"
                ? (converser.toUpperCase() as ConverserRole)
                : converser;

        const request: IngestPromptRequest = {
            uid,
            converser: resolvedConverser,
            text,
            ...(metadata !== undefined && { metadata }),
        };

        return this.http.post<IngestResponse>("/api/v1/memory/ingest/prompt", request);
    }

    /**
     * Ingest a large document into CortexDB.
     * The server will extract a hierarchical page index (document tree).
     *
     * @param uid           User identifier.
     * @param documentTitle Title of the document.
     * @param documentText  The full text content of the document.
     * @returns IngestResponse with the created KnowledgeBase and processing info.
     */
    async document(
        uid: string,
        documentTitle: string,
        documentText: string,
    ): Promise<IngestResponse> {
        const request: IngestDocumentRequest = {
            uid,
            documentTitle,
            documentText,
        };

        return this.http.post<IngestResponse>("/api/v1/memory/ingest/document", request);
    }
}
