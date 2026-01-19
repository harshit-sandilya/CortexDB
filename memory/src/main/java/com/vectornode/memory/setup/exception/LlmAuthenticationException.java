package com.vectornode.memory.setup.exception;

//Handles authentication specific issues, such as a missing or invalid API key
public class LlmAuthenticationException extends LlmProviderException {
    public LlmAuthenticationException(String message) {
        super(message);
    }
}
