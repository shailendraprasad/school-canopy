package com.schoolcanopy.common.exceptions;

public class ConflictException extends RuntimeException {

    private final String field;
    private final String code;

    public ConflictException(String field, String message) {
        super(message);
        this.field = field;
        this.code = "ALREADY_EXISTS";
    }

    public ConflictException(String field, String code, String message) {
        super(message);
        this.field = field;
        this.code = code;
    }

    public String getField() { return field; }
    public String getCode() { return code; }
}
