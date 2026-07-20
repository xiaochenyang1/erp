package com.tuowei.erp.system.menu;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.menu.service.MenuService;
import com.tuowei.erp.system.menu.web.MenuCreateRequest;
import com.tuowei.erp.system.menu.web.MenuPageQuery;
import com.tuowei.erp.system.menu.web.MenuResponse;
import com.tuowei.erp.system.menu.web.MenuUpdateRequest;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MenuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MenuService menuService;

    @Test
    @WithErpUser(authorities = "system:menu:view")
    void listBindsPageQueryAndReturnsPageResponse() throws Exception {
        when(menuService.list(any(MenuPageQuery.class))).thenReturn(new PageResponse<>(
                2,
                20,
                1,
                List.of(response(5001L, 0L, "CATALOG", "SYSTEM"))
        ));

        mockMvc.perform(get("/api/system/menus")
                        .param("pageNo", "2")
                        .param("pageSize", "20")
                        .param("keyword", "system")
                        .param("status", "ACTIVE")
                        .param("parentId", "0")
                        .param("menuType", "CATALOG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.records[0].menuCode").value("SYSTEM"));

        ArgumentCaptor<MenuPageQuery> queryCaptor = ArgumentCaptor.forClass(MenuPageQuery.class);
        verify(menuService).list(queryCaptor.capture());
        MenuPageQuery query = queryCaptor.getValue();
        assertThat(query.getPageNo()).isEqualTo(2);
        assertThat(query.getPageSize()).isEqualTo(20);
        assertThat(query.getKeyword()).isEqualTo("system");
        assertThat(query.getStatus()).isEqualTo("ACTIVE");
        assertThat(query.getParentId()).isEqualTo(0L);
        assertThat(query.getMenuType()).isEqualTo("CATALOG");
    }

    @Test
    @WithErpUser(authorities = "system:menu:view")
    void treeReturnsMenuHierarchy() throws Exception {
        when(menuService.tree()).thenReturn(List.of(
                new MenuResponse(
                        5001L,
                        0L,
                        "CATALOG",
                        "SYSTEM",
                        "系统管理",
                        "/system",
                        null,
                        null,
                        1,
                        1,
                        "ACTIVE",
                        List.of(response(5002L, 5001L, "MENU", "SYSTEM_USER"))
                )
        ));

        mockMvc.perform(get("/api/system/menus/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].menuCode").value("SYSTEM"))
                .andExpect(jsonPath("$.data[0].children[0].menuCode").value("SYSTEM_USER"));

        verify(menuService).tree();
    }

    @Test
    @WithErpUser(authorities = "system:menu:view")
    void createRequiresCreatePermission() throws Exception {
        mockMvc.perform(post("/api/system/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(menuService);
    }

    @Test
    @WithErpUser(authorities = "system:menu:create")
    void createRejectsMissingMenuCodeBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/system/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "menuType": "MENU",
                                  "menuName": "用户管理"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(menuService);
    }

    @Test
    @WithErpUser(authorities = "system:menu:create")
    void createDelegatesValidatedPayloadToService() throws Exception {
        when(menuService.create(any(MenuCreateRequest.class))).thenReturn(response(5002L, 5001L, "MENU", "SYSTEM_USER"));

        mockMvc.perform(post("/api/system/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5002))
                .andExpect(jsonPath("$.data.menuCode").value("SYSTEM_USER"));

        ArgumentCaptor<MenuCreateRequest> requestCaptor = ArgumentCaptor.forClass(MenuCreateRequest.class);
        verify(menuService).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().parentId()).isEqualTo(5001L);
        assertThat(requestCaptor.getValue().menuType()).isEqualTo("MENU");
        assertThat(requestCaptor.getValue().menuCode()).isEqualTo("SYSTEM_USER");
        assertThat(requestCaptor.getValue().permission()).isEqualTo("system:user:view");
    }

    @Test
    @WithErpUser(authorities = "system:menu:update")
    void updateRejectsMissingMenuNameBeforeCallingService() throws Exception {
        mockMvc.perform(put("/api/system/menus/{id}", 5002L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"path":"/system/users"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));

        verifyNoInteractions(menuService);
    }

    @Test
    @WithErpUser(authorities = "system:menu:update")
    void updateDelegatesToService() throws Exception {
        when(menuService.update(eq(5002L), any(MenuUpdateRequest.class))).thenReturn(response(5002L, 5001L, "MENU", "SYSTEM_USER"));

        mockMvc.perform(put("/api/system/menus/{id}", 5002L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "menuName": "用户管理",
                                  "path": "/system/users",
                                  "component": "system/user/index",
                                  "permission": "system:user:view",
                                  "sortNo": 20
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5002));

        verify(menuService).update(eq(5002L), any(MenuUpdateRequest.class));
    }

    @Test
    @WithErpUser(authorities = "system:menu:enable")
    void enableDelegatesToService() throws Exception {
        when(menuService.enable(5002L)).thenReturn(response(5002L, 5001L, "MENU", "SYSTEM_USER"));

        mockMvc.perform(post("/api/system/menus/{id}/enable", 5002L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(menuService).enable(5002L);
    }

    @Test
    @WithErpUser(authorities = "system:menu:disable")
    void disableDelegatesToService() throws Exception {
        when(menuService.disable(5002L)).thenReturn(response(5002L, 5001L, "MENU", "SYSTEM_USER"));

        mockMvc.perform(post("/api/system/menus/{id}/disable", 5002L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(menuService).disable(5002L);
    }

    private static String validCreateBody() {
        return """
                {
                  "parentId": 5001,
                  "menuType": "MENU",
                  "menuCode": "SYSTEM_USER",
                  "menuName": "用户管理",
                  "path": "/system/users",
                  "component": "system/user/index",
                  "permission": "system:user:view",
                  "sortNo": 20
                }
                """;
    }

    private static MenuResponse response(Long id, Long parentId, String menuType, String menuCode) {
        return new MenuResponse(
                id,
                parentId,
                menuType,
                menuCode,
                "用户管理",
                "/system/users",
                "system/user/index",
                "system:user:view",
                20,
                1,
                "ACTIVE",
                List.of()
        );
    }
}
