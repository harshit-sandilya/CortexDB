package com.vectornode.cortexdb.exceptions;

/**
 * Raised when an LLM operation fails.
 */
public class LLMException extends CortexDBException {

    public LLMException(String message) {
        super(message);
    }

    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
