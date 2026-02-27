/**
 * Unit tests for QueryAPI.
 * Equivalent to cortexdb-py/tests/test_query.py
 * Covers all endpoint groups: contexts, entities, history, user, graph, hybrid.
 */

import { describe, it, expect, afterEach } from "vitest";
import { CortexDB } from "../../src/index.js";
import {
    mockFetch,
    restoreFetch,
    SAMPLE_QUERY_RESPONSE,
    EMPTY_QUERY_RESPONSE,
    SAMPLE_ENTITY,
    SAMPLE_RELATION,
    KB_ID,
    ENTITY_ID,
    SOURCE_ID,
    TARGET_ID,
} from "./helpers.js";

afterEach(() => restoreFetch());

// ─── Context endpoints ───────────────────────────────────────────────

describe("QueryAPI — Context endpoints", () => {
    it("searchContexts() sends correct POST payload", async () => {
        let capturedBody: any;

        mockFetch((req) => {
            expect(req.url).toBe("/api/query/contexts");
            expect(req.method).toBe("POST");
            capturedBody = req.body;
            return { status: 200, body: SAMPLE_QUERY_RESPONSE };
        });

        const db = new CortexDB("http://testserver");
        const resp = await db.query.searchContexts("test query", 10, 0.5);

        expect(resp.results).toHaveLength(1);
        expect(resp.results[0].content).toBe("Test content");
        expect(resp.results[0].score).toBe(0.85);
        expect(capturedBody.query).toBe("test query");
        expect(capturedBody.limit).toBe(10);
        expect(capturedBody.minRelevance).toBe(0.5);
    });

    it("searchContexts() uses sensible defaults (limit=5, minRelevance=0.7)", async () => {
        let capturedBody: any;
        mockFetch((req) => { capturedBody = req.body; return { status: 200, body: EMPTY_QUERY_RESPONSE }; });

        const db = new CortexDB("http://testserver");
        await db.query.searchContexts("hello");

        expect(capturedBody.limit).toBe(5);
        expect(capturedBody.minRelevance).toBe(0.7);
    });

    it("getContextsByKb() calls correct GET endpoint", async () => {
        mockFetch((req) => {
            expect(req.url).toBe(`/api/query/contexts/kb/${KB_ID}`);
            expect(req.method).toBe("GET");
            return { status: 200, body: SAMPLE_QUERY_RESPONSE };
        });

        const db = new CortexDB("http://testserver");
        const resp = await db.query.getContextsByKb(KB_ID);
        expect(resp.results).toHaveLength(1);
    });

    it("getRecentContexts() sends days as query param", async () => {
        mockFetch((req) => {
            expect(req.url).toBe("/api/query/contexts/recent");
            expect(req.searchParams.get("days")).toBe("14");
            return { status: 200, body: SAMPLE_QUERY_RESPONSE };
        });

        const db = new CortexDB("http://testserver");
        const resp = await db.query.getRecentContexts(14);
        expect(resp.results[0].content).toBe("Test content");
    });

    it("searchRecentContexts() sends both days param and POST body", async () => {
        let capturedBody: any;
        mockFetch((req) => {
            expect(req.url).toBe("/api/query/contexts/recent/search");
            expect(req.searchParams.get("days")).toBe("3");
            capturedBody = req.body;
            return { status: 200, body: SAMPLE_QUERY_RESPONSE };
        });

        const db = new CortexDB("http://testserver");
        await db.query.searchRecentContexts("something recent", 3);
        expect(capturedBody.query).toBe("something recent");
    });

    it("getSiblingContexts() calls correct GET endpoint", async () => {
        const contextId = "abc-123";
        mockFetch((req) => {
            expect(req.url).toBe(`/api/query/contexts/${contextId}/siblings`);
            return { status: 200, body: EMPTY_QUERY_RESPONSE };
        });

        const db = new CortexDB("http://testserver");
        const resp = await db.query.getSiblingContexts(contextId);
        expect(resp.results).toHaveLength(0);
    });
});

// ─── Entity endpoints ────────────────────────────────────────────────

describe("QueryAPI — Entity endpoints", () => {
    it("searchEntities() POSTs to /api/query/entities", async () => {
        mockFetch((req) => {
            expect(req.url).toBe("/api/query/entities");
            expect(req.method).toBe("POST");
            return { status: 200, body: SAMPLE_QUERY_RESPONSE };
        });

        const db = new CortexDB("http://testserver");
        const resp = await db.query.searchEntities("machine learning");
        expect(resp.results).toHaveLength(1);
    });

    it("getEntityByName() returns an entity when found", async () => {
        mockFetch((req) => {
            expect(req.url).toBe("/api/query/entities/name/Google");
            return { status: 200, body: SAMPLE_ENTITY };
        });

        const db = new CortexDB("http://testserver");
        const entity = await db.query.getEntityByName("Google");

        expect(entity).not.toBeNull();
        expect(entity!.name).toBe("Google");
        expect(entity!.type).toBe("ORGANIZATION");
    });

    it("getEntityByName() returns null on 404", async () => {
        mockFetch(() => ({ status: 404 }));

        const db = new CortexDB("http://testserver");
        const entity = await db.query.getEntityByName("NonExistent");
        expect(entity).toBeNull();
    });

    it("getEntityByNameIgnoreCase() calls ignorecase endpoint", async () => {
        mockFetch((req) => {
            expect(req.url).toBe("/api/query/entities/name/google/ignorecase");
            return { status: 200, body: SAMPLE_ENTITY };
        });

        const db = new CortexDB("http://testserver");
        const entity = await db.query.getEntityByNameIgnoreCase("google");
        expect(entity!.name).toBe("Google");
    });

    it("getEntityIdByName() returns the id string when found", async () => {
        mockFetch((req) => {
            expect(req.url).toBe("/api/query/entities/id/Google");
            return { status: 200, body: { id: ENTITY_ID } };
        });

        const db = new CortexDB("http://testserver");
        const id = await db.query.getEntityIdByName("Google");
        expect(id).toBe(ENTITY_ID);
    });

    it("getEntityIdByName() returns null on 404", async () => {
        mockFetch(() => ({ status: 404 }));

        const db = new CortexDB("http://testserver");
        const id = await db.query.getEntityIdByName("Missing");
        expect(id).toBeNull();
    });

    it("getContextsForEntity() returns an array", async () => {
        mockFetch((req) => {
            expect(req.url).toBe(`/api/query/entities/${ENTITY_ID}/contexts`);
            return { status: 200, body: [{ id: "ctx-1" }] };
        });

        const db = new CortexDB("http://testserver");
        const contexts = await db.query.getContextsForEntity(ENTITY_ID);
        expect(contexts).toHaveLength(1);
    });

    it("getEntitiesForContext() returns entity array", async () => {
        mockFetch((req) => {
            expect(req.url).toBe(`/api/query/contexts/${ENTITY_ID}/entities`);
            return { status: 200, body: [SAMPLE_ENTITY] };
        });

        const db = new CortexDB("http://testserver");
        const entities = await db.query.getEntitiesForContext(ENTITY_ID);
        expect(entities).toHaveLength(1);
        expect(entities[0].name).toBe("Google");
    });

    it("mergeEntities() POSTs to merge endpoint with correct params", async () => {
        mockFetch((req) => {
            expect(req.url).toBe("/api/query/entities/merge");
            expect(req.method).toBe("POST");
            expect(req.searchParams.get("sourceEntityId")).toBe(SOURCE_ID);
            expect(req.searchParams.get("targetEntityId")).toBe(TARGET_ID);
            return { status: 200 };
        });

        const db = new CortexDB("http://testserver");
        await expect(db.query.mergeEntities(SOURCE_ID, TARGET_ID)).resolves.not.toThrow();
    });
});

// ─── History endpoints ───────────────────────────────────────────────

describe("QueryAPI — History endpoints", () => {
    it("searchHistory() POSTs to /api/query/history", async () => {
        mockFetch((req) => {
            expect(req.url).toBe("/api/query/history");
            return { status: 200, body: SAMPLE_QUERY_RESPONSE };
        });

        const db = new CortexDB("http://testserver");
        const resp = await db.query.searchHistory("previous conversation");
        expect(resp.results).toHaveLength(1);
    });

    it("getHistoryByUser() GETs user history", async () => {
        mockFetch((req) => {
            expect(req.url).toBe("/api/query/history/user/user-1");
            return { status: 200, body: SAMPLE_QUERY_RESPONSE };
        });

        const db = new CortexDB("http://testserver");
        const resp = await db.query.getHistoryByUser("user-1");
        expect(resp.results).toHaveLength(1);
    });

    it("getHistoryByUser() returns empty results for unknown user", async () => {
        mockFetch(() => ({ status: 200, body: EMPTY_QUERY_RESPONSE }));

        const db = new CortexDB("http://testserver");
        const resp = await db.query.getHistoryByUser("unknown");
        expect(resp.results).toHaveLength(0);
    });

    it("getRecentKbs() sends hours as query param", async () => {
        mockFetch((req) => {
            expect(req.searchParams.get("hours")).toBe("48");
            return { status: 200, body: EMPTY_QUERY_RESPONSE };
        });

        const db = new CortexDB("http://testserver");
        await db.query.getRecentKbs(48);
    });

    it("getKbsSince() sends since as query param", async () => {
        mockFetch((req) => {
            expect(req.searchParams.get("since")).toBe("2025-01-01T00:00:00Z");
            return { status: 200, body: EMPTY_QUERY_RESPONSE };
        });

        const db = new CortexDB("http://testserver");
        await db.query.getKbsSince("2025-01-01T00:00:00Z");
    });

    it("deleteUserData() sends DELETE request", async () => {
        mockFetch((req) => {
            expect(req.url).toBe("/api/query/user/user-1");
            expect(req.method).toBe("DELETE");
            return { status: 200 };
        });

        const db = new CortexDB("http://testserver");
        await expect(db.query.deleteUserData("user-1")).resolves.not.toThrow();
    });
});

// ─── Graph endpoints ─────────────────────────────────────────────────

describe("QueryAPI — Graph endpoints", () => {
    it("getOutgoingConnections() calls correct endpoint", async () => {
        mockFetch((req) => {
            expect(req.url).toBe(`/api/query/graph/outgoing/${ENTITY_ID}`);
            return { status: 200, body: SAMPLE_QUERY_RESPONSE };
        });

        const db = new CortexDB("http://testserver");
        const resp = await db.query.getOutgoingConnections(ENTITY_ID);
        expect(resp.results).toHaveLength(1);
    });

    it("getIncomingConnections() calls correct endpoint", async () => {
        mockFetch((req) => {
            expect(req.url).toBe(`/api/query/graph/incoming/${ENTITY_ID}`);
            return { status: 200, body: EMPTY_QUERY_RESPONSE };
        });

        const db = new CortexDB("http://testserver");
        const resp = await db.query.getIncomingConnections(ENTITY_ID);
        expect(resp.results).toHaveLength(0);
    });

    it("getTwoHopConnections() returns an array of strings", async () => {
        mockFetch((req) => {
            expect(req.url).toBe(`/api/query/graph/two-hop/${ENTITY_ID}`);
            return { status: 200, body: ["Entity A", "Entity B"] };
        });

        const db = new CortexDB("http://testserver");
        const result = await db.query.getTwoHopConnections(ENTITY_ID);
        expect(result).toEqual(["Entity A", "Entity B"]);
    });

    it("getTopRelations() uses limit param and returns relations array", async () => {
        mockFetch((req) => {
            expect(req.url).toBe("/api/query/graph/top-relations");
            expect(req.searchParams.get("limit")).toBe("5");
            return { status: 200, body: [SAMPLE_RELATION] };
        });

        const db = new CortexDB("http://testserver");
        const relations = await db.query.getTopRelations(5);
        expect(relations).toHaveLength(1);
        expect(relations[0].relationType).toBe("WORKS_FOR");
        expect(relations[0].weight).toBe(0.95);
    });

    it("getRelationsBySource() calls correct endpoint", async () => {
        mockFetch((req) => {
            expect(req.url).toBe(`/api/query/graph/relations/source/${SOURCE_ID}`);
            return { status: 200, body: [SAMPLE_RELATION] };
        });

        const db = new CortexDB("http://testserver");
        const relations = await db.query.getRelationsBySource(SOURCE_ID);
        expect(relations).toHaveLength(1);
    });

    it("getRelationsByTarget() calls correct endpoint", async () => {
        mockFetch((req) => {
            expect(req.url).toBe(`/api/query/graph/relations/target/${TARGET_ID}`);
            return { status: 200, body: [] };
        });

        const db = new CortexDB("http://testserver");
        const relations = await db.query.getRelationsByTarget(TARGET_ID);
        expect(relations).toHaveLength(0);
    });

    it("getRelationsByType() calls correct endpoint", async () => {
        mockFetch((req) => {
            expect(req.url).toBe("/api/query/graph/relations/type/WORKS_FOR");
            return { status: 200, body: [] };
        });

        const db = new CortexDB("http://testserver");
        const relations = await db.query.getRelationsByType("WORKS_FOR");
        expect(relations).toHaveLength(0);
    });
});

// ─── Hybrid search ───────────────────────────────────────────────────

describe("QueryAPI — Hybrid search", () => {
    it("hybridSearch() POSTs to /api/query/hybrid", async () => {
        let capturedBody: any;
        mockFetch((req) => {
            expect(req.url).toBe("/api/query/hybrid");
            expect(req.method).toBe("POST");
            capturedBody = req.body;
            return { status: 200, body: SAMPLE_QUERY_RESPONSE };
        });

        const db = new CortexDB("http://testserver");
        const resp = await db.query.hybridSearch("combined search", 3, 0.6);
        expect(resp.results).toHaveLength(1);
        expect(capturedBody.limit).toBe(3);
        expect(capturedBody.minRelevance).toBe(0.6);
    });
});
