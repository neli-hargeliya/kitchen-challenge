package com.example.kitchen.model;

import java.time.Instant;

// Standard error payload returned by the API
public record ErrorResponse(
        Instant timestamp, // server time (UTC) when the error was produced
        int status,        // HTTP status code (e.g., 400, 404, 500)
        String error,      // short status text (e.g., "Bad Request")
        String message,    // human-readable error message
        String path        // request path (e.g., "/api/orders/123/pickup")
) {
}
