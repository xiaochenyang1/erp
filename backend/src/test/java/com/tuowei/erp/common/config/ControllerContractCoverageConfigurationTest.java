package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerContractCoverageConfigurationTest {

    private static final Set<String> AUTHENTICATED_OR_PUBLIC_ENDPOINTS_WITHOUT_METHOD_PERMISSION = Set.of(
            "system/auth/controller/AuthController.java:@PostMapping(\"/login\")",
            "system/auth/controller/AuthController.java:@PostMapping(\"/refresh\")",
            "system/auth/controller/AuthController.java:@PostMapping(\"/logout\")",
            "system/auth/controller/AuthController.java:@PostMapping(\"/change-password\")",
            "system/controller/HealthController.java:@GetMapping(\"/health\")"
    );

    @Test
    void highRiskSystemControllersHaveContractTests() {
        assertThat(Path.of("src", "test", "java", "com", "tuowei", "erp",
                "system", "user", "UserControllerTest.java"))
                .as("UserController changes account, role and password state; it must have MockMvc contract coverage")
                .exists()
                .isRegularFile();
        assertThat(Path.of("src", "test", "java", "com", "tuowei", "erp",
                "system", "role", "RoleControllerTest.java"))
                .as("RoleController changes permission state; it must have MockMvc contract coverage")
                .exists()
                .isRegularFile();
        assertThat(Path.of("src", "test", "java", "com", "tuowei", "erp",
                "system", "menu", "MenuControllerTest.java"))
                .as("MenuController changes permission menu state; it must have MockMvc contract coverage")
                .exists()
                .isRegularFile();
        assertThat(Path.of("src", "test", "java", "com", "tuowei", "erp",
                "system", "config", "SequenceRuleControllerTest.java"))
                .as("SequenceRuleController changes document number rules; it must have MockMvc contract coverage")
                .exists()
                .isRegularFile();
        assertThat(Path.of("src", "test", "java", "com", "tuowei", "erp",
                "system", "config", "SystemConfigControllerTest.java"))
                .as("SystemConfigController changes global system parameters; it must have MockMvc contract coverage")
                .exists()
                .isRegularFile();
        assertThat(Path.of("src", "test", "java", "com", "tuowei", "erp",
                "system", "attachment", "AttachmentControllerSecurityContractTest.java"))
                .as("AttachmentController streams user files; it must have permission and binding contract coverage")
                .exists()
                .isRegularFile();
        assertThat(Path.of("src", "test", "java", "com", "tuowei", "erp",
                "report", "ReportControllerSecurityContractTest.java"))
                .as("ReportController exposes financial and inventory reports; it must have permission and export contract coverage")
                .exists()
                .isRegularFile();
        assertThat(Path.of("src", "test", "java", "com", "tuowei", "erp",
                "finance", "FinanceSettlementPermissionContractTest.java"))
                .as("PaymentController and ReceiptController cancel posted cash documents; they must have dedicated permission coverage")
                .exists()
                .isRegularFile();
        assertThat(Path.of("src", "test", "java", "com", "tuowei", "erp",
                "imports", "ImportControllerSecurityContractTest.java"))
                .as("ImportController accepts user CSV files and commits data; it must have permission and multipart contract coverage")
                .exists()
                .isRegularFile();
        assertThat(Path.of("src", "test", "java", "com", "tuowei", "erp",
                "system", "auth", "AuthControllerContractTest.java"))
                .as("AuthController issues and revokes tokens; it must have validation and delegation contract coverage")
                .exists()
                .isRegularFile();
        assertThat(Path.of("src", "test", "java", "com", "tuowei", "erp",
                "system", "auth", "UserSessionControllerContractTest.java"))
                .as("UserSessionController revokes login sessions; it must have permission and query contract coverage")
                .exists()
                .isRegularFile();
    }

    @Test
    void controllerMethodsDeclarePermissionsUnlessExplicitlyPublicOrAuthenticatedOnly() throws IOException {
        Path controllerRoot = Path.of("src", "main", "java", "com", "tuowei", "erp");
        List<String> missingPermissions = new ArrayList<>();
        try (var paths = Files.walk(controllerRoot)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith("Controller.java"))
                    .toList()) {
                collectMissingMethodPermissions(controllerRoot, path, missingPermissions);
            }
        }

        assertThat(missingPermissions)
                .as("控制器方法必须显式声明权限；公开或仅需登录的接口必须进入白名单")
                .isEmpty();
    }

    private void collectMissingMethodPermissions(Path controllerRoot, Path path, List<String> missingPermissions) throws IOException {
        List<String> lines = Files.readAllLines(path);
        String relativePath = controllerRoot.relativize(path).toString().replace('\\', '/');
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (!isMethodMapping(line)) {
                continue;
            }
            String endpoint = relativePath + ":" + line;
            if (!hasNearbyPreAuthorize(lines, index)
                    && !AUTHENTICATED_OR_PUBLIC_ENDPOINTS_WITHOUT_METHOD_PERMISSION.contains(endpoint)) {
                missingPermissions.add(endpoint);
            }
        }
    }

    private boolean isMethodMapping(String line) {
        return line.startsWith("@GetMapping")
                || line.startsWith("@PostMapping")
                || line.startsWith("@PutMapping")
                || line.startsWith("@DeleteMapping")
                || line.startsWith("@PatchMapping");
    }

    private boolean hasNearbyPreAuthorize(List<String> lines, int mappingIndex) {
        for (int index = Math.max(0, mappingIndex - 8); index < mappingIndex; index++) {
            if (lines.get(index).contains("@PreAuthorize")) {
                return true;
            }
        }
        return false;
    }
}
