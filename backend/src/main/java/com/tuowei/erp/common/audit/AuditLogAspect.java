package com.tuowei.erp.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;
import java.util.Set;

/**
 * 操作审计切面
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");

    /**
     * 敏感字段名关键字（小写匹配，子串命中即脱敏），避免明文密码/令牌进入审计日志。
     */
    private static final Set<String> SENSITIVE_FIELD_KEYWORDS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "credential", "privatekey");
    private static final String MASKED = "***";

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ObjectMapper objectMapper;
    private final CurrentUserContext currentUserContext;
    private final ClientIpResolver clientIpResolver;

    public AuditLogAspect(ObjectMapper objectMapper,
                         CurrentUserContext currentUserContext,
                         ClientIpResolver clientIpResolver) {
        this.objectMapper = objectMapper;
        this.currentUserContext = currentUserContext;
        this.clientIpResolver = clientIpResolver;
    }

    @Around("@annotation(auditLog)")
    public Object audit(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable exception = null;

        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable e) {
            exception = e;
            throw e;
        } finally {
            try {
                recordAuditLog(pjp, auditLog, result, exception, startTime);
            } catch (Exception e) {
                // 审计日志记录失败不应影响业务
                auditLogger.error("记录审计日志失败", e);
            }
        }
    }

    private void recordAuditLog(ProceedingJoinPoint pjp, AuditLog auditLog,
                                Object result, Throwable exception, long startTime) {
        long duration = System.currentTimeMillis() - startTime;

        // 获取用户信息
        String username = "anonymous";
        Long userId = null;
        try {
            ErpPrincipal principal = currentUserContext.requirePrincipal();
            username = principal.username();
            userId = principal.userId();
        } catch (Exception ignored) {
        }

        // 获取IP地址
        String ip = getClientIp();

        // 获取Request ID
        String requestId = MDC.get("requestId");

        // 解析描述（SpEL表达式）
        String description = resolveDescription(auditLog.description(), pjp);

        // 构建审计日志
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("审计日志 | ");
        logMessage.append("模块=").append(auditLog.module()).append(" | ");
        logMessage.append("操作=").append(auditLog.operation().getLabel()).append(" | ");
        logMessage.append("用户=").append(username);
        if (userId != null) {
            logMessage.append("(").append(userId).append(")");
        }
        logMessage.append(" | ");
        logMessage.append("IP=").append(ip).append(" | ");
        logMessage.append("耗时=").append(duration).append("ms | ");
        logMessage.append("请求ID=").append(requestId != null ? requestId : "-").append(" | ");

        if (description != null && !description.isEmpty()) {
            logMessage.append("描述=").append(description).append(" | ");
        }

        // 记录参数
        if (auditLog.logParams() && pjp.getArgs().length > 0) {
            try {
                JsonNode argsNode = objectMapper.valueToTree(pjp.getArgs());
                maskSensitive(argsNode);
                String params = objectMapper.writeValueAsString(argsNode);
                logMessage.append("参数=").append(truncate(params, 500)).append(" | ");
            } catch (Exception ignored) {
            }
        }

        // 记录返回值
        if (auditLog.logResult() && result != null) {
            try {
                String resultStr = objectMapper.writeValueAsString(result);
                logMessage.append("返回值=").append(truncate(resultStr, 200)).append(" | ");
            } catch (Exception ignored) {
            }
        }

        // 记录异常
        if (exception != null) {
            logMessage.append("状态=失败 | ");
            logMessage.append("异常=").append(exception.getClass().getSimpleName());
            logMessage.append(": ").append(exception.getMessage());
        } else {
            logMessage.append("状态=成功");
        }

        // 输出审计日志
        auditLogger.info(logMessage.toString());
    }

    private String resolveDescription(String descriptionTemplate, ProceedingJoinPoint pjp) {
        if (descriptionTemplate == null || descriptionTemplate.isEmpty()) {
            return "";
        }

        try {
            StandardEvaluationContext context = new StandardEvaluationContext();

            // 设置方法参数到上下文
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            String[] paramNames = signature.getParameterNames();
            Object[] args = pjp.getArgs();

            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }

            return parser.parseExpression(descriptionTemplate).getValue(context, String.class);
        } catch (Exception e) {
            return descriptionTemplate;
        }
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                return clientIpResolver.resolve(request);
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }

    /**
     * 递归遍历 JSON 树，将敏感字段（密码/令牌等）的值替换为掩码，避免明文落入审计日志。
     */
    private void maskSensitive(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node instanceof ObjectNode objectNode) {
            objectNode.fieldNames().forEachRemaining(field -> {
                if (isSensitiveField(field)) {
                    objectNode.put(field, MASKED);
                } else {
                    maskSensitive(objectNode.get(field));
                }
            });
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::maskSensitive);
        }
    }

    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase(Locale.ROOT);
        for (String keyword : SENSITIVE_FIELD_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
