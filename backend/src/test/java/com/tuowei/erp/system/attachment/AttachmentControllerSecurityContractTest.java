package com.tuowei.erp.system.attachment;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.attachment.web.AttachmentPageQuery;
import com.tuowei.erp.system.attachment.web.AttachmentPolicyResponse;
import com.tuowei.erp.system.attachment.web.AttachmentResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttachmentControllerSecurityContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttachmentService attachmentService;

    @Test
    @WithErpUser(authorities = "system:attachment:view")
    void uploadRequiresManagePermission() throws Exception {
        mockMvc.perform(multipart("/api/system/attachments")
                        .file(file())
                        .param("businessType", "SALES_ORDER")
                        .param("businessId", "910001")
                        .param("businessNo", "SO-ATT-001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(attachmentService);
    }

    @Test
    @WithErpUser(authorities = "system:attachment:manage")
    void uploadDelegatesMultipartRequestToService() throws Exception {
        when(attachmentService.upload(eq("SALES_ORDER"), eq(910001L), eq("SO-ATT-001"), any(MultipartFile.class)))
                .thenReturn(response(9001L, "contract.txt"));

        mockMvc.perform(multipart("/api/system/attachments")
                        .file(file())
                        .param("businessType", "SALES_ORDER")
                        .param("businessId", "910001")
                        .param("businessNo", "SO-ATT-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9001))
                .andExpect(jsonPath("$.data.originalFilename").value("contract.txt"));

        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(attachmentService).upload(eq("SALES_ORDER"), eq(910001L), eq("SO-ATT-001"), fileCaptor.capture());
        assertThat(fileCaptor.getValue().getOriginalFilename()).isEqualTo("contract.txt");
        assertThat(fileCaptor.getValue().getContentType()).isEqualTo(MediaType.TEXT_PLAIN_VALUE);
    }

    @Test
    @WithErpUser(authorities = "system:attachment:view")
    void listBindsPageQueryAndReturnsPageResponse() throws Exception {
        when(attachmentService.list(any(AttachmentPageQuery.class))).thenReturn(new PageResponse<>(
                2,
                20,
                1,
                List.of(response(9001L, "contract.txt"))
        ));

        mockMvc.perform(get("/api/system/attachments")
                        .param("pageNo", "2")
                        .param("pageSize", "20")
                        .param("businessType", "SALES_ORDER")
                        .param("businessId", "910001")
                        .param("businessNo", "SO-ATT-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.records[0].originalFilename").value("contract.txt"));

        ArgumentCaptor<AttachmentPageQuery> queryCaptor = ArgumentCaptor.forClass(AttachmentPageQuery.class);
        verify(attachmentService).list(queryCaptor.capture());
        AttachmentPageQuery query = queryCaptor.getValue();
        assertThat(query.getPageNo()).isEqualTo(2);
        assertThat(query.getPageSize()).isEqualTo(20);
        assertThat(query.getBusinessType()).isEqualTo("SALES_ORDER");
        assertThat(query.getBusinessId()).isEqualTo(910001L);
        assertThat(query.getBusinessNo()).isEqualTo("SO-ATT-001");
    }

    @Test
    @WithErpUser
    void policyRequiresAuthenticationOnly() throws Exception {
        when(attachmentService.policy()).thenReturn(new AttachmentPolicyResponse(
                20L * 1024 * 1024,
                1,
                List.of("EXPENSE"),
                List.of("EXPENSE", "SALES_ORDER")
        ));

        mockMvc.perform(get("/api/system/attachments/policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.maxFileSizeBytes").value(20L * 1024 * 1024))
                .andExpect(jsonPath("$.data.minRequiredCount").value(1))
                .andExpect(jsonPath("$.data.requiredBusinessTypes[0]").value("EXPENSE"))
                .andExpect(jsonPath("$.data.gatedBusinessTypes[1]").value("SALES_ORDER"));

        verify(attachmentService).policy();
    }

    @Test
    @WithErpUser(authorities = "system:attachment:view")
    void downloadDelegatesToServiceAndPreservesStreamingResponseHeaders() throws Exception {
        Resource resource = new ByteArrayResource("contract-content".getBytes(StandardCharsets.UTF_8));
        when(attachmentService.download(9001L)).thenReturn(ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(16)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition("contract.txt"))
                .body(resource));

        mockMvc.perform(get("/api/system/attachments/{id}/download", 9001L))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                        .contains("filename*=UTF-8''contract.txt")
                        .doesNotContain("filename=contract.txt"))
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("contract-content"));

        verify(attachmentService).download(9001L);
    }

    @Test
    @WithErpUser(authorities = "system:attachment:view")
    void deleteRequiresDeletePermission() throws Exception {
        mockMvc.perform(delete("/api/system/attachments/{id}", 9001L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(attachmentService);
    }

    @Test
    @WithErpUser(authorities = "system:attachment:delete")
    void deleteDelegatesToService() throws Exception {
        mockMvc.perform(delete("/api/system/attachments/{id}", 9001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(attachmentService).delete(9001L);
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile(
                "file",
                "contract.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "contract-content".getBytes(StandardCharsets.UTF_8)
        );
    }

    private static AttachmentResponse response(Long id, String originalFilename) {
        return new AttachmentResponse(
                id,
                "SALES_ORDER",
                910001L,
                "SO-ATT-001",
                originalFilename,
                MediaType.TEXT_PLAIN_VALUE,
                16L,
                "checksum",
                LocalDateTime.of(2026, 1, 1, 9, 30),
                1001L
        );
    }

    private static String contentDisposition(String filename) {
        return ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();
    }
}
