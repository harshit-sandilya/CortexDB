package com.aditya.hello.config;

import org.springframework.context.ApplicationEvent;

public class PostgresNotificationEvent extends ApplicationEvent {
    private final String channel;
    private final String payload;

    public PostgresNotificationEvent(Object source, String channel, String payload) {
        super(source);
        this.channel = channel;
        this.payload = payload;
    }

    public String getChannel() {
        return channel;
    }

    public String getPayload() {
        return payload;
    }
}
