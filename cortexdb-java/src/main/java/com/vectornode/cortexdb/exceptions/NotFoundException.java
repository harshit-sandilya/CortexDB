package com.vectornode.cortexdb.exceptions;

/**
 * Raised when a requested resource is not found (HTTP 404).
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(404, message, null);
    }
}
