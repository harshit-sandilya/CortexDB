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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Listens to PostgreSQL NOTIFY events on the 'rag_events' channel.
 * Dispatches events to the IngestionWorker for async processing.
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

    private void handleNotification(PGNotification notification) {
        try {
            String payload = notification.getParameter();
            log.debug("Received notification: {}", payload);

            JsonNode json = objectMapper.readTree(payload);
            String type = json.get("type").asText();
            Integer id = json.get("id").asInt();

            switch (type) {
                case "KB_CREATED":
                    ingestionWorker.handleKbCreated(id);
                    break;
                case "CONTEXT_CREATED":
                    ingestionWorker.handleContextCreated(id);
                    break;
                default:
                    log.warn("Unknown notification type: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to process notification: {}", e.getMessage(), e);
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
