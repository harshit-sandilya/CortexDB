package com.vectornode.cortexdb.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vectornode.cortexdb.exceptions.ApiException;
import com.vectornode.cortexdb.exceptions.CortexDBException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Thin wrapper around {@link java.net.http.HttpClient} that handles
 * JSON serialization/deserialization via Jackson and shared configuration.
 */
public class HttpClientWrapper {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    /**
     * @param baseUrl        Base URL of the CortexDB server (e.g.
     *                       "http://localhost:8080").
     * @param timeoutSeconds Request timeout in seconds.
     */
    public HttpClientWrapper(String baseUrl, long timeoutSeconds) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /** Access the shared ObjectMapper (e.g. for manual deserialization). */
    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    // ── GET ──────────────────────────────────────────────────────

    /**
     * Execute a GET request and deserialize the response.
     */
    public <T> T get(String path, Class<T> responseType) {
        return get(path, null, responseType);
    }

    /**
     * Execute a GET request with query parameters and deserialize the response.
     */
    public <T> T get(String path, Map<String, String> params, Class<T> responseType) {
        String url = buildUrl(path, params);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .build();
        return execute(request, responseType);
    }

    /**
     * Execute a GET request and return the raw JSON string.
     */
    public String getRaw(String path) {
        return getRaw(path, null);
    }

    /**
     * Execute a GET request with query parameters and return the raw JSON string.
     */
    public String getRaw(String path, Map<String, String> params) {
        String url = buildUrl(path, params);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .build();
        return executeRaw(request);
    }

    /**
     * Execute a GET request. Returns the raw response body, or {@code null} if 404.
     */
    public String getRawOrNull(String path) {
        String url = buildUrl(path, null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return null;
            }
            checkStatus(response);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CortexDBException("Request failed: " + e.getMessage(), e);
        }
    }

    // ── POST ─────────────────────────────────────────────────────

    /**
     * Execute a POST request with a JSON body and deserialize the response.
     */
    public <T> T post(String path, Object body, Class<T> responseType) {
        return post(path, null, body, responseType);
    }

    /**
     * Execute a POST request with query parameters and a JSON body.
     */
    public <T> T post(String path, Map<String, String> params, Object body, Class<T> responseType) {
        String url = buildUrl(path, params);
        String json = serialize(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();
        return execute(request, responseType);
    }

    /**
     * Execute a POST request with a plain-text body.
     */
    public <T> T postPlainText(String path, Map<String, String> params,
            String textBody, Class<T> responseType) {
        String url = buildUrl(path, params);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(textBody))
                .header("Content-Type", "text/plain")
                .header("Accept", "application/json")
                .build();
        return execute(request, responseType);
    }

    /**
     * Execute a POST request with query parameters and no body.
     */
    public void postNoBody(String path, Map<String, String> params) {
        String url = buildUrl(path, params);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("Accept", "application/json")
                .build();
        executeVoid(request);
    }

    // ── DELETE ───────────────────────────────────────────────────

    /**
     * Execute a DELETE request.
     */
    public void delete(String path) {
        String url = buildUrl(path, null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .DELETE()
                .build();
        executeVoid(request);
    }

    // ── Internal helpers ─────────────────────────────────────────

    private <T> T execute(HttpRequest request, Class<T> responseType) {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            checkStatus(response);
            return deserialize(response.body(), responseType);
        } catch (ApiException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CortexDBException("Request failed: " + e.getMessage(), e);
        }
    }

    private String executeRaw(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            checkStatus(response);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CortexDBException("Request failed: " + e.getMessage(), e);
        }
    }

    private void executeVoid(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            checkStatus(response);
        } catch (ApiException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CortexDBException("Request failed: " + e.getMessage(), e);
        }
    }

    private void checkStatus(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 400) {
            throw new ApiException(status, "Server returned error", response.body());
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new CortexDBException("JSON serialization failed", e);
        }
    }

    private <T> T deserialize(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new CortexDBException("JSON deserialization failed", e);
        }
    }

    private String buildUrl(String path, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(baseUrl).append(path);
        if (params != null && !params.isEmpty()) {
            sb.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first)
                    sb.append('&');
                sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                first = false;
            }
        }
        return sb.toString();
    }
}
