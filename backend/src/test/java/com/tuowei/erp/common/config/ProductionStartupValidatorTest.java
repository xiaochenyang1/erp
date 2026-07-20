package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionStartupValidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void prodRejectsUnexpectedMessageExposure() {
        MockEnvironment environment = prodEnvironment(tempDir.resolve("attachments"))
                .withProperty("erp.error.expose-unexpected-message", "true");

        ProductionStartupValidator validator = new ProductionStartupValidator(environment);

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境禁止暴露未知异常明文");
    }

    @Test
    void prodRejectsAttachmentStorageRootWhenItIsAFile() throws Exception {
        Path filePath = tempDir.resolve("attachments-as-file");
        Files.writeString(filePath, "not a directory");
        MockEnvironment environment = prodEnvironment(filePath)
                .withProperty("erp.error.expose-unexpected-message", "false");

        ProductionStartupValidator validator = new ProductionStartupValidator(environment);

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境附件存储目录不是有效目录");
    }

    @Test
    void prodRejectsCorsAllowedOriginsWithTrailingEmptyOrigin() {
        MockEnvironment environment = prodEnvironment(tempDir.resolve("attachments"))
                .withProperty("erp.security.cors.allowed-origins", "https://erp.example.com,");

        ProductionStartupValidator validator = new ProductionStartupValidator(environment);

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境 ERP_CORS_ALLOWED_ORIGINS 包含空 Origin");
    }

    @Test
    void prodRejectsUnresolvedEnvironmentPlaceholders() {
        MockEnvironment environment = prodEnvironment(tempDir.resolve("attachments"))
                .withProperty("spring.datasource.password", "${ERP_DATASOURCE_PASSWORD}");

        ProductionStartupValidator validator = new ProductionStartupValidator(environment);

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境 ERP_DATASOURCE_PASSWORD 不能使用占位符");
    }

    @Test
    void prodRejectsDatasourceUrlPlaceholder() {
        MockEnvironment environment = prodEnvironment(tempDir.resolve("attachments"))
                .withProperty("spring.datasource.url", "CHANGE_ME_DATASOURCE_URL");

        ProductionStartupValidator validator = new ProductionStartupValidator(environment);

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境 ERP_DATASOURCE_URL 不能使用占位符");
    }

    @Test
    void prodAcceptsWritableAttachmentStorageRoot() {
        Path storageRoot = tempDir.resolve("nested").resolve("attachments");
        MockEnvironment environment = prodEnvironment(storageRoot)
                .withProperty("erp.error.expose-unexpected-message", "false");

        ProductionStartupValidator validator = new ProductionStartupValidator(environment);

        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }

    private MockEnvironment prodEnvironment(Path attachmentStorageRoot) {
        return new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("spring.datasource.url", "jdbc:mysql://mysql:3306/erp")
                .withProperty("spring.datasource.username", "erp_user")
                .withProperty("spring.datasource.password", "strong-db-password")
                .withProperty("spring.data.redis.host", "redis")
                .withProperty("spring.data.redis.password", "strong-redis-password")
                .withProperty("erp.security.cors.allowed-origins", "https://erp.example.com")
                .withProperty("erp.security.jwt.secret", "prod-secret-prod-secret-prod-secret-prod-secret-prod-secret")
                .withProperty("erp.security.public-api-docs-enabled", "false")
                .withProperty("erp.attachment.storage-root", attachmentStorageRoot.toString());
    }
}
