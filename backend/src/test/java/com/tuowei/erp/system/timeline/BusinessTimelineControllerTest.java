package com.tuowei.erp.system.timeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.common.security.UserPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.FileSystemUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BusinessTimelineControllerTest {

    private static final long ADMIN_ID = 885001L;
    private static final long OTHER_ADMIN_ID = 885002L;
    private static final long ROLE_ID = 885401L;
    private static final long OTHER_ROLE_ID = 885402L;
    private static final String ADMIN_USERNAME = "timeline_admin";
    private static final String OTHER_USERNAME = "timeline_other_admin";
    private static final String PASSWORD = "P@ssw0rd123";
    private static final String BUSINESS_TYPE = "SALES_ORDER";
    private static final long BUSINESS_ID = 920001L;
    private static final String BUSINESS_NO = "SO-TL-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserPermissionService userPermissionService;

    @Autowired
    private SecurityPrincipalCache securityPrincipalCache;

    @BeforeEach
    void setup() {
        cleanup();
        seedUsers();
        evictAuthCaches();
    }

    @AfterEach
    void cleanupAfter() {
        cleanup();
    }

    @Test
    void canCreateAndListBusinessCommentTimelineItems() throws Exception {
        String token = login(ADMIN_USERNAME);

        mockMvc.perform(post("/api/business-timeline/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessType": "sales_order",
                                  "businessId": 920001,
                                  "businessNo": "SO-TL-001",
                                  "content": "客户补充了开票资料"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessType").value(BUSINESS_TYPE))
                .andExpect(jsonPath("$.data.businessId").value(BUSINESS_ID))
                .andExpect(jsonPath("$.data.businessNo").value(BUSINESS_NO))
                .andExpect(jsonPath("$.data.eventType").value("COMMENT"))
                .andExpect(jsonPath("$.data.content").value("客户补充了开票资料"))
                .andExpect(jsonPath("$.data.operatorUserId").value(ADMIN_ID));

        mockMvc.perform(get("/api/business-timeline")
                        .header("Authorization", "Bearer " + token)
                        .param("businessType", BUSINESS_TYPE)
                        .param("businessId", Long.toString(BUSINESS_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].eventType").value("COMMENT"))
                .andExpect(jsonPath("$.data.records[0].content").value("客户补充了开票资料"));
    }

    @Test
    void attachmentUploadCreatesBusinessTimelineItem() throws Exception {
        String token = login(ADMIN_USERNAME);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "contract-content".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/system/attachments")
                        .file(file)
                        .param("businessType", BUSINESS_TYPE)
                        .param("businessId", Long.toString(BUSINESS_ID))
                        .param("businessNo", BUSINESS_NO)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        long attachmentId = objectMapper.readTree(uploadResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("id")
                .asLong();

        mockMvc.perform(get("/api/business-timeline")
                        .header("Authorization", "Bearer " + token)
                        .param("businessType", BUSINESS_TYPE)
                        .param("businessId", Long.toString(BUSINESS_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].eventType").value("ATTACHMENT_UPLOADED"))
                .andExpect(jsonPath("$.data.records[0].content").value("上传附件：contract.txt"))
                .andExpect(jsonPath("$.data.records[0].attachmentId").value(attachmentId));
    }

    @Test
    void timelineIsScopedByCurrentAccountBook() throws Exception {
        String token = login(ADMIN_USERNAME);
        String otherToken = login(OTHER_USERNAME);

        mockMvc.perform(post("/api/business-timeline/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessType": "SALES_ORDER",
                                  "businessId": 920001,
                                  "businessNo": "SO-TL-001",
                                  "content": "只属于账套一"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/business-timeline")
                        .header("Authorization", "Bearer " + otherToken)
                        .param("businessType", BUSINESS_TYPE)
                        .param("businessId", Long.toString(BUSINESS_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        return data.path("accessToken").asText();
    }

    private void seedUsers() {
        seedRole();
        seedUser(ADMIN_ID, ADMIN_USERNAME, 1L);
        seedUser(OTHER_ADMIN_ID, OTHER_USERNAME, 2L);
    }

    private void seedUser(long userId, String username, long accountBookId) {
        jdbcTemplate.update("""
                insert into sys_user
                (id, company_id, account_book_id, username, password, employee_no, real_name, dept_id, post_id,
                 status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, ?, ?, ?, ?, ?, 3501, 3601,
                        'ACTIVE', 0, 'timeline controller test', 0, 0, 0)
                """, userId, accountBookId, username, passwordEncoder.encode(PASSWORD),
                "EMP_TIMELINE_" + userId, "时间线管理员" + userId);
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (?, ?, ?, 0)",
                userId + 1000, userId, accountBookId == 1L ? ROLE_ID : OTHER_ROLE_ID);
    }

    private void seedRole() {
        jdbcTemplate.update("""
                insert into sys_role
                (id, company_id, account_book_id, role_code, role_name, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, 'TIMELINE_CONTROLLER_TEST', '业务时间线测试角色', 'ACTIVE', 0,
                        'timeline controller test', 0, 0, 0)
                """, ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (885901, ?, 5060, 0)",
                ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (885902, ?, 5061, 0)",
                ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (885903, ?, 5062, 0)",
                ROLE_ID);
        jdbcTemplate.update("""
                insert into sys_role
                (id, company_id, account_book_id, role_code, role_name, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 2, 'TIMELINE_CONTROLLER_TEST_VIEW', '业务时间线测试只读角色', 'ACTIVE', 0,
                        'timeline controller test', 0, 0, 0)
                """, OTHER_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (885904, ?, 5060, 0)",
                OTHER_ROLE_ID);
    }

    private void cleanup() {
        evictAuthCaches();
        jdbcTemplate.update("delete from biz_business_timeline where created_by in (?, ?)", ADMIN_ID, OTHER_ADMIN_ID);
        jdbcTemplate.update("delete from sys_attachment where created_by in (?, ?)", ADMIN_ID, OTHER_ADMIN_ID);
        jdbcTemplate.update("delete from sys_refresh_token where user_id in (?, ?)", ADMIN_ID, OTHER_ADMIN_ID);
        jdbcTemplate.update("delete from sys_login_log where username in (?, ?)", ADMIN_USERNAME, OTHER_USERNAME);
        jdbcTemplate.update("delete from sys_user_role where user_id in (?, ?)", ADMIN_ID, OTHER_ADMIN_ID);
        jdbcTemplate.update("delete from sys_role_menu where role_id in (?, ?)", ROLE_ID, OTHER_ROLE_ID);
        jdbcTemplate.update("delete from sys_role where id in (?, ?)", ROLE_ID, OTHER_ROLE_ID);
        jdbcTemplate.update("delete from sys_user where id in (?, ?)", ADMIN_ID, OTHER_ADMIN_ID);
        FileSystemUtils.deleteRecursively(Path.of("target", "test-attachments").toFile());
    }

    private void evictAuthCaches() {
        userPermissionService.evictUserPermissions(ADMIN_ID, 1L, 1L);
        userPermissionService.evictUserPermissions(OTHER_ADMIN_ID, 1L, 2L);
        securityPrincipalCache.evictUser(ADMIN_ID);
        securityPrincipalCache.evictUser(OTHER_ADMIN_ID);
    }
}
