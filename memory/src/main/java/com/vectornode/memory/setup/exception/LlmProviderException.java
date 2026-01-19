package com.vectornode.memory.setup.exception;

//initialization errors, connection issues to the AI provider, execution failures during embedding/chat calls.
public class LlmProviderException extends RuntimeException {
    public LlmProviderException(String message) {
        super(message);
    }

    public LlmProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
