package com.vectornode.cortexdb.exceptions;

/**
 * Raised when the CortexDB server returns an HTTP error response.
 */
public class ApiException extends CortexDBException {

    private final int statusCode;
    private final String responseBody;

    public ApiException(int statusCode, String message, String responseBody) {
        super("HTTP " + statusCode + ": " + message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
