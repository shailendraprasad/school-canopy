package com.schoolcanopy.common;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private T data;
    private PaginationMeta pagination;
    private List<ErrorDetail> errors;

    private ApiResponse() {}

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.data = data;
        return response;
    }

    public static <T> ApiResponse<T> success(T data, PaginationMeta pagination) {
        ApiResponse<T> response = new ApiResponse<>();
        response.data = data;
        response.pagination = pagination;
        return response;
    }

    public static <T> ApiResponse<T> error(List<ErrorDetail> errors) {
        ApiResponse<T> response = new ApiResponse<>();
        response.errors = errors;
        return response;
    }

    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.errors = List.of(new ErrorDetail(null, null, message));
        return response;
    }

    public static <T> ApiResponse<T> error(String field, String code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.errors = List.of(new ErrorDetail(field, code, message));
        return response;
    }

    public T getData() { return data; }
    public PaginationMeta getPagination() { return pagination; }
    public List<ErrorDetail> getErrors() { return errors; }
}
