package com.vectornode.cortexdb.llm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.cortexdb.exceptions.LLMException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Native LLM provider — calls the Gemini / OpenAI APIs directly from the client
 * side
 * without routing through the CortexDB backend.
 *
 * <p>
 * Supports Gemini, OpenAI, Anthropic, Azure, and OpenRouter.
 *
 * <p>
 * Usage:
 * 
 * <pre>{@code
 * LLMProvider llm = new LLMProvider("GEMINI", "your-api-key",
 *         "gemini-2.0-flash", "gemini-embedding-001", null);
 *
 * List<Float> embedding = llm.getEmbedding("Hello world");
 * String response = llm.callLLM("Explain quantum computing");
 * }</pre>
 */
public class LLMProvider {

    private static final Logger logger = Logger.getLogger(LLMProvider.class.getName());

    private final String provider;
    private final String apiKey;
    private final String chatModel;
    private final String embedModel;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Initialize the LLM provider.
     *
     * @param provider   Provider name — "GEMINI", "OPENAI", "ANTHROPIC", "AZURE",
     *                   or "OPENROUTER".
     * @param apiKey     API key for authentication.
     * @param chatModel  Name of the chat model.
     * @param embedModel Name of the embedding model.
     * @param baseUrl    Custom base URL (optional).
     */
    public LLMProvider(String provider, String apiKey, String chatModel,
            String embedModel, String baseUrl) {
        this.provider = provider.toUpperCase();
        this.apiKey = apiKey;
        this.chatModel = chatModel;
        this.embedModel = embedModel;
        this.baseUrl = baseUrl;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        if (!this.provider.equals("GEMINI") && !this.provider.equals("OPENAI")
                && !this.provider.equals("ANTHROPIC") && !this.provider.equals("AZURE")
                && !this.provider.equals("OPENROUTER")) {
            throw new IllegalArgumentException("Unsupported provider: " + provider);
        }

        logger.info("LLMProvider initialized: provider=" + this.provider
                + ", chatModel=" + chatModel + ", embedModel=" + embedModel);
    }

    /**
     * Generate an embedding vector for the given text.
     *
     * @param text Input text to embed.
     * @return A list of floats representing the embedding vector.
     * @throws LLMException if embedding generation fails.
     */
    public List<Float> getEmbedding(String text) {
        logger.fine("Generating embedding for text (" + text.length() + " chars)");

        try {
            if (provider.equals("GEMINI")) {
                return getGeminiEmbedding(text);
            } else {
                return getOpenAIEmbedding(text);
            }
        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Embedding generation failed", e);
            throw new LLMException("Embedding generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Send a prompt to the LLM and return the response text.
     *
     * @param prompt The prompt to send.
     * @return The LLM's text response.
     * @throws LLMException if the call fails.
     */
    public String callLLM(String prompt) {
        logger.fine("Calling LLM with prompt (" + prompt.length() + " chars)");

        try {
            if (provider.equals("GEMINI")) {
                return callGemini(prompt);
            } else {
                return callOpenAI(prompt);
            }
        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "LLM call failed", e);
            throw new LLMException("LLM call failed: " + e.getMessage(), e);
        }
    }

    // ── Gemini implementation ────────────────────────────────────

    private List<Float> getGeminiEmbedding(String text) throws Exception {
        String model = embedModel != null ? embedModel : "gemini-embedding-001";
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":embedContent?key=" + apiKey;

        String body = objectMapper.writeValueAsString(
                new java.util.LinkedHashMap<>() {
                    {
                        put("model", "models/" + model);
                        put("content", new java.util.LinkedHashMap<>() {
                            {
                                put("parts", List.of(new java.util.LinkedHashMap<>() {
                                    {
                                        put("text", text);
                                    }
                                }));
                            }
                        });
                    }
                });

        String response = sendRequest(url, body);
        JsonNode root = objectMapper.readTree(response);
        JsonNode values = root.path("embedding").path("values");

        if (values.isMissingNode() || !values.isArray()) {
            throw new LLMException("Unexpected Gemini embedding response: " + response);
        }

        List<Float> embedding = new ArrayList<>();
        for (JsonNode val : values) {
            embedding.add((float) val.asDouble());
        }
        return embedding;
    }

    private String callGemini(String prompt) throws Exception {
        String model = chatModel != null ? chatModel : "gemini-2.0-flash";
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        String body = objectMapper.writeValueAsString(
                new java.util.LinkedHashMap<>() {
                    {
                        put("contents", List.of(new java.util.LinkedHashMap<>() {
                            {
                                put("parts", List.of(new java.util.LinkedHashMap<>() {
                                    {
                                        put("text", prompt);
                                    }
                                }));
                            }
                        }));
                    }
                });

        String response = sendRequest(url, body);
        JsonNode root = objectMapper.readTree(response);
        JsonNode text = root.path("candidates").path(0)
                .path("content").path("parts").path(0).path("text");

        if (text.isMissingNode()) {
            throw new LLMException("Unexpected Gemini chat response: " + response);
        }
        return text.asText();
    }

    // ── OpenAI / Azure implementation ────────────────────────────

    private List<Float> getOpenAIEmbedding(String text) throws Exception {
        String url = resolveOpenAIUrl("/v1/embeddings");

        String body = objectMapper.writeValueAsString(
                new java.util.LinkedHashMap<>() {
                    {
                        put("model", embedModel != null ? embedModel : "text-embedding-ada-002");
                        put("input", text);
                    }
                });

        String response = sendRequest(url, body, "Bearer " + apiKey);
        JsonNode root = objectMapper.readTree(response);
        JsonNode embeddingArr = root.path("data").path(0).path("embedding");

        if (embeddingArr.isMissingNode() || !embeddingArr.isArray()) {
            throw new LLMException("Unexpected OpenAI embedding response: " + response);
        }

        List<Float> embedding = new ArrayList<>();
        for (JsonNode val : embeddingArr) {
            embedding.add((float) val.asDouble());
        }
        return embedding;
    }

    private String callOpenAI(String prompt) throws Exception {
        String url = resolveOpenAIUrl("/v1/chat/completions");

        String body = objectMapper.writeValueAsString(
                new java.util.LinkedHashMap<>() {
                    {
                        put("model", chatModel != null ? chatModel : "gpt-4");
                        put("messages", List.of(new java.util.LinkedHashMap<>() {
                            {
                                put("role", "user");
                                put("content", prompt);
                            }
                        }));
                    }
                });

        String response = sendRequest(url, body, "Bearer " + apiKey);
        JsonNode root = objectMapper.readTree(response);
        JsonNode content = root.path("choices").path(0).path("message").path("content");

        if (content.isMissingNode()) {
            throw new LLMException("Unexpected OpenAI chat response: " + response);
        }
        return content.asText();
    }

    private String resolveOpenAIUrl(String path) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            return base + path;
        }
        String defaultBase = switch (provider) {
            case "ANTHROPIC" -> "https://api.anthropic.com";
            case "OPENROUTER" -> "https://openrouter.ai/api";
            default -> "https://api.openai.com";
        };
        return defaultBase + path;
    }

    // ── HTTP helper ──────────────────────────────────────────────

    private String sendRequest(String url, String body) throws Exception {
        return sendRequest(url, body, null);
    }

    private String sendRequest(String url, String body, String authHeader) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json");

        if (authHeader != null) {
            builder.header("Authorization", authHeader);
        }

        HttpResponse<String> response = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new LLMException("LLM API error (HTTP " + response.statusCode()
                    + "): " + response.body());
        }
        return response.body();
    }
}
