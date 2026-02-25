/**
 * Query API wrapper for CortexDB.
 *
 * Equivalent to cortexdb-py/cortexdb/query.py
 * Mirrors all 18+ methods across contexts, entities, history,
 * user-data, graph, and hybrid search endpoints.
 */

import { HttpClient } from "./httpWrapper.js";
import type {
    Entity,
    QueryRequest,
    QueryResponse,
    Relation,
} from "./models.js";

export class QueryAPI {
    /** @internal */
    constructor(private readonly http: HttpClient) { }

    // ── Context endpoints ────────────────────────────────────────────

    /**
     * Semantic search on contexts.
     *
     * @param query         Search query text.
     * @param limit         Maximum number of results (default 5).
     * @param minRelevance  Minimum relevance score 0-1 (default 0.7).
     * @param filters       Optional filters dict.
     */
    async searchContexts(
        query: string,
        limit: number = 5,
        minRelevance: number = 0.7,
        filters?: Record<string, any>,
    ): Promise<QueryResponse> {
        return this.postQuery("/api/query/contexts", query, limit, minRelevance, filters);
    }

    /** Get all contexts for a knowledge base. */
    async getContextsByKb(kbId: string): Promise<QueryResponse> {
        return this.http.get<QueryResponse>(`/api/query/contexts/kb/${kbId}`);
    }

    /** Get recent contexts from the last N days. */
    async getRecentContexts(days: number = 7): Promise<QueryResponse> {
        return this.http.get<QueryResponse>("/api/query/contexts/recent", { days });
    }

    /** Search recent contexts with vector similarity. */
    async searchRecentContexts(
        query: string,
        days: number = 7,
        limit: number = 5,
        minRelevance: number = 0.7,
    ): Promise<QueryResponse> {
        return this.postQuery(
            "/api/query/contexts/recent/search",
            query,
            limit,
            minRelevance,
            undefined,
            { days },
        );
    }

    /** Get sibling contexts (other chunks from the same document). */
    async getSiblingContexts(contextId: string): Promise<QueryResponse> {
        return this.http.get<QueryResponse>(`/api/query/contexts/${contextId}/siblings`);
    }

    // ── Entity endpoints ─────────────────────────────────────────────

    /** Semantic search on entities. */
    async searchEntities(
        query: string,
        limit: number = 5,
        minRelevance: number = 0.7,
    ): Promise<QueryResponse> {
        return this.postQuery("/api/query/entities", query, limit, minRelevance);
    }

    /** Find entity by exact name. Returns null if not found. */
    async getEntityByName(name: string): Promise<Entity | null> {
        return this.http.get<Entity>(`/api/query/entities/name/${name}`);
    }

    /** Find entity by name (case-insensitive). Returns null if not found. */
    async getEntityByNameIgnoreCase(name: string): Promise<Entity | null> {
        return this.http.get<Entity>(`/api/query/entities/name/${name}/ignorecase`);
    }

    /** Get entity ID by name. Returns null if not found. */
    async getEntityIdByName(name: string): Promise<string | null> {
        const data = await this.http.get<{ id: string }>(`/api/query/entities/id/${name}`);
        return data?.id ?? null;
    }

    /** Disambiguate entity using vector similarity with context. */
    async disambiguateEntity(entityName: string, contextText: string): Promise<Entity | null> {
        return this.http.post<Entity>(
            "/api/query/entities/disambiguate",
            contextText,
            { entityName },
            { "Content-Type": "text/plain" },
        );
    }

    /** Get all contexts where an entity is mentioned. */
    async getContextsForEntity(entityId: string): Promise<any[]> {
        return this.http.get<any[]>(`/api/query/entities/${entityId}/contexts`);
    }

    /** Get all entities mentioned in a context. */
    async getEntitiesForContext(contextId: string): Promise<Entity[]> {
        return this.http.get<Entity[]>(`/api/query/contexts/${contextId}/entities`);
    }

    /** Merge two entities (source into target). */
    async mergeEntities(sourceEntityId: string, targetEntityId: string): Promise<void> {
        await this.http.post<void>(
            "/api/query/entities/merge",
            undefined,
            { sourceEntityId, targetEntityId },
        );
    }

    // ── History endpoints ────────────────────────────────────────────

    /** Semantic search on knowledge bases (history). */
    async searchHistory(
        query: string,
        limit: number = 5,
        minRelevance: number = 0.7,
    ): Promise<QueryResponse> {
        return this.postQuery("/api/query/history", query, limit, minRelevance);
    }

    /** Get all history for a user. */
    async getHistoryByUser(uid: string): Promise<QueryResponse> {
        return this.http.get<QueryResponse>(`/api/query/history/user/${uid}`);
    }

    /** Get recent knowledge bases from the last N hours. */
    async getRecentKbs(hours: number = 24): Promise<QueryResponse> {
        return this.http.get<QueryResponse>("/api/query/history/recent", { hours });
    }

    /** Get knowledge bases since a timestamp (ISO-8601). */
    async getKbsSince(since: string): Promise<QueryResponse> {
        return this.http.get<QueryResponse>("/api/query/history/since", { since });
    }

    // ── User data ────────────────────────────────────────────────────

    /** Delete all data for a user (GDPR compliance). */
    async deleteUserData(uid: string): Promise<void> {
        await this.http.delete<void>(`/api/query/user/${uid}`);
    }

    // ── Graph endpoints ──────────────────────────────────────────────

    /** Get outgoing relations for an entity. */
    async getOutgoingConnections(entityId: string): Promise<QueryResponse> {
        return this.http.get<QueryResponse>(`/api/query/graph/outgoing/${entityId}`);
    }

    /** Get incoming relations for an entity. */
    async getIncomingConnections(entityId: string): Promise<QueryResponse> {
        return this.http.get<QueryResponse>(`/api/query/graph/incoming/${entityId}`);
    }

    /** Get 2-hop connections (entity names reachable in 2 hops). */
    async getTwoHopConnections(entityId: string): Promise<string[]> {
        return this.http.get<string[]>(`/api/query/graph/two-hop/${entityId}`);
    }

    /** Get top/strongest relations. */
    async getTopRelations(limit: number = 10): Promise<Relation[]> {
        return this.http.get<Relation[]>("/api/query/graph/top-relations", { limit });
    }

    /** Get relations by source entity. */
    async getRelationsBySource(sourceId: string): Promise<Relation[]> {
        return this.http.get<Relation[]>(`/api/query/graph/relations/source/${sourceId}`);
    }

    /** Get relations by target entity. */
    async getRelationsByTarget(targetId: string): Promise<Relation[]> {
        return this.http.get<Relation[]>(`/api/query/graph/relations/target/${targetId}`);
    }

    /** Get relations by type. */
    async getRelationsByType(relationType: string): Promise<Relation[]> {
        return this.http.get<Relation[]>(`/api/query/graph/relations/type/${relationType}`);
    }

    // ── Hybrid search ────────────────────────────────────────────────

    /** Hybrid search combining vector and graph results. */
    async hybridSearch(
        query: string,
        limit: number = 5,
        minRelevance: number = 0.7,
    ): Promise<QueryResponse> {
        return this.postQuery("/api/query/hybrid", query, limit, minRelevance);
    }

    // ── Internal helpers ─────────────────────────────────────────────

    /** @internal Send a POST query request and return the parsed response. */
    private async postQuery(
        url: string,
        query: string,
        limit: number = 5,
        minRelevance: number = 0.7,
        filters?: Record<string, any>,
        params?: Record<string, any>,
    ): Promise<QueryResponse> {
        const request: QueryRequest = { query, limit, minRelevance };
        if (filters) {
            request.filters = filters;
        }
        return this.http.post<QueryResponse>(url, request, params);
    }
}
