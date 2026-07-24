package com.schoolcanopy.rbac;

import java.util.Arrays;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import com.schoolcanopy.common.exceptions.ForbiddenException;

@Interceptor
@RequiresRole({})
public class RbacInterceptor {

    @Inject
    RequestContext requestContext;

    @AroundInvoke
    public Object checkRole(InvocationContext ctx) throws Exception {
        RequiresRole annotation = ctx.getMethod().getAnnotation(RequiresRole.class);
        if (annotation == null) {
            annotation = ctx.getTarget().getClass().getAnnotation(RequiresRole.class);
        }
        if (annotation != null && annotation.value().length > 0) {
            String currentRole = requestContext.getCurrentUserRole();
            Set<String> allowedRoles = Set.of(annotation.value());
            if (!allowedRoles.contains(currentRole)) {
                throw new ForbiddenException();
            }
        }
        return ctx.proceed();
    }
}
