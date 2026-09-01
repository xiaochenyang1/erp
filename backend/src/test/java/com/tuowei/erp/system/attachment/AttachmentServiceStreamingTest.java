package com.tuowei.erp.system.attachment;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.config.AttachmentProperties;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.attachment.mapper.AttachmentMapper;
import com.tuowei.erp.system.attachment.model.AttachmentEntity;
import com.tuowei.erp.system.attachment.service.AttachmentQueryService;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.attachment.web.AttachmentPageQuery;
import com.tuowei.erp.system.attachment.web.AttachmentResponse;
import com.tuowei.erp.system.timeline.service.BusinessTimelineService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachmentServiceStreamingTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            1L,
            1L,
            884001L,
            LocalDateTime.of(2026, 6, 2, 9, 0)
    );
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-02T01:00:00Z"),
            ZoneId.of("Asia/Shanghai")
    );

    @TempDir
    Path storageRoot;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(AttachmentEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), AttachmentEntity.class.getName());
        assistant.setCurrentNamespace(AttachmentEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, AttachmentEntity.class);
    }

    @Test
    void uploadStreamsMultipartContentWithoutCallingGetBytes() throws Exception {
        AttachmentMapper mapper = mock(AttachmentMapper.class);
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(mapper.insert(any(AttachmentEntity.class))).thenAnswer(invocation -> {
            AttachmentEntity entity = invocation.getArgument(0);
            entity.setId(1001L);
            return 1;
        });
        AttachmentService service = newService(mapper, auditMetadataFactory);

        AttachmentResponse response = service.upload(
                "sales_order",
                9001L,
                "SO-STREAM-001",
                new InputStreamOnlyMultipartFile("stream.txt", "text/plain", "stream-content")
        );

        assertThat(response.originalFilename()).isEqualTo("stream.txt");
        assertThat(response.fileSize()).isEqualTo(14L);
        assertThat(response.checksumSha256()).isEqualTo(sha256("stream-content"));
        assertThat(Files.walk(storageRoot)
                .filter(Files::isRegularFile)
                .findFirst()
                .map(path -> readString(path))
                .orElseThrow()).isEqualTo("stream-content");
    }

    @Test
    void uploadFallsBackToOctetStreamForInvalidMultipartContentType() {
        AttachmentMapper mapper = mock(AttachmentMapper.class);
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(mapper.insert(any(AttachmentEntity.class))).thenAnswer(invocation -> {
            AttachmentEntity entity = invocation.getArgument(0);
            entity.setId(1003L);
            return 1;
        });
        AttachmentService service = newService(mapper, auditMetadataFactory);

        AttachmentResponse response = service.upload(
                "sales_order",
                9001L,
                "SO-STREAM-002",
                new InputStreamOnlyMultipartFile("invalid-content-type.txt", "not a media type", "content")
        );

        assertThat(response.contentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    @Test
    void uploadSanitizesUnsafeOriginalFilenameCharacters() {
        AttachmentMapper mapper = mock(AttachmentMapper.class);
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(mapper.insert(any(AttachmentEntity.class))).thenAnswer(invocation -> {
            AttachmentEntity entity = invocation.getArgument(0);
            entity.setId(1005L);
            return 1;
        });
        AttachmentService service = newService(mapper, auditMetadataFactory);

        AttachmentResponse response = service.upload(
                "sales_order",
                9001L,
                "SO-STREAM-003",
                new InputStreamOnlyMultipartFile("..\\evil\r\nheader:name?.txt", "text/plain", "content")
        );

        assertThat(response.originalFilename()).isEqualTo("evil__header_name_.txt");
    }

    @Test
    void uploadDeletesStoredFileWhenInsertDoesNotPersistAttachmentRecord() throws Exception {
        AttachmentMapper mapper = mock(AttachmentMapper.class);
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(mapper.insert(any(AttachmentEntity.class))).thenReturn(0);
        AttachmentService service = newService(mapper, auditMetadataFactory);

        assertThatThrownBy(() -> service.upload(
                "sales_order",
                9001L,
                "SO-STREAM-004",
                new InputStreamOnlyMultipartFile("orphan.txt", "text/plain", "content")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("保存附件记录失败");

        try (var files = Files.walk(storageRoot)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void listScopesAttachmentQueryByCompanyAndAccountBook() {
        AttachmentMapper mapper = mock(AttachmentMapper.class);
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<AttachmentEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            return page;
        });
        AttachmentService service = newService(mapper, auditMetadataFactory);

        service.list(new AttachmentPageQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<AttachmentEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag");
    }

    @Test
    void countActiveScopesConfiguredGateByTenantAndBusiness() {
        AttachmentMapper mapper = mock(AttachmentMapper.class);
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(mapper.selectCount(any())).thenReturn(2L);
        AttachmentService service = newService(mapper, auditMetadataFactory);

        long count = service.countActive(" expense ", 9001L);

        assertThat(count).isEqualTo(2L);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<AttachmentEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectCount(wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("company_id")
                .contains("account_book_id")
                .contains("business_type")
                .contains("business_id")
                .contains("deleted_flag")
                .contains("status");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), "EXPENSE", 9001L, 0, "ACTIVE");
    }

    @Test
    void requireIfConfiguredRejectsWhenActiveAttachmentCountIsBelowMinimum() {
        AttachmentMapper mapper = mock(AttachmentMapper.class);
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(mapper.selectCount(any())).thenReturn(1L);
        AttachmentProperties properties = new AttachmentProperties(
                storageRoot.toString(), 1024L, "SALES_ORDER", 2);
        AttachmentService service = newService(mapper, auditMetadataFactory, properties);

        assertThatThrownBy(() -> service.requireIfConfigured("sales_order", 9001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少上传 2 个附件，当前 1 个");
    }

    @Test
    void requireForBusinessRejectsAttachmentOwnedByAnotherBusinessRecord() {
        AttachmentEntity entity = new AttachmentEntity();
        entity.setId(1009L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setBusinessType("SALES_ORDER");
        entity.setBusinessId(9001L);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);

        AttachmentMapper mapper = mock(AttachmentMapper.class);
        when(mapper.selectById(1009L)).thenReturn(entity);
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        AttachmentService service = newService(mapper, auditMetadataFactory);

        assertThatThrownBy(() -> service.requireForBusiness(1009L, "SALES_ORDER", 9002L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("附件不存在");
    }

    @Test
    void downloadReturnsFileResourceInsteadOfByteArrayResource() throws Exception {
        Path storedFile = storageRoot.resolve(Path.of("1", "1", "2026-06-02", "download.txt"));
        Files.createDirectories(storedFile.getParent());
        Files.writeString(storedFile, "download-content", StandardCharsets.UTF_8);

        AttachmentEntity entity = new AttachmentEntity();
        entity.setId(1002L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setOriginalFilename("download.txt");
        entity.setStoragePath("1/1/2026-06-02/download.txt");
        entity.setContentType(MediaType.TEXT_PLAIN_VALUE);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);

        AttachmentMapper mapper = mock(AttachmentMapper.class);
        when(mapper.selectById(1002L)).thenReturn(entity);
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        AttachmentService service = newService(mapper, auditMetadataFactory);

        ResponseEntity<? extends Resource> response = service.download(1002L);

        assertThat(response.getBody()).isNotInstanceOf(ByteArrayResource.class);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().contentLength()).isEqualTo(16L);
        try (InputStream inputStream = response.getBody().getInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("download-content");
        }
    }

    @Test
    void downloadSanitizesLegacyStoredFilenameBeforeBuildingContentDisposition() throws Exception {
        Path storedFile = storageRoot.resolve(Path.of("1", "1", "2026-06-02", "legacy.txt"));
        Files.createDirectories(storedFile.getParent());
        Files.writeString(storedFile, "download-content", StandardCharsets.UTF_8);

        AttachmentEntity entity = new AttachmentEntity();
        entity.setId(1006L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setOriginalFilename("..\\legacy\r\nheader:name?.txt");
        entity.setStoragePath("1/1/2026-06-02/legacy.txt");
        entity.setContentType(MediaType.TEXT_PLAIN_VALUE);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);

        AttachmentMapper mapper = mock(AttachmentMapper.class);
        when(mapper.selectById(1006L)).thenReturn(entity);
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        AttachmentService service = newService(mapper, auditMetadataFactory);

        ResponseEntity<? extends Resource> response = service.download(1006L);

        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("legacy__header_name_.txt");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("filename*=UTF-8''legacy__header_name_.txt")
                .doesNotContain("\r")
                .doesNotContain("\n")
                .doesNotContain("..\\legacy")
                .doesNotContain("header:name")
                .doesNotContain("name?.txt");
    }

    @Test
    void downloadRejectsAttachmentWhenStatusIsNotActiveEvenIfDeletedFlagIsZero() throws Exception {
        Path storedFile = storageRoot.resolve(Path.of("1", "1", "2026-06-02", "deleted-status.txt"));
        Files.createDirectories(storedFile.getParent());
        Files.writeString(storedFile, "download-content", StandardCharsets.UTF_8);

        AttachmentEntity entity = new AttachmentEntity();
        entity.setId(1007L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setOriginalFilename("deleted-status.txt");
        entity.setStoragePath("1/1/2026-06-02/deleted-status.txt");
        entity.setContentType(MediaType.TEXT_PLAIN_VALUE);
        entity.setStatus("DELETED");
        entity.setDeletedFlag(0);

        AttachmentMapper mapper = mock(AttachmentMapper.class);
        when(mapper.selectById(1007L)).thenReturn(entity);
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        AttachmentService service = newService(mapper, auditMetadataFactory);

        assertThatThrownBy(() -> service.download(1007L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("附件不存在");
    }

    @Test
    void downloadRejectsAttachmentWhenStoredPathIsMissing() {
        AttachmentEntity entity = new AttachmentEntity();
        entity.setId(1008L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setOriginalFilename("missing-path.txt");
        entity.setStoragePath(null);
        entity.setContentType(MediaType.TEXT_PLAIN_VALUE);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);

        AttachmentMapper mapper = mock(AttachmentMapper.class);
        when(mapper.selectById(1008L)).thenReturn(entity);
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        AttachmentService service = newService(mapper, auditMetadataFactory);

        assertThatThrownBy(() -> service.download(1008L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("附件路径无效");
    }

    @Test
    void downloadFallsBackToOctetStreamForInvalidStoredContentType() throws Exception {
        Path storedFile = storageRoot.resolve(Path.of("1", "1", "2026-06-02", "invalid-content-type.txt"));
        Files.createDirectories(storedFile.getParent());
        Files.writeString(storedFile, "download-content", StandardCharsets.UTF_8);

        AttachmentEntity entity = new AttachmentEntity();
        entity.setId(1004L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setOriginalFilename("invalid-content-type.txt");
        entity.setStoragePath("1/1/2026-06-02/invalid-content-type.txt");
        entity.setContentType("not a media type");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);

        AttachmentMapper mapper = mock(AttachmentMapper.class);
        when(mapper.selectById(1004L)).thenReturn(entity);
        AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        AttachmentService service = newService(mapper, auditMetadataFactory);

        ResponseEntity<? extends Resource> response = service.download(1004L);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getBody()).isNotNull();
        try (InputStream inputStream = response.getBody().getInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("download-content");
        }
    }

    private String sha256(String content) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private AttachmentService newService(AttachmentMapper mapper, AuditMetadataFactory auditMetadataFactory) {
        AttachmentProperties properties = new AttachmentProperties(storageRoot.toString(), 1024L, "EXPENSE", 1);
        return newService(mapper, auditMetadataFactory, properties);
    }

    private AttachmentService newService(
            AttachmentMapper mapper,
            AuditMetadataFactory auditMetadataFactory,
            AttachmentProperties properties
    ) {
        AttachmentQueryService queryService = new AttachmentQueryService(mapper, auditMetadataFactory, properties);
        return new AttachmentService(
                mapper,
                auditMetadataFactory,
                properties,
                mock(BusinessTimelineService.class),
                CLOCK,
                queryService
        );
    }

    private static class InputStreamOnlyMultipartFile implements MultipartFile {

        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        private InputStreamOnlyMultipartFile(String originalFilename, String contentType, String content) {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() throws IOException {
            throw new IOException("getBytes should not be called");
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), content);
        }

        @Override
        public void transferTo(Path dest) throws IOException, IllegalStateException {
            Files.write(dest, content);
        }
    }
}
