package com.vectornode.cortexdb.examples;

import com.vectornode.cortexdb.CortexDBClient;
import com.vectornode.cortexdb.exceptions.CortexDBException;
import com.vectornode.cortexdb.llm.LLMProvider;
import com.vectornode.cortexdb.models.ConverserRole;
import com.vectornode.cortexdb.models.LLMApiProvider;
import com.vectornode.cortexdb.models.QueryResponse;
import com.vectornode.cortexdb.models.SearchResult;
import com.vectornode.cortexdb.models.SetupResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.SequencedSet;

/**
 * Example application: Medical Chatbot using the CortexDB Java SDK.
 *
 * <p>
 * Proves that cortexdb-java can talk to the Java backend in a real scenario,
 * outside of unit tests — a 1:1 port of cortexdb-py/main.py.
 *
 * <p>
 * Usage (from cortexdb-java/):
 * 
 * <pre>
 *   .\mvnw.cmd exec:java
 * </pre>
 *
 * <p>
 * Configuration is loaded from {@code .env} file automatically
 * (same as Python's dotenv and Node's dotenv/config).
 */
public class MedicalChatbot {

    // ── .env file loader (mirrors Python/Node dotenv) ────────────────────────

    private static final Map<String, String> dotenv = loadDotenv();

    private static Map<String, String> loadDotenv() {
        Map<String, String> map = new HashMap<>();
        Path envFile = Path.of(".env");
        if (!Files.exists(envFile))
            return map;
        try {
            for (String line : Files.readAllLines(envFile)) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                int eq = line.indexOf('=');
                if (eq > 0) {
                    map.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: could not read .env file: " + e.getMessage());
        }
        return map;
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    private static final String API_URL = env("CORTEXDB_URL", "http://localhost:8080");
    private static final String PROVIDER = "GEMINI";
    private static final String API_KEY = env("GEMINI_API_KEY", null);
    private static final String CHAT_MODEL = env("GEMINI_CHAT_MODEL", "gemini-2.0-flash");
    private static final String EMBED_MODEL = env("GEMINI_EMBED_MODEL", "gemini-embedding-001");

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        if (API_KEY == null || API_KEY.isBlank()) {
            System.err.println("GEMINI_API_KEY environment variable not set.");
            System.err.println("Set it before running:  set GEMINI_API_KEY=your-key");
            System.exit(1);
        }

        System.out.println("Starting Medical Chatbot (CortexDB Java Demo)...");

        // 1. Initialise client
        CortexDBClient db;
        try {
            db = new CortexDBClient(API_URL);
            System.out.println("Connected to CortexDB at " + API_URL);
        } catch (Exception e) {
            System.err.println("Failed to connect: " + e.getMessage());
            return;
        }

        // 2. Configure LLM
        printStep("Configuring LLM Provider...");
        try {
            SetupResponse resp = db.setup().configure(
                    LLMApiProvider.GEMINI, API_KEY, CHAT_MODEL, EMBED_MODEL);
            if (resp.isSuccess()) {
                System.out.println("LLM Configured: " + resp.getConfiguredProvider());
            } else {
                System.out.println("Setup warning: " + resp.getMessage());
            }
        } catch (CortexDBException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("Connection refused") || msg.contains("connect")) {
                System.err.println("Could not connect to CortexDB server. Is it running?");
                System.err.println("   Run: docker compose up --build backend");
                return;
            }
            System.err.println("Setup failed: " + msg);
            return;
        }

        // 3. Ingest medical records
        printStep("Ingesting Medical Records...");
        final String patientId = "patient-101";
        List<String> medicalNotes = List.of(
                "Patient John Doe (DOB: 1980-05-12) presented with severe headache and light sensitivity.",
                "Examination reveals elevated blood pressure (150/95) and mild fever (100.4°F).",
                "Patient has history of migraines but reports this feels different, describing it as 'thundering'.",
                "Prescribed lisinopril 10mg daily for hypertension and recommended CT scan to rule out neurological issues.",
                "Patient is allergic to penicillin.");

        for (int i = 0; i < medicalNotes.size(); i++) {
            System.out.printf("   Ingesting note %d/%d... ", i + 1, medicalNotes.size());
            System.out.flush();
            try {
                db.ingest().document(
                        patientId,
                        ConverserRole.SYSTEM,
                        medicalNotes.get(i),
                        Map.of("type", "medical_record", "priority", "high"));
                System.out.println("Done.");
            } catch (CortexDBException e) {
                System.out.println("Failed: " + e.getMessage());
            }
        }

        // Allow time for async embedding generation + entity extraction
        System.out.print("   Waiting for processing... ");
        System.out.flush();
        Thread.sleep(5_000);
        System.out.println("Ready.");

        // 4. Initialise LLM provider (reused across all queries)
        LLMProvider llm = new LLMProvider(PROVIDER, API_KEY, CHAT_MODEL, EMBED_MODEL, null);

        // 5. Interactive chat loop
        printStep("Chat Session Started (Type 'exit' to quit)");
        System.out.println("You are now chatting about Patient " + patientId + "'s records.");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("\nUser: ");
                System.out.flush();

                if (!scanner.hasNextLine()) {
                    System.out.println("\nGoodbye!");
                    break;
                }

                String query = scanner.nextLine().strip();
                if (query.isEmpty())
                    continue;
                if (query.equalsIgnoreCase("exit") || query.equalsIgnoreCase("quit")) {
                    System.out.println("Goodbye!");
                    break;
                }

                try {
                    // A. Retrieve relevant context chunks
                    QueryResponse searchResponse = db.query().searchContexts(query, 10, 0.3, null);

                    // B. Search knowledge-graph entities
                    QueryResponse entityResponse = db.query().searchEntities(query, 5, 0.3);

                    // C. De-duplicate contexts (same note may appear from re-runs)
                    SequencedSet<String> seenContents = new LinkedHashSet<>();
                    List<SearchResult> uniqueResults = searchResponse.getResults().stream()
                            .filter(r -> seenContents.add(r.getContent()))
                            .toList();

                    // D. Display retrieval results
                    System.out.println("   Found " + uniqueResults.size() + " unique relevant notes.");
                    for (SearchResult res : uniqueResults) {
                        String preview = res.getContent().length() > 60
                                ? res.getContent().substring(0, 60) + "..."
                                : res.getContent();
                        System.out.printf("   - [Context] %s (Score: %.2f)%n", preview, res.getScore());
                    }
                    for (SearchResult res : entityResponse.getResults()) {
                        if (res.getContent() != null && !res.getContent().isBlank()) {
                            System.out.println("   - [Entity] " + res.getContent() + " (" + res.getType() + ")");
                        }
                    }

                    // E. Build prompt
                    StringBuilder contextStr = new StringBuilder();
                    for (SearchResult r : uniqueResults)
                        contextStr.append("- ").append(r.getContent()).append("\n");

                    StringBuilder entityStr = new StringBuilder();
                    for (SearchResult r : entityResponse.getResults()) {
                        if (r.getContent() != null && !r.getContent().isBlank()) {
                            entityStr.append("- ").append(r.getContent()).append(" (").append(r.getType())
                                    .append(")\n");
                        }
                    }

                    String prompt = "You are a helpful medical assistant. Answer the user's question based ONLY on the following information.\n\n"
                            + "## Patient Notes:\n" + contextStr + "\n";
                    if (!entityStr.isEmpty()) {
                        prompt += "## Extracted Entities:\n" + entityStr + "\n";
                    }
                    prompt += "Question: " + query + "\nAnswer:";

                    // F. Generate answer via Gemini
                    System.out.print("\n  Generating answer... ");
                    System.out.flush();
                    try {
                        String answer = llm.callLLM(prompt);
                        System.out.println("Done.");
                        System.out.println("\nBot: " + answer + "\n");
                    } catch (Exception e) {
                        System.out.println("Failed: " + e.getMessage());
                    }

                } catch (CortexDBException e) {
                    System.err.println("Error: " + e.getMessage());
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void printStep(String msg) {
        System.out.println("\n➤ " + msg);
    }

    private static String env(String key, String defaultValue) {
        // Check .env file first, then OS environment, then default
        String val = dotenv.get(key);
        if (val == null || val.isBlank()) {
            val = System.getenv(key);
        }
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
