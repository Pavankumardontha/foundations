package com.zenith.events;

import java.util.List;

// The one error shape. Every error from this API, whatever its cause, is one of these.
record ApiError(
        String errorId,
        int status,
        String code,
        String message,
        List<FieldError> fieldErrors) {

    record FieldError(String field, String message) {}
}
