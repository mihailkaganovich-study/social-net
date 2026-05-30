package ru.otus.study.web;

import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RequestIdPropagation {

    public static String current() {
        String requestId = RequestIdContext.get();
        if (isBlank(requestId)) {
            requestId = MDC.get(RequestIdConstants.MDC_KEY);
        }
        if (isBlank(requestId)) {
            var attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                requestId = servletAttributes.getRequest().getHeader(RequestIdConstants.HEADER);
            }
        }
        return isBlank(requestId) ? null : requestId;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private RequestIdPropagation() {
    }
}
