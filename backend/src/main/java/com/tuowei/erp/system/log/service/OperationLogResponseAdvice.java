package com.tuowei.erp.system.log.service;

import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.system.log.annotation.OperationLog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.MethodParameter;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
@ConditionalOnBean(SystemLogService.class)
public class OperationLogResponseAdvice implements ResponseBodyAdvice<Object> {

    private final SystemLogService systemLogService;
    private final CurrentUserContext currentUserContext;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    public OperationLogResponseAdvice(SystemLogService systemLogService, CurrentUserContext currentUserContext) {
        this.systemLogService = systemLogService;
        this.currentUserContext = currentUserContext;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        HttpServletRequest servletRequest = resolveServletRequest(request);
        OperationLog operationLog = resolveOperationLog(returnType, servletRequest);
        if (operationLog != null) {
            systemLogService.recordOperation(
                    resolveCurrentUser(),
                    operationLog.module(),
                    operationLog.operation(),
                    resolveExpression(operationLog.bizNo(), body),
                    resolveResult(response),
                    operationLog.message(),
                    servletRequest
            );
        }
        return body;
    }

    private OperationLog resolveOperationLog(MethodParameter returnType, HttpServletRequest request) {
        OperationLog operationLog = returnType.getMethodAnnotation(OperationLog.class);
        if (operationLog != null || request == null) {
            return operationLog;
        }
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod handlerMethod) {
            return handlerMethod.getMethodAnnotation(OperationLog.class);
        }
        return null;
    }

    private ErpPrincipal resolveCurrentUser() {
        try {
            return currentUserContext.requirePrincipal();
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    private String resolveExpression(String expression, Object result) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        EvaluationContext context = new StandardEvaluationContext();
        context.setVariable("result", result);
        try {
            Object value = expressionParser.parseExpression(expression).getValue(context);
            return value == null ? null : value.toString();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String resolveResult(ServerHttpResponse response) {
        if (response instanceof ServletServerHttpResponse servletResponse
                && servletResponse.getServletResponse().getStatus() >= 400) {
            return "FAILURE";
        }
        return "SUCCESS";
    }

    private HttpServletRequest resolveServletRequest(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest();
        }
        return null;
    }
}
