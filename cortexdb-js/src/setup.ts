/**
 * Setup API wrapper for CortexDB.
 *
 * Equivalent to cortexdb-py/cortexdb/setup.py
 */

import { HttpClient } from "./httpWrapper.js";
import {
    LLMApiProvider,
    type SetupRequest,
    type SetupResponse,
} from "./models.js";

export class SetupAPI {
    /** @internal */
    constructor(private readonly http: HttpClient) { }

    /**
     * Configure the LLM provider on the CortexDB server.
     *
     * @param provider    LLM provider name (e.g. "GEMINI", "OPENAI", "AZURE").
     * @param chatModel   Name of the chat model.
     * @param embedModel  Name of the embedding model.
     * @param apiKey      API key for the provider (optional).
     * @param baseUrl     Custom base URL (optional).
     * @returns SetupResponse with configuration details.
     */
    async configure(
        provider: string | LLMApiProvider,
        chatModel: string,
        embedModel: string,
        apiKey?: string,
        baseUrl?: string,
    ): Promise<SetupResponse> {
        // Normalise string provider to enum value
        const resolvedProvider =
            typeof provider === "string"
                ? (provider.toUpperCase() as LLMApiProvider)
                : provider;

        const request: SetupRequest = {
            provider: resolvedProvider,
            chatModelName: chatModel,
            embedModelName: embedModel,
            ...(apiKey !== undefined && { apiKey }),
            ...(baseUrl !== undefined && { baseUrl }),
        };

        return this.http.post<SetupResponse>("/api/setup", request);
    }
}
