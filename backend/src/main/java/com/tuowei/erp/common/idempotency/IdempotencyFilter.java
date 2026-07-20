package com.tuowei.erp.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.web.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";

    private static final Set<String> IDEMPOTENT_METHODS = Set.of("POST", "PUT", "DELETE");
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!shouldHandle(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        ErpPrincipal principal = currentPrincipal();
        if (principal == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (request.getContentLengthLong() > idempotencyService.maxRequestBodyBytes()) {
            writeApiResponse(response, HttpStatus.BAD_REQUEST, "请求体超过幂等处理限制");
            return;
        }

        byte[] requestBody;
        try {
            requestBody = readRequestBody(request);
        } catch (RequestBodyTooLargeException ex) {
            writeApiResponse(response, HttpStatus.BAD_REQUEST, ex.getMessage());
            return;
        }
        String requestHash = sha256(requestBody);
        IdempotencyService.BeginResult beginResult;
        try {
            beginResult = idempotencyService.begin(
                    principal,
                    request.getHeader(IDEMPOTENCY_KEY_HEADER),
                    request.getMethod(),
                    requestPath(request),
                    requestHash
            );
        } catch (BusinessConflictException ex) {
            writeApiResponse(response, HttpStatus.CONFLICT, ex.getMessage());
            return;
        } catch (IllegalArgumentException ex) {
            writeApiResponse(response, HttpStatus.BAD_REQUEST, ex.getMessage());
            return;
        }

        if (beginResult.replay()) {
            replay(response, beginResult);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request, requestBody);
        LimitedContentCaptureResponseWrapper wrappedResponse = new LimitedContentCaptureResponseWrapper(
                response,
                idempotencyService.maxReplayBodyBytes()
        );
        wrappedResponse.setHeader(IDEMPOTENCY_REPLAYED_HEADER, "false");
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } catch (IOException | ServletException | RuntimeException ex) {
            idempotencyService.abandon(beginResult.requestId());
            wrappedResponse.copyBodyToResponse();
            throw ex;
        }

        byte[] responseBody = wrappedResponse.getCapturedBody();
        if (shouldStore(wrappedResponse, responseBody)) {
            idempotencyService.complete(
                    beginResult.requestId(),
                    wrappedResponse.getStatus(),
                    wrappedResponse.getContentType(),
                    new String(responseBody, StandardCharsets.UTF_8)
            );
        } else {
            idempotencyService.abandon(beginResult.requestId());
        }
        wrappedResponse.copyBodyToResponse();
    }

    private boolean shouldHandle(HttpServletRequest request) {
        if (!idempotencyService.enabled()) {
            return false;
        }
        if (!IDEMPOTENT_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            return false;
        }
        if (!StringUtils.hasText(request.getHeader(IDEMPOTENCY_KEY_HEADER))) {
            return false;
        }
        return isJsonOrEmptyContentType(request.getContentType());
    }

    private boolean isJsonOrEmptyContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return true;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.startsWith(MediaType.APPLICATION_JSON_VALUE)
                || normalized.contains("+json");
    }

    private boolean shouldStore(LimitedContentCaptureResponseWrapper response, byte[] body) {
        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            return false;
        }
        if (response.captureOverflowed()) {
            return false;
        }
        if (body.length > idempotencyService.maxReplayBodyBytes()) {
            return false;
        }
        return isJsonOrEmptyContentType(response.getContentType());
    }

    private byte[] readRequestBody(HttpServletRequest request) throws IOException {
        int maxRequestBodyBytes = idempotencyService.maxRequestBodyBytes();
        ByteArrayOutputStream body = new ByteArrayOutputStream(Math.min(maxRequestBodyBytes, 4096));
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        var inputStream = request.getInputStream();
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > maxRequestBodyBytes) {
                throw new RequestBodyTooLargeException("请求体超过幂等处理限制");
            }
            body.write(buffer, 0, read);
        }
        return body.toByteArray();
    }

    private void replay(HttpServletResponse response, IdempotencyService.BeginResult beginResult) throws IOException {
        int status = beginResult.responseStatus() == null ? HttpStatus.OK.value() : beginResult.responseStatus();
        String body = beginResult.responseBody() == null ? "" : beginResult.responseBody();
        response.setStatus(status);
        response.setHeader(IDEMPOTENCY_REPLAYED_HEADER, "true");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (StringUtils.hasText(beginResult.responseContentType())) {
            response.setContentType(beginResult.responseContentType());
        } else {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        }
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }

    private void writeApiResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), new ApiResponse<>(String.valueOf(status.value()), message, null));
    }

    private ErpPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ErpPrincipal principal) {
            return principal;
        }
        return null;
    }

    private String requestPath(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (!StringUtils.hasText(queryString)) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + queryString;
    }

    private String sha256(byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return URL_ENCODER.encodeToString(digest.digest(body));
        } catch (Exception ex) {
            throw new IllegalStateException("请求体哈希失败", ex);
        }
    }

    private static class RequestBodyTooLargeException extends RuntimeException {

        RequestBodyTooLargeException(String message) {
            super(message);
        }
    }
}
