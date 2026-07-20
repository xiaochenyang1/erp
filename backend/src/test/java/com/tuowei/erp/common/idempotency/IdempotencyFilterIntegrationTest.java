package com.tuowei.erp.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.config.IdempotencyProperties;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.web.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotencyFilterIntegrationTest {

    private MockMvc mockMvc;
    private InMemoryIdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        CurrentUserContext currentUserContext = new CurrentUserContext();
        IdempotencyTestController controller = new IdempotencyTestController(currentUserContext);
        idempotencyService = new InMemoryIdempotencyService();
        IdempotencyFilter filter = new IdempotencyFilter(idempotencyService, new ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(filter)
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void repeatedPostWithSameIdempotencyKeyReplaysFirstResponse() throws Exception {
        String body = "{\"name\":\"alpha\"}";

        mockMvc.perform(post("/api/test/idempotency/counter")
                        .with(erpUser(1L, 501L))
                        .header("Idempotency-Key", "idem-replay-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.data.sequence").value(1))
                .andExpect(jsonPath("$.data.companyId").value(501));

        mockMvc.perform(post("/api/test/idempotency/counter")
                        .with(erpUser(1L, 501L))
                        .header("Idempotency-Key", "idem-replay-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.data.sequence").value(1))
                .andExpect(jsonPath("$.data.companyId").value(501));
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/test/idempotency/counter")
                        .with(erpUser(1L, 502L))
                        .header("Idempotency-Key", "idem-conflict-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"alpha\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sequence").value(1));

        mockMvc.perform(post("/api/test/idempotency/counter")
                        .with(erpUser(1L, 502L))
                        .header("Idempotency-Key", "idem-conflict-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"beta\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("409"))
                .andExpect(jsonPath("$.message").value("Idempotency-Key 已用于不同请求，请重新生成后再提交"));
    }

    @Test
    void idempotencyKeyIsIsolatedByCompany() throws Exception {
        String body = "{\"name\":\"tenant-scope\"}";

        mockMvc.perform(post("/api/test/idempotency/counter")
                        .with(erpUser(6101L, 601L))
                        .header("Idempotency-Key", "idem-tenant-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sequence").value(1))
                .andExpect(jsonPath("$.data.companyId").value(601));

        mockMvc.perform(post("/api/test/idempotency/counter")
                        .with(erpUser(6201L, 602L))
                        .header("Idempotency-Key", "idem-tenant-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sequence").value(2))
                .andExpect(jsonPath("$.data.companyId").value(602));

        mockMvc.perform(post("/api/test/idempotency/counter")
                        .with(erpUser(6101L, 601L))
                        .header("Idempotency-Key", "idem-tenant-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.data.sequence").value(1))
                .andExpect(jsonPath("$.data.companyId").value(601));
    }

    @Test
    void idempotencyKeyIsIsolatedByUserWithinSameCompany() throws Exception {
        String body = "{\"name\":\"user-scope\"}";

        mockMvc.perform(post("/api/test/idempotency/counter")
                        .with(erpUser(7101L, 701L))
                        .header("Idempotency-Key", "idem-user-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.data.sequence").value(1))
                .andExpect(jsonPath("$.data.companyId").value(701));

        mockMvc.perform(post("/api/test/idempotency/counter")
                        .with(erpUser(7201L, 701L))
                        .header("Idempotency-Key", "idem-user-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.data.sequence").value(2))
                .andExpect(jsonPath("$.data.companyId").value(701));

        mockMvc.perform(post("/api/test/idempotency/counter")
                        .with(erpUser(7101L, 701L))
                        .header("Idempotency-Key", "idem-user-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.data.sequence").value(1))
                .andExpect(jsonPath("$.data.companyId").value(701));
    }

    @Test
    void idempotencyKeyIsIsolatedByAccountBookWithinSameCompanyAndUser() throws Exception {
        String body = "{\"name\":\"book-scope\"}";

        mockMvc.perform(post("/api/test/idempotency/counter")
                        .with(erpUser(7301L, 703L, 1L))
                        .header("Idempotency-Key", "idem-book-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.data.sequence").value(1))
                .andExpect(jsonPath("$.data.companyId").value(703))
                .andExpect(jsonPath("$.data.accountBookId").value(1));

        mockMvc.perform(post("/api/test/idempotency/counter")
                        .with(erpUser(7301L, 703L, 2L))
                        .header("Idempotency-Key", "idem-book-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.data.sequence").value(2))
                .andExpect(jsonPath("$.data.companyId").value(703))
                .andExpect(jsonPath("$.data.accountBookId").value(2));

        mockMvc.perform(post("/api/test/idempotency/counter")
                        .with(erpUser(7301L, 703L, 1L))
                        .header("Idempotency-Key", "idem-book-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.data.sequence").value(1))
                .andExpect(jsonPath("$.data.accountBookId").value(1));
    }

    @Test
    void rejectsIdempotentJsonRequestBodyLargerThanDefaultLimit() throws Exception {
        String body = "{\"name\":\"" + "A".repeat(1_048_577) + "\"}";

        mockMvc.perform(post("/api/test/idempotency/counter")
                        .with(erpUser(8101L, 801L))
                        .header("Idempotency-Key", "idem-large-request-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("请求体超过幂等处理限制"));
    }

    @Test
    void largeResponseBodyIsReturnedButNotStoredForReplay() throws Exception {
        idempotencyService.setMaxReplayBodyBytes(64);
        String body = "{\"name\":\"large-response\"}";
        String payload = "R".repeat(256);

        mockMvc.perform(post("/api/test/idempotency/large-response")
                        .with(erpUser(9101L, 901L))
                        .header("Idempotency-Key", "idem-large-response-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.data.sequence").value(1))
                .andExpect(jsonPath("$.data.payload").value(payload));

        mockMvc.perform(post("/api/test/idempotency/large-response")
                        .with(erpUser(9101L, 901L))
                        .header("Idempotency-Key", "idem-large-response-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.data.sequence").value(2))
                .andExpect(jsonPath("$.data.payload").value(payload));
    }

    private RequestPostProcessor erpUser(long userId, long companyId) {
        return erpUser(userId, companyId, 1L);
    }

    private RequestPostProcessor erpUser(long userId, long companyId, long accountBookId) {
        return request -> {
            SecurityContextHolder.getContext().setAuthentication(authenticationFor(userId, companyId, accountBookId));
            return request;
        };
    }

    private Authentication authenticationFor(long userId, long companyId, long accountBookId) {
        ErpPrincipal principal = new ErpPrincipal(
                userId,
                companyId,
                accountBookId,
                1L,
                1L,
                "idem_user_" + userId,
                "幂等测试用户",
                "N/A",
                Set.of(),
                DataScopeSnapshot.all()
        );
        return UsernamePasswordAuthenticationToken.authenticated(principal, "N/A", principal.getAuthorities());
    }

    @RestController
    static class IdempotencyTestController {

        private final CurrentUserContext currentUserContext;
        private final AtomicInteger counter = new AtomicInteger();

        IdempotencyTestController(CurrentUserContext currentUserContext) {
            this.currentUserContext = currentUserContext;
        }

        @PostMapping("/api/test/idempotency/counter")
        ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> request) {
            CurrentUser user = currentUserContext.requireCurrentUser();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sequence", counter.incrementAndGet());
            data.put("companyId", user.companyId());
            data.put("accountBookId", user.accountBookId());
            data.put("name", request.get("name"));
            return ApiResponse.success(data);
        }

        @PostMapping("/api/test/idempotency/large-response")
        ApiResponse<Map<String, Object>> largeResponse(@RequestBody Map<String, Object> request) {
            CurrentUser user = currentUserContext.requireCurrentUser();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sequence", counter.incrementAndGet());
            data.put("companyId", user.companyId());
            data.put("accountBookId", user.accountBookId());
            data.put("name", request.get("name"));
            data.put("payload", "R".repeat(256));
            return ApiResponse.success(data);
        }
    }

    static class InMemoryIdempotencyService extends IdempotencyService {

        private static final String STATUS_PROCESSING = "PROCESSING";
        private static final String STATUS_COMPLETED = "COMPLETED";

        private final AtomicLong ids = new AtomicLong(1);
        private final Map<RequestKey, StoredRequest> requests = new HashMap<>();
        private final Map<Long, RequestKey> idsToKeys = new HashMap<>();
        private int maxReplayBodyBytes = 1_048_576;

        InMemoryIdempotencyService() {
            super(null, new IdempotencyProperties(true, 86_400, 1_048_576, 1_048_576), Clock.systemUTC());
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public int maxReplayBodyBytes() {
            return maxReplayBodyBytes;
        }

        void setMaxReplayBodyBytes(int maxReplayBodyBytes) {
            this.maxReplayBodyBytes = maxReplayBodyBytes;
        }

        @Override
        public int maxRequestBodyBytes() {
            return 1_048_576;
        }

        @Override
        public BeginResult begin(
                ErpPrincipal principal,
                String idempotencyKey,
                String requestMethod,
                String requestPath,
                String requestBodyHash
        ) {
            if (!StringUtils.hasText(idempotencyKey)) {
                throw new IllegalArgumentException("Idempotency-Key 不能为空");
            }
            RequestKey key = new RequestKey(
                    principal.companyId(),
                    principal.accountBookId(),
                    principal.userId(),
                    requestMethod,
                    requestPath,
                    idempotencyKey.trim()
            );
            StoredRequest existing = requests.get(key);
            if (existing != null) {
                if (!existing.requestBodyHash.equals(requestBodyHash)) {
                    throw new BusinessConflictException("Idempotency-Key 已用于不同请求，请重新生成后再提交");
                }
                if (STATUS_COMPLETED.equals(existing.status)) {
                    return BeginResult.replay(
                            existing.responseStatus,
                            existing.responseContentType,
                            existing.responseBody
                    );
                }
                throw new BusinessConflictException("请求正在处理中，请稍后重试");
            }

            long id = ids.getAndIncrement();
            requests.put(key, new StoredRequest(id, requestBodyHash));
            idsToKeys.put(id, key);
            return BeginResult.proceed(id);
        }

        @Override
        public void complete(Long id, int responseStatus, String responseContentType, String responseBody) {
            StoredRequest stored = request(id);
            stored.status = STATUS_COMPLETED;
            stored.responseStatus = responseStatus;
            stored.responseContentType = responseContentType;
            stored.responseBody = responseBody;
        }

        @Override
        public void abandon(Long id) {
            RequestKey key = idsToKeys.remove(id);
            if (key != null) {
                requests.remove(key);
            }
        }

        private StoredRequest request(Long id) {
            RequestKey key = idsToKeys.get(id);
            if (key == null) {
                throw new IllegalStateException("Unknown idempotency request: " + id);
            }
            return requests.get(key);
        }

        private record RequestKey(Long companyId, Long accountBookId, Long userId, String method, String path, String idempotencyKey) {
        }

        private static class StoredRequest {

            private final Long id;
            private final String requestBodyHash;
            private String status = STATUS_PROCESSING;
            private Integer responseStatus;
            private String responseContentType;
            private String responseBody;

            private StoredRequest(Long id, String requestBodyHash) {
                this.id = id;
                this.requestBodyHash = requestBodyHash;
            }
        }
    }
}
