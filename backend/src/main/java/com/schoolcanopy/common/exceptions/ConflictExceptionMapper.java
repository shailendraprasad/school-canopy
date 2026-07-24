package com.schoolcanopy.common.exceptions;

import com.schoolcanopy.common.ApiResponse;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ConflictExceptionMapper implements ExceptionMapper<ConflictException> {

    @Override
    public Response toResponse(ConflictException ex) {
        return Response.status(Response.Status.CONFLICT)
                .entity(ApiResponse.error(ex.getField(), ex.getCode(), ex.getMessage()))
                .build();
    }
}
