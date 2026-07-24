package com.schoolcanopy.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorDetail {

    private String field;
    private String code;
    private String message;

    public ErrorDetail() {}

    public ErrorDetail(String field, String code, String message) {
        this.field = field;
        this.code = code;
        this.message = message;
    }

    public String getField() { return field; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
}
