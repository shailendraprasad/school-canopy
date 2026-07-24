package com.schoolcanopy.common;

import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.DefaultValue;

public class PaginationParams {

    public static final int DEFAULT_OFFSET = 0;
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    @QueryParam("offset")
    @DefaultValue("0")
    private int offset;

    @QueryParam("limit")
    @DefaultValue("20")
    private int limit;

    public int getOffset() {
        return Math.max(0, offset);
    }

    public int getLimit() {
        return limit;
    }

    public boolean isValid() {
        return limit >= 1 && limit <= MAX_LIMIT && offset >= 0;
    }

    public String getValidationError() {
        if (limit < 1 || limit > MAX_LIMIT) {
            return "Limit must be between 1 and " + MAX_LIMIT;
        }
        if (offset < 0) {
            return "Offset must be non-negative";
        }
        return null;
    }
}
