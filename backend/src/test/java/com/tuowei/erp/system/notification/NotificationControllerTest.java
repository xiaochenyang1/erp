package com.tuowei.erp.system.notification;

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

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTest {

    private static final long USER_ID = 885001L;
    private static final long OTHER_USER_ID = 885002L;
    private static final long NOTIFICATION_ROLE_ID = 885401L;
    private static final String USERNAME = "notification_user";
    private static final String OTHER_USERNAME = "notification_other";
    private static final String PASSWORD = "P@ssw0rd123";

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
        seedUser(USER_ID, USERNAME, 1L, 1L);
        seedUser(OTHER_USER_ID, OTHER_USERNAME, 1L, 1L);
        seedNotificationRole();
        jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (885901, ?, ?, 0)",
                USER_ID, NOTIFICATION_ROLE_ID);
    }

    @AfterEach
    void cleanupAfter() {
        cleanup();
    }

    @Test
    void currentUserOnlySeesOwnNotificationsAndUnreadCount() throws Exception {
        seedNotification(885101L, 885201L, 1L, 1L, USER_ID, "NOTICE", "SYSTEM", "我的通知", "mine");
        seedNotification(885102L, 885202L, 1L, 1L, OTHER_USER_ID, "NOTICE", "SYSTEM", "别人的通知", "other");
        seedNotification(885103L, 885203L, 2L, 1L, USER_ID, "NOTICE", "SYSTEM", "其他租户通知", "tenant");
        String token = login(USERNAME);

        mockMvc.perform(get("/api/system/notifications")
                        .header("Authorization", "Bearer " + token)
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].recipientId").value(885201L))
                .andExpect(jsonPath("$.data.records[0].title").value("我的通知"))
                .andExpect(jsonPath("$.data.records[0].readFlag").value(false));

        mockMvc.perform(get("/api/system/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    void currentUserCanMarkOneAndAllNotificationsAsRead() throws Exception {
        seedNotification(885111L, 885211L, 1L, 1L, USER_ID, "NOTICE", "SYSTEM", "第一条", "first");
        seedNotification(885112L, 885212L, 1L, 1L, USER_ID, "NOTICE", "SYSTEM", "第二条", "second");
        String token = login(USERNAME);

        mockMvc.perform(get("/api/system/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2));

        mockMvc.perform(post("/api/system/notifications/{recipientId}/read", 885211L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipientId").value(885211L))
                .andExpect(jsonPath("$.data.readFlag").value(true));

        mockMvc.perform(get("/api/system/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        mockMvc.perform(post("/api/system/notifications/read-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/system/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    private void seedNotification(
            long notificationId,
            long recipientId,
            long companyId,
            long accountBookId,
            long recipientUserId,
            String category,
            String notificationType,
            String title,
            String businessNo
    ) {
        jdbcTemplate.update("""
                insert into sys_notification
                (id, company_id, account_book_id, category, notification_type, title, content,
                 business_type, business_id, business_no, target_url, created_by, updated_by, version)
                values (?, ?, ?, ?, ?, ?, ?, 'TEST', ?, ?, concat('/test/', ?), 0, 0, 0)
                """, notificationId, companyId, accountBookId, category, notificationType, title,
                "content-" + businessNo, notificationId, businessNo, businessNo);
        jdbcTemplate.update("""
                insert into sys_notification_recipient
                (id, company_id, notification_id, recipient_user_id, read_flag, status, created_by, updated_by, version)
                values (?, ?, ?, ?, 0, 'ACTIVE', 0, 0, 0)
                """, recipientId, companyId, notificationId, recipientUserId);
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

    private void seedUser(long id, String username, long companyId, long accountBookId) {
        jdbcTemplate.update("""
                insert into sys_user
                (id, company_id, account_book_id, username, password, employee_no, real_name, dept_id, post_id,
                 status, deleted_flag, remark, created_by, updated_by, version)
                values (?, ?, ?, ?, ?, ?, ?, 3501, 3601,
                        'ACTIVE', 0, 'notification controller test', 0, 0, 0)
                """, id, companyId, accountBookId, username, passwordEncoder.encode(PASSWORD), "EMP_" + id, username);
    }

    private void seedNotificationRole() {
        jdbcTemplate.update("""
                insert into sys_role
                (id, company_id, account_book_id, role_code, role_name, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, 'NOTIFICATION_CONTROLLER_TEST', '通知控制器测试角色', 'ACTIVE', 0,
                        'notification controller test', 0, 0, 0)
                """, NOTIFICATION_ROLE_ID);
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (885902, ?, 5063, 0)",
                NOTIFICATION_ROLE_ID);
        // 5321 = system:notification:manage(标记已读/全部已读),V102 收口后写操作需独立写权限码
        jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id, created_by) values (885903, ?, 5321, 0)",
                NOTIFICATION_ROLE_ID);
    }

    private void cleanup() {
        jdbcTemplate.update("delete from sys_notification_recipient where recipient_user_id in (?, ?)", USER_ID, OTHER_USER_ID);
        jdbcTemplate.update("delete from sys_notification where id between 885100 and 885199");
        jdbcTemplate.update("delete from sys_refresh_token where user_id in (?, ?)", USER_ID, OTHER_USER_ID);
        jdbcTemplate.update("delete from sys_login_log where username in (?, ?)", USERNAME, OTHER_USERNAME);
        jdbcTemplate.update("delete from sys_user_role where user_id in (?, ?)", USER_ID, OTHER_USER_ID);
        jdbcTemplate.update("delete from sys_role_menu where role_id = ?", NOTIFICATION_ROLE_ID);
        jdbcTemplate.update("delete from sys_role where id = ?", NOTIFICATION_ROLE_ID);
        jdbcTemplate.update("delete from sys_user where id in (?, ?)", USER_ID, OTHER_USER_ID);
    }
}
