/**
 * Shared test utilities — mock fetch helpers and sample fixture data.
 *
 * Each unit test calls `mockFetch(handler)` to intercept fetch calls,
 * then `restoreFetch()` in afterEach to tear down the mock.
 *
 * This mirrors the role of `respx` in the Python test suite.
 */

import { vi } from "vitest";

// ─── Types ───────────────────────────────────────────────────────────

export interface MockRequest {
    url: string;
    method: string;
    body: any | null;
    headers: Record<string, string>;
    searchParams: URLSearchParams;
}

// ─── Mock fetch ──────────────────────────────────────────────────────

type FetchHandler = (req: MockRequest) => { status: number; body?: any };

const _originalFetch = globalThis.fetch;

/**
 * Replace globalThis.fetch with a mock handler.
 * Call `restoreFetch()` after each test.
 */
export function mockFetch(handler: FetchHandler): void {
    globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(input.toString());

        let parsedBody: any = null;
        if (init?.body && typeof init.body === "string") {
            try {
                parsedBody = JSON.parse(init.body);
            } catch {
                parsedBody = init.body; // plain text body
            }
        }

        const req: MockRequest = {
            url: url.pathname,
            method: (init?.method ?? "GET").toUpperCase(),
            body: parsedBody,
            headers: Object.fromEntries(new Headers(init?.headers ?? {}).entries()),
            searchParams: url.searchParams,
        };

        const { status, body } = handler(req);

        return new Response(
            body !== undefined ? JSON.stringify(body) : null,
            {
                status,
                headers: { "Content-Type": "application/json" },
            },
        );
    }) as typeof fetch;
}

/** Restore the original globalThis.fetch. */
export function restoreFetch(): void {
    globalThis.fetch = _originalFetch;
}

// ─── Fixture data ────────────────────────────────────────────────────

export const SAMPLE_QUERY_RESPONSE = {
    query: "test query",
    results: [
        {
            id: "550e8400-e29b-41d4-a716-446655440000",
            content: "Test content",
            score: 0.85,
            type: "CHUNK",
            metadata: { chunkIndex: 0 },
        },
    ],
    processingTimeMs: 25,
};

export const EMPTY_QUERY_RESPONSE = {
    query: "empty",
    results: [],
    processingTimeMs: 5,
};

export const SAMPLE_ENTITY = {
    id: "550e8400-e29b-41d4-a716-446655440000",
    name: "Google",
    type: "ORGANIZATION",
    description: "Tech company",
};

export const SAMPLE_RELATION = {
    id: "550e8400-e29b-41d4-a716-446655440000",
    sourceEntityId: "660e8400-e29b-41d4-a716-446655440000",
    targetEntityId: "770e8400-e29b-41d4-a716-446655440000",
    relationType: "WORKS_FOR",
    weight: 0.95,
};

export const KB_ID = "550e8400-e29b-41d4-a716-446655440000";
export const ENTITY_ID = "550e8400-e29b-41d4-a716-446655440000";
export const SOURCE_ID = "660e8400-e29b-41d4-a716-446655440000";
export const TARGET_ID = "770e8400-e29b-41d4-a716-446655440000";
