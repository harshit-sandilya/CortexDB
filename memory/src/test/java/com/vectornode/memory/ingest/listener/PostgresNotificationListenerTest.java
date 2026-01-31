package com.vectornode.memory.ingest.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.ingest.service.IngestionWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.PGNotification;

import java.lang.reflect.Method;

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

    @Test
    @DisplayName("should dispatch KB_CREATED event to ingestion worker")
    void shouldDispatchKbCreatedEvent() throws Exception {
        // Create a mock notification
        PGNotification notification = mock(PGNotification.class);
        when(notification.getParameter()).thenReturn("{\"type\":\"KB_CREATED\",\"id\":42}");

        // Use reflection to test private handleNotification method
        PostgresNotificationListener listener = createListenerWithMockDataSource();
        Method handleMethod = PostgresNotificationListener.class.getDeclaredMethod("handleNotification",
                PGNotification.class);
        handleMethod.setAccessible(true);

        handleMethod.invoke(listener, notification);

        verify(ingestionWorker).handleKbCreated(42);
    }

    @Test
    @DisplayName("should dispatch CONTEXT_CREATED event to ingestion worker")
    void shouldDispatchContextCreatedEvent() throws Exception {
        PGNotification notification = mock(PGNotification.class);
        when(notification.getParameter()).thenReturn("{\"type\":\"CONTEXT_CREATED\",\"id\":100}");

        PostgresNotificationListener listener = createListenerWithMockDataSource();
        Method handleMethod = PostgresNotificationListener.class.getDeclaredMethod("handleNotification",
                PGNotification.class);
        handleMethod.setAccessible(true);

        handleMethod.invoke(listener, notification);

        verify(ingestionWorker).handleContextCreated(100);
    }

    @Test
    @DisplayName("should not throw on unknown event type")
    void shouldNotThrowOnUnknownEventType() throws Exception {
        PGNotification notification = mock(PGNotification.class);
        when(notification.getParameter()).thenReturn("{\"type\":\"UNKNOWN_EVENT\",\"id\":1}");

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
        // Use constructor reflection to create instance
        var constructor = PostgresNotificationListener.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);

        // The @RequiredArgsConstructor creates a constructor with DataSource and
        // IngestionWorker
        javax.sql.DataSource mockDataSource = mock(javax.sql.DataSource.class);
        return (PostgresNotificationListener) constructor.newInstance(mockDataSource, ingestionWorker);
    }
}
