package com.vectornode.memory.ingest.listener;

import com.vectornode.memory.ingest.service.IngestionWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.PGNotification;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Unit tests for PostgresNotificationListener.
 * Tests the notification handling logic using reflection to access private
 * method.
 */
@ExtendWith(MockitoExtension.class)
class PostgresNotificationListenerTest {

    @Mock
    private IngestionWorker ingestionWorker;

    private static final UUID TEST_KB_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID TEST_CONTEXT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    @Test
    @DisplayName("should dispatch KB_CREATED event to ingestion worker")
    void shouldDispatchKbCreatedEvent() throws Exception {
        String payload = String.format(
                "{\"type\":\"KB_CREATED\",\"id\":\"%s\",\"content\":\"Test content\"}",
                TEST_KB_ID);

        PGNotification notification = mock(PGNotification.class);
        when(notification.getParameter()).thenReturn(payload);

        PostgresNotificationListener listener = createListenerWithMockDataSource();
        Method handleMethod = PostgresNotificationListener.class.getDeclaredMethod("handleNotification",
                PGNotification.class);
        handleMethod.setAccessible(true);

        handleMethod.invoke(listener, notification);

        verify(ingestionWorker).processKnowledgeBase(TEST_KB_ID, "Test content");
    }

    @Test
    @DisplayName("should dispatch CONTEXT_CREATED event to ingestion worker")
    void shouldDispatchContextCreatedEvent() throws Exception {
        String payload = String.format(
                "{\"type\":\"CONTEXT_CREATED\",\"id\":\"%s\",\"kb_id\":\"%s\",\"text_chunk\":\"Test chunk\"}",
                TEST_CONTEXT_ID, TEST_KB_ID);

        PGNotification notification = mock(PGNotification.class);
        when(notification.getParameter()).thenReturn(payload);

        PostgresNotificationListener listener = createListenerWithMockDataSource();
        Method handleMethod = PostgresNotificationListener.class.getDeclaredMethod("handleNotification",
                PGNotification.class);
        handleMethod.setAccessible(true);

        handleMethod.invoke(listener, notification);

        verify(ingestionWorker).processContext(TEST_CONTEXT_ID, TEST_KB_ID, "Test chunk");
    }

    @Test
    @DisplayName("should not throw on unknown event type")
    void shouldNotThrowOnUnknownEventType() throws Exception {
        PGNotification notification = mock(PGNotification.class);
        when(notification.getParameter()).thenReturn("{\"type\":\"UNKNOWN_EVENT\",\"id\":\"" + TEST_KB_ID + "\"}");

        PostgresNotificationListener listener = createListenerWithMockDataSource();
        Method handleMethod = PostgresNotificationListener.class.getDeclaredMethod("handleNotification",
                PGNotification.class);
        handleMethod.setAccessible(true);

        // Should not throw
        handleMethod.invoke(listener, notification);

        verifyNoInteractions(ingestionWorker);
    }

    @Test
    @DisplayName("should not throw on malformed JSON payload")
    void shouldNotThrowOnMalformedJson() throws Exception {
        PGNotification notification = mock(PGNotification.class);
        when(notification.getParameter()).thenReturn("not valid json");

        PostgresNotificationListener listener = createListenerWithMockDataSource();
        Method handleMethod = PostgresNotificationListener.class.getDeclaredMethod("handleNotification",
                PGNotification.class);
        handleMethod.setAccessible(true);

        // Should not throw, just log error
        handleMethod.invoke(listener, notification);

        verifyNoInteractions(ingestionWorker);
    }

    /**
     * Creates a listener instance using reflection with mock data source.
     */
    private PostgresNotificationListener createListenerWithMockDataSource() throws Exception {
        var constructor = PostgresNotificationListener.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);

        javax.sql.DataSource mockDataSource = mock(javax.sql.DataSource.class);
        return (PostgresNotificationListener) constructor.newInstance(mockDataSource, ingestionWorker);
    }
}
