package com.schoolcanopy.common;

public class PaginationMeta {

    private long totalCount;
    private int offset;
    private int limit;
    private boolean hasMore;

    public PaginationMeta() {}

    public PaginationMeta(long totalCount, int offset, int limit) {
        this.totalCount = totalCount;
        this.offset = offset;
        this.limit = limit;
        this.hasMore = totalCount > (long) offset + limit;
    }

    public long getTotalCount() { return totalCount; }
    public int getOffset() { return offset; }
    public int getLimit() { return limit; }
    public boolean isHasMore() { return hasMore; }
}
