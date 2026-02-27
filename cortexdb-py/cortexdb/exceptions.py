"""Custom exceptions for the CortexDB Python SDK."""

from __future__ import annotations


class CortexDBError(Exception):
    """Base exception for all CortexDB errors."""

    pass


class ConnectionError(CortexDBError):
    """Raised when the SDK cannot connect to the CortexDB server."""

    pass


class APIError(CortexDBError):
    """Raised when the CortexDB server returns an error response."""

    def __init__(self, status_code: int, message: str, detail: str | None = None):
        self.status_code = status_code
        self.detail = detail
        super().__init__(f"HTTP {status_code}: {message}")


class NotFoundError(APIError):
    """Raised when a requested resource is not found (404)."""

    def __init__(self, message: str = "Resource not found"):
        super().__init__(status_code=404, message=message)


class ValidationError(APIError):
    """Raised when request validation fails (400)."""

    def __init__(self, message: str = "Validation failed", detail: str | None = None):
        super().__init__(status_code=400, message=message, detail=detail)


class LLMError(CortexDBError):
    """Raised when an LLM operation fails."""

    pass


class LLMNotInitializedError(LLMError):
    """Raised when LLM provider is used before initialization."""

    def __init__(self):
        super().__init__("LLM provider not initialized. Call initialize() first.")
