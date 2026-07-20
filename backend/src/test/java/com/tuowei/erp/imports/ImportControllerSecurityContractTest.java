package com.tuowei.erp.imports;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.imports.service.ImportJobService;
import com.tuowei.erp.imports.web.ImportJobPageQuery;
import com.tuowei.erp.imports.web.ImportJobResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
class ImportControllerSecurityContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImportJobService importJobService;

    @Test
    @WithErpUser(authorities = "system:user:view")
    void templateRequiresImportManagePermission() throws Exception {
        mockMvc.perform(get("/api/import/templates/{type}", "PRODUCT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(importJobService);
    }

    @Test
    @WithErpUser(authorities = "import:init:manage")
    void templateDelegatesToServiceAndPreservesCsvResponse() throws Exception {
        when(importJobService.template("PRODUCT")).thenReturn(ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition("product-template.csv"))
                .body(new ByteArrayResource("product_code,product_name\r\nP001,标准商品\r\n"
                        .getBytes(StandardCharsets.UTF_8))));

        mockMvc.perform(get("/api/import/templates/{type}", "PRODUCT"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                        .contains("filename*=UTF-8''product-template.csv")
                        .doesNotContain("filename=product-template.csv"))
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string("product_code,product_name\r\nP001,标准商品\r\n"));

        verify(importJobService).template("PRODUCT");
    }

    @Test
    @WithErpUser(authorities = "system:user:view")
    void previewRequiresImportManagePermission() throws Exception {
        mockMvc.perform(multipart("/api/import/jobs/{type}/preview", "PRODUCT")
                        .file(csvFile()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(importJobService);
    }

    @Test
    @WithErpUser(authorities = "import:init:manage")
    void previewDelegatesMultipartRequestToService() throws Exception {
        when(importJobService.preview(eq("PRODUCT"), any(MultipartFile.class))).thenReturn(response("VALIDATED", 0));

        mockMvc.perform(multipart("/api/import/jobs/{type}/preview", "PRODUCT")
                        .file(csvFile()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(8101))
                .andExpect(jsonPath("$.data.importType").value("PRODUCT"))
                .andExpect(jsonPath("$.data.status").value("VALIDATED"));

        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(importJobService).preview(eq("PRODUCT"), fileCaptor.capture());
        assertThat(fileCaptor.getValue().getOriginalFilename()).isEqualTo("product.csv");
        assertThat(fileCaptor.getValue().getContentType()).isEqualTo("text/csv");
    }

    @Test
    @WithErpUser(authorities = "system:user:view")
    void listRequiresImportManagePermission() throws Exception {
        mockMvc.perform(get("/api/import/jobs")
                        .param("importType", "PRODUCT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(importJobService);
    }

    @Test
    @WithErpUser(authorities = "import:init:manage")
    void listDelegatesPageQueryToService() throws Exception {
        when(importJobService.list(any(ImportJobPageQuery.class))).thenReturn(new PageResponse<>(
                1,
                20,
                1,
                List.of(response("INVALID", 0))
        ));

        mockMvc.perform(get("/api/import/jobs")
                        .param("pageNo", "1")
                        .param("pageSize", "20")
                        .param("importType", "PRODUCT")
                        .param("status", "INVALID")
                        .param("createdBy", "886001")
                        .param("createdTimeFrom", "2026-05-01T00:00:00")
                        .param("createdTimeTo", "2026-05-31T23:59:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].jobId").value(8101));

        ArgumentCaptor<ImportJobPageQuery> queryCaptor = ArgumentCaptor.forClass(ImportJobPageQuery.class);
        verify(importJobService).list(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getImportType()).isEqualTo("PRODUCT");
        assertThat(queryCaptor.getValue().getStatus()).isEqualTo("INVALID");
        assertThat(queryCaptor.getValue().getCreatedBy()).isEqualTo(886001L);
    }

    @Test
    @WithErpUser(authorities = "import:init:manage")
    void detailDelegatesToService() throws Exception {
        when(importJobService.detail(8101L)).thenReturn(response("VALIDATED", 0));

        mockMvc.perform(get("/api/import/jobs/{jobId}", 8101L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(8101))
                .andExpect(jsonPath("$.data.status").value("VALIDATED"));

        verify(importJobService).detail(8101L);
    }

    @Test
    @WithErpUser(authorities = "system:user:view")
    void exportErrorRowsRequiresImportManagePermission() throws Exception {
        mockMvc.perform(get("/api/import/jobs/{jobId}/error-rows/export", 8101L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(importJobService);
    }

    @Test
    @WithErpUser(authorities = "import:init:manage")
    void exportErrorRowsDelegatesToServiceAndPreservesCsvResponse() throws Exception {
        when(importJobService.exportErrorRows(8101L)).thenReturn(ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition("import-job-8101-errors.csv"))
                .body(new ByteArrayResource("rowNo,rawData,errors\r\n2,bad,error\r\n"
                        .getBytes(StandardCharsets.UTF_8))));

        mockMvc.perform(get("/api/import/jobs/{jobId}/error-rows/export", 8101L))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                        .contains("filename*=UTF-8''import-job-8101-errors.csv")
                        .doesNotContain("filename=import-job-8101-errors.csv"))
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string("rowNo,rawData,errors\r\n2,bad,error\r\n"));

        verify(importJobService).exportErrorRows(8101L);
    }

    @Test
    @WithErpUser(authorities = "system:user:view")
    void commitRequiresImportManagePermission() throws Exception {
        mockMvc.perform(post("/api/import/jobs/{jobId}/commit", 8101L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(importJobService);
    }

    @Test
    @WithErpUser(authorities = "import:init:manage")
    void commitDelegatesToService() throws Exception {
        when(importJobService.commit(8101L)).thenReturn(response("COMMITTED", 1));

        mockMvc.perform(post("/api/import/jobs/{jobId}/commit", 8101L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(8101))
                .andExpect(jsonPath("$.data.status").value("COMMITTED"))
                .andExpect(jsonPath("$.data.committedRows").value(1));

        verify(importJobService).commit(8101L);
    }

    private static MockMultipartFile csvFile() {
        return new MockMultipartFile(
                "file",
                "product.csv",
                "text/csv",
                "product_code,product_name\r\nP001,标准商品\r\n".getBytes(StandardCharsets.UTF_8)
        );
    }

    private static ImportJobResponse response(String status, int committedRows) {
        return new ImportJobResponse(
                8101L,
                "PRODUCT",
                "product.csv",
                status,
                1,
                1,
                0,
                committedRows,
                null,
                List.of()
        );
    }

    private static String contentDisposition(String filename) {
        return ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();
    }
}
