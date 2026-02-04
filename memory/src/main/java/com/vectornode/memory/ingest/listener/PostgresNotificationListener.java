package com.vectornode.memory.ingest.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.ingest.service.IngestionWorker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Listens to PostgreSQL NOTIFY events on the 'rag_events' channel.
 * Fire-and-forget dispatch to IngestionWorker for async processing.
 * 
 * Expected notification payloads:
 * KB_CREATED: {"type": "KB_CREATED", "id": "uuid", "content": "text content"}
 * CONTEXT_CREATED: {"type": "CONTEXT_CREATED", "id": "uuid", "kb_id": "uuid",
 * "text_chunk": "chunk text"}
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PostgresNotificationListener {

    private final DataSource dataSource;
    private final IngestionWorker ingestionWorker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService listenerExecutor;
    private Connection listenerConnection;

    @PostConstruct
    public void startListening() {
        running.set(true);
        listenerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "pg-notify-listener");
            t.setDaemon(true);
            return t;
        });
        listenerExecutor.submit(this::listenLoop);
        log.info("PostgresNotificationListener started on channel 'rag_events'");
    }

    @PreDestroy
    public void stopListening() {
        running.set(false);
        if (listenerExecutor != null) {
            listenerExecutor.shutdownNow();
        }
        closeConnection();
        log.info("PostgresNotificationListener stopped");
    }

    private void listenLoop() {
        while (running.get()) {
            try {
                ensureConnection();
                PGConnection pgConn = listenerConnection.unwrap(PGConnection.class);

                // Poll for notifications (blocking for up to 500ms)
                PGNotification[] notifications = pgConn.getNotifications(500);

                if (notifications != null) {
                    for (PGNotification notification : notifications) {
                        handleNotification(notification);
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Error in notification listener loop, will retry: {}", e.getMessage());
                    closeConnection();
                    sleep(2000); // Wait before retry
                }
            }
        }
    }

    private void ensureConnection() throws Exception {
        if (listenerConnection == null || listenerConnection.isClosed()) {
            listenerConnection = dataSource.getConnection();
            listenerConnection.setAutoCommit(true);

            try (Statement stmt = listenerConnection.createStatement()) {
                stmt.execute("LISTEN rag_events");
            }
            log.info("LISTEN registered on 'rag_events' channel");
        }
    }

    private void closeConnection() {
        if (listenerConnection != null) {
            try {
                listenerConnection.close();
            } catch (Exception ignored) {
            }
            listenerConnection = null;
        }
    }

    /**
     * Handle notification - fire-and-forget dispatch to IngestionWorker.
     * Does NOT wait for processing to complete.
     */
    private void handleNotification(PGNotification notification) {
        try {
            String payload = notification.getParameter();
            log.debug("Received notification: {}", payload);

            JsonNode json = objectMapper.readTree(payload);
            String type = json.get("type").asText();

            switch (type) {
                case "KB_CREATED" -> handleKbCreated(json);
                case "CONTEXT_CREATED" -> handleContextCreated(json);
                default -> log.warn("Unknown notification type: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to process notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Fire-and-forget: Dispatch KB processing to worker.
     * Payload: {"type": "KB_CREATED", "id": "uuid", "content": "text"}
     */
    private void handleKbCreated(JsonNode json) {
        UUID kbId = UUID.fromString(json.get("id").asText());
        String content = json.get("content").asText();

        log.info("Dispatching KB_CREATED for id: {} (fire-and-forget)", kbId);

        // Fire-and-forget - don't wait for result
        ingestionWorker.processKnowledgeBase(kbId, content);
    }

    /**
     * Fire-and-forget: Dispatch Context processing to worker.
     * Payload: {"type": "CONTEXT_CREATED", "id": "uuid", "kb_id": "uuid",
     * "text_chunk": "text"}
     */
    private void handleContextCreated(JsonNode json) {
        UUID contextId = UUID.fromString(json.get("id").asText());
        UUID kbId = UUID.fromString(json.get("kb_id").asText());
        String textChunk = json.get("text_chunk").asText();

        log.info("Dispatching CONTEXT_CREATED for id: {} (fire-and-forget)", contextId);

        // Fire-and-forget - don't wait for result
        ingestionWorker.processContext(contextId, kbId, textChunk);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
