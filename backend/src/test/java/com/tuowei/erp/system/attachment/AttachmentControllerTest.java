package com.tuowei.erp.system.attachment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.FileSystemUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttachmentControllerTest {

    private static final long ADMIN_ID = 884001L;
    private static final long ATTACHMENT_ROLE_ID = 884401L;
    private static final String ADMIN_USERNAME = "attachment_admin";
    private static final String PASSWORD = "P@ssw0rd123";
    private static final String BUSINESS_TYPE = "SALES_ORDER";
    private static final long BUSINESS_ID = 910001L;
    private static final String BUSINESS_NO = "SO-ATT-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        cleanup();
        seedAdmin();
    }

    @AfterEach
    void cleanupAfter() {
        cleanup();
    }

    @Test
    void adminCanUploadListAndDownloadAttachment() throws Exception {
        String token = login();
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
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.businessType").value(BUSINESS_TYPE))
                .andExpect(jsonPath("$.data.businessId").value(Long.toString(BUSINESS_ID)))
                .andExpect(jsonPath("$.data.businessNo").value(BUSINESS_NO))
                .andExpect(jsonPath("$.data.originalFilename").value("contract.txt"))
                .andExpect(jsonPath("$.data.contentType").value(MediaType.TEXT_PLAIN_VALUE))
                .andExpect(jsonPath("$.data.fileSize").value(16))
                .andReturn();

        long attachmentId = objectMapper.readTree(uploadResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("id")
                .asLong();

        mockMvc.perform(get("/api/system/attachments")
                        .header("Authorization", "Bearer " + token)
                        .param("businessType", BUSINESS_TYPE)
                        .param("businessId", Long.toString(BUSINESS_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(Long.toString(attachmentId)))
                .andExpect(jsonPath("$.data.records[0].originalFilename").value("contract.txt"));

        mockMvc.perform(get("/api/system/attachments/{id}/download", attachmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("contract.txt")))
                .andExpect(content().string("contract-content"));
    }

    @Test
    void deleteSoftDeletesAttachmentAndBlocksDownload() throws Exception {
        String token = login();
        long attachmentId = upload(token);

        mockMvc.perform(delete("/api/system/attachments/{id}", attachmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/system/attachments")
                        .header("Authorization", "Bearer " + token)
                        .param("businessType", BUSINESS_TYPE)
                        .param("businessId", Long.toString(BUSINESS_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/system/attachments/{id}/download", attachmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
    }

    private long upload(String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "delete-me.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "delete-content".getBytes(StandardCharsets.UTF_8)
        );
        MvcResult result = mockMvc.perform(multipart("/api/system/attachments")
                        .file(file)
                        .param("businessType", BUSINESS_TYPE)
                        .param("businessId", Long.toString(BUSINESS_ID))
                        .param("businessNo", BUSINESS_NO)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("id")
                .asLong();
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(ADMIN_USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        return data.path("accessToken").asText();
    }

    private void seedAdmin() {
        jdbcTemplate.update("""
                insert into sys_user
                (id, company_id, account_book_id, username, password, employee_no, real_name, dept_id, post_id,
                 status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 'EMP_ATTACHMENT_884001', '附件管理员', 3501, 3601,
                        'ACTIVE', 0, 'attachment controller test', 0, 0, 0)
                """, ADMIN_ID, ADMIN_USERNAME, passwordEncoder.encode(PASSWORD));
        seedAttachmentRole();
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (884901, ?, ?, 0)",
                ADMIN_ID, ATTACHMENT_ROLE_ID);
    }

    private void seedAttachmentRole() {
        jdbcTemplate.update("""
                insert into sys_role
                (id, company_id, account_book_id, role_code, role_name, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, 'ATTACHMENT_CONTROLLER_TEST', '附件控制器测试角色', 'ACTIVE', 0,
                        'attachment controller test', 0, 0, 0)
                """, ATTACHMENT_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (884902, ?, 5060, 0)",
                ATTACHMENT_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (884903, ?, 5061, 0)",
                ATTACHMENT_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (884904, ?, 5062, 0)",
                ATTACHMENT_ROLE_ID);
    }

    private void cleanup() {
        jdbcTemplate.update("delete from sys_attachment where created_by = ?", ADMIN_ID);
        jdbcTemplate.update("delete from sys_refresh_token where user_id = ?", ADMIN_ID);
        jdbcTemplate.update("delete from sys_login_log where username = ?", ADMIN_USERNAME);
        jdbcTemplate.update("delete from sys_user_role where user_id = ?", ADMIN_ID);
        jdbcTemplate.update("delete from sys_role_menu where role_id = ?", ATTACHMENT_ROLE_ID);
        jdbcTemplate.update("delete from sys_role where id = ?", ATTACHMENT_ROLE_ID);
        jdbcTemplate.update("delete from sys_user where id = ?", ADMIN_ID);
        FileSystemUtils.deleteRecursively(Path.of("target", "test-attachments").toFile());
    }
}
