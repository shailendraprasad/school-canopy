package com.schoolcanopy.common.exceptions;

import java.util.List;

import com.schoolcanopy.common.ErrorDetail;

public class ValidationException extends RuntimeException {

    private final List<ErrorDetail> fieldErrors;

    public ValidationException(String field, String code, String message) {
        super(message);
        this.fieldErrors = List.of(new ErrorDetail(field, code, message));
    }

    public ValidationException(List<ErrorDetail> fieldErrors) {
        super("Validation failed");
        this.fieldErrors = fieldErrors;
    }

    public List<ErrorDetail> getFieldErrors() {
        return fieldErrors;
    }
}
