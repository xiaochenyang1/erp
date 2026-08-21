package com.tuowei.erp.common.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPrincipalCacheInvalidationConfigurationTest {

    @Test
    void securityMutationServicesEvictPrincipalCache() throws IOException {
        String userCommandService = readSource("system", "user", "service", "UserCommandService.java");
        String roleService = readSource("system", "role", "service", "RoleCommandService.java");
        String menuCommandService = readSource("system", "menu", "service", "MenuCommandService.java");
        String authService = readSource("system", "auth", "service", "AuthService.java");

        assertThat(userCommandService)
                .contains("SecurityPrincipalCache principalCache")
                .contains("principalCache.evictUser(id);")
                .contains("principalCache.evictUser(userId);");

        assertThat(roleService)
                .contains("SecurityPrincipalCache principalCache")
                .contains("principalCache.evictAll();");

        assertThat(menuCommandService)
                .contains("SecurityPrincipalCache principalCache")
                .contains("principalCache.evictAll();");

        assertThat(authService)
                .contains("SecurityPrincipalCache principalCache")
                .contains("principalCache.evictUser(principal.userId());");
    }

    private static String readSource(String... pathParts) throws IOException {
        Path path = Path.of("src", "main", "java", "com", "tuowei", "erp");
        for (String pathPart : pathParts) {
            path = path.resolve(pathPart);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
