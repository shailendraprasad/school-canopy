package com.schoolcanopy.common.exceptions;

import com.schoolcanopy.common.ApiResponse;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

    @Override
    public Response toResponse(ForbiddenException ex) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(ApiResponse.error("Access denied"))
                .build();
    }
}
