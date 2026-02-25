/**
 * Thin HTTP wrapper around the native fetch API.
 *
 * Mirrors the role of httpx in the Python SDK — handles base URL
 * resolution, JSON serialisation, timeout, and error handling so
 * the API classes stay clean.
 */

export class HttpClient {
    private readonly baseUrl: string;
    private readonly timeout: number;

    /**
     * @param baseUrl  Base URL of the CortexDB server.
     * @param timeout  Request timeout in milliseconds.
     */
    constructor(baseUrl: string, timeout: number = 30_000) {
        // Strip trailing slash so path concatenation is predictable
        this.baseUrl = baseUrl.replace(/\/+$/, "");
        this.timeout = timeout;
    }

    // ── Convenience methods ──────────────────────────────────────────

    async get<T>(path: string, params?: Record<string, any>): Promise<T> {
        return this.request<T>("GET", path, undefined, params);
    }

    async post<T>(
        path: string,
        body?: any,
        params?: Record<string, any>,
        headers?: Record<string, string>,
    ): Promise<T> {
        return this.request<T>("POST", path, body, params, headers);
    }

    async delete<T>(path: string, params?: Record<string, any>): Promise<T> {
        return this.request<T>("DELETE", path, undefined, params);
    }

    // ── Core request method ──────────────────────────────────────────

    async request<T>(
        method: string,
        path: string,
        body?: any,
        params?: Record<string, any>,
        extraHeaders?: Record<string, string>,
    ): Promise<T> {
        const url = new URL(this.baseUrl + path);

        if (params) {
            for (const [key, value] of Object.entries(params)) {
                if (value !== undefined && value !== null) {
                    url.searchParams.append(key, String(value));
                }
            }
        }

        const headers: Record<string, string> = {
            Accept: "application/json",
            ...extraHeaders,
        };

        let requestBody: string | undefined;
        if (body !== undefined) {
            headers["Content-Type"] = "application/json";
            requestBody = JSON.stringify(body);
        }

        const response = await fetch(url.toString(), {
            method,
            headers,
            body: requestBody,
            signal: AbortSignal.timeout(this.timeout),
        });

        // 404 → return null (mirrors the Python SDK's pattern)
        if (response.status === 404) {
            return null as T;
        }

        if (!response.ok) {
            const errorBody = await response.text().catch(() => "");
            throw new Error(
                `CortexDB HTTP Error: ${response.status} ${response.statusText}` +
                (errorBody ? ` — ${errorBody}` : ""),
            );
        }

        // 204 No Content
        if (response.status === 204) {
            return undefined as T;
        }

        // Some void endpoints respond 200 with no body (e.g. DELETE, merge).
        // Wrap json() in a try/catch so empty bodies don't throw.
        try {
            return await response.json() as T;
        } catch {
            return undefined as T;
        }
    }
}
