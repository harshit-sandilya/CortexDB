package com.vectornode.cortexdb.exceptions;

/**
 * Base exception for all CortexDB SDK errors.
 */
public class CortexDBException extends RuntimeException {

    public CortexDBException(String message) {
        super(message);
    }

    public CortexDBException(String message, Throwable cause) {
        super(message, cause);
    }
}
