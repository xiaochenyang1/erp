package com.tuowei.erp.system.attachment.service;

import com.tuowei.erp.common.config.AttachmentProperties;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.system.attachment.model.AttachmentEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

/** Filesystem adapter for attachment storage: path resolution, streaming writes, checksums and download responses. */
@Service
public class AttachmentStorageService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final AttachmentProperties attachmentProperties;
    private final Clock clock;

    public AttachmentStorageService(AttachmentProperties attachmentProperties, Clock clock) {
        this.attachmentProperties = attachmentProperties;
        this.clock = clock;
    }

    StoredFile writeFile(Path target, MultipartFile file) {
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long totalBytes = 0;
            byte[] buffer = new byte[8192];
            try (DigestInputStream inputStream = new DigestInputStream(file.getInputStream(), digest);
                 OutputStream outputStream = Files.newOutputStream(target)) {
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    totalBytes += read;
                    if (totalBytes > attachmentProperties.maxFileSizeBytes()) {
                        throw new IllegalArgumentException("附件大小超过限制");
                    }
                    outputStream.write(buffer, 0, read);
                }
            }
            return new StoredFile(totalBytes, URL_ENCODER.encodeToString(digest.digest()));
        } catch (IOException ex) {
            throw new UncheckedIOException("保存附件失败", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("计算附件摘要失败", ex);
        }
    }

    ResponseEntity<Resource> buildDownloadResponse(AttachmentEntity entity) {
        Path path = resolveStoragePath(entity.getStoragePath());
        long fileSize = requireFileSize(path);
        String downloadFilename = normalizeFilename(entity.getOriginalFilename());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resolveContentType(entity.getContentType())))
                .contentLength(fileSize)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(downloadFilename, StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(path));
    }

    private long requireFileSize(Path path) {
        try {
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                throw new IllegalArgumentException("附件文件不存在");
            }
            return Files.size(path);
        } catch (IOException ex) {
            throw new UncheckedIOException("读取附件失败", ex);
        }
    }

    void deleteFileQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    Path resolveStoragePath(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new IllegalArgumentException("附件路径无效");
        }
        Path root = Path.of(attachmentProperties.storageRoot()).toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("附件路径无效");
        }
        return target;
    }

    String buildRelativePath(AuditMetadata audit, String originalFilename) {
        String suffix = SafeFilename.extensionOf(originalFilename, ".bin", 32);
        LocalDate today = LocalDate.now(clock);
        return Path.of(
                audit.companyId().toString(),
                audit.accountBookId().toString(),
                today.toString(),
                UUID.randomUUID() + suffix
        ).toString().replace('\\', '/');
    }

    String normalizeFilename(String filename) {
        return SafeFilename.normalize(filename, "attachment", 255);
    }

    String resolveContentType(MultipartFile file) {
        return resolveContentType(file.getContentType());
    }

    String resolveContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            return MediaType.parseMediaType(contentType.trim()).toString();
        } catch (InvalidMediaTypeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    record StoredFile(long fileSize, String checksumSha256) {
    }
}
