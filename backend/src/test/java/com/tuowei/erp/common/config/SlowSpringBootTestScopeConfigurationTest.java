package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SlowSpringBootTestScopeConfigurationTest {

    @Test
    void springBootTestsUseTestProfile() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src", "test", "java"))) {
            List<String> missingTestProfile = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(SlowSpringBootTestScopeConfigurationTest::hasSpringBootTestWithoutActiveProfile)
                    .map(Path::toString)
                    .toList();

            assertThat(missingTestProfile)
                    .as("@SpringBootTest contexts must load application-test.yml so background schedulers stay disabled.")
                    .isEmpty();
        }
    }

    @Test
    void idempotencyFilterTestUsesStandaloneMockMvcInsteadOfFullSpringBootContext() throws IOException {
        String content = Files.readString(
                Path.of("src", "test", "java", "com", "tuowei", "erp", "common", "idempotency",
                        "IdempotencyFilterIntegrationTest.java"),
                StandardCharsets.UTF_8);

        assertThat(content)
                .doesNotContain("@SpringBootTest")
                .doesNotContain("@AutoConfigureMockMvc")
                .contains("MockMvcBuilders.standaloneSetup");
    }

    @Test
    void flywayMigrationSmokeTestRunsFlywayDirectlyInsteadOfFullSpringBootContext() throws IOException {
        String content = Files.readString(
                Path.of("src", "test", "java", "com", "tuowei", "erp", "db", "FlywayMigrationSmokeTest.java"),
                StandardCharsets.UTF_8);

        assertThat(content)
                .doesNotContain("@SpringBootTest")
                .doesNotContain("@ActiveProfiles")
                .contains("Flyway.configure()")
                .contains("DriverManagerDataSource");
    }

    @Test
    void financeSettlementCancelTestAvoidsWebMvcContext() throws IOException {
        String content = Files.readString(
                Path.of("src", "test", "java", "com", "tuowei", "erp", "finance",
                        "FinanceSettlementCancelControllerTest.java"),
                StandardCharsets.UTF_8);

        assertThat(content)
                .doesNotContain("@AutoConfigureMockMvc")
                .doesNotContain("MockMvc")
                .contains("webEnvironment = SpringBootTest.WebEnvironment.NONE")
                .contains("PaymentService")
                .contains("ReceiptService");
    }

    @Test
    void salesCostPostingTestAvoidsWebMvcContext() throws IOException {
        String content = Files.readString(
                Path.of("src", "test", "java", "com", "tuowei", "erp", "finance",
                        "SalesCostPostingTest.java"),
                StandardCharsets.UTF_8);

        assertThat(content)
                .doesNotContain("@SpringBootTest\n")
                .contains("webEnvironment = SpringBootTest.WebEnvironment.NONE")
                .contains("FinancePostingService");
    }

    @Test
    void initialImportEntrypointTestAvoidsWebMvcContext() throws IOException {
        String content = Files.readString(
                Path.of("src", "test", "java", "com", "tuowei", "erp", "imports",
                        "InitialImportControllerTest.java"),
                StandardCharsets.UTF_8);

        assertThat(content)
                .doesNotContain("@AutoConfigureMockMvc")
                .doesNotContain("MockMvc")
                .contains("webEnvironment = SpringBootTest.WebEnvironment.NONE")
                .contains("ImportJobService");
    }

    @Test
    void inventoryLotDomainTestAvoidsWebMvcContext() throws IOException {
        String content = Files.readString(
                Path.of("src", "test", "java", "com", "tuowei", "erp", "inventory",
                        "InventoryLotDomainIntegrationTest.java"),
                StandardCharsets.UTF_8);

        assertThat(content)
                .doesNotContain("@SpringBootTest\n")
                .contains("webEnvironment = SpringBootTest.WebEnvironment.NONE")
                .contains("InventoryTransferService")
                .contains("PurchaseReturnService")
                .contains("SalesReturnService");
    }

    @Test
    void payableEntrypointTestAvoidsWebMvcContext() throws IOException {
        String content = Files.readString(
                Path.of("src", "test", "java", "com", "tuowei", "erp", "finance", "payable",
                        "PayableControllerTest.java"),
                StandardCharsets.UTF_8);

        assertThat(content)
                .doesNotContain("@AutoConfigureMockMvc")
                .doesNotContain("MockMvc")
                .contains("webEnvironment = SpringBootTest.WebEnvironment.NONE")
                .contains("PayableQueryService");
    }

    @Test
    void receivableEntrypointTestAvoidsWebMvcContext() throws IOException {
        String content = Files.readString(
                Path.of("src", "test", "java", "com", "tuowei", "erp", "finance", "receivable",
                        "ReceivableControllerTest.java"),
                StandardCharsets.UTF_8);

        assertThat(content)
                .doesNotContain("@AutoConfigureMockMvc")
                .doesNotContain("MockMvc")
                .contains("webEnvironment = SpringBootTest.WebEnvironment.NONE")
                .contains("ReceivableQueryService");
    }

    @Test
    void accountPeriodEntrypointTestAvoidsWebMvcContext() throws IOException {
        String content = Files.readString(
                Path.of("src", "test", "java", "com", "tuowei", "erp", "finance", "period",
                        "AccountPeriodControllerTest.java"),
                StandardCharsets.UTF_8);

        assertThat(content)
                .doesNotContain("@AutoConfigureMockMvc")
                .doesNotContain("MockMvc")
                .contains("webEnvironment = SpringBootTest.WebEnvironment.NONE")
                .contains("AccountPeriodService")
                .contains("AccountPeriodCloseChecker");
    }

    @Test
    void accountPeriodGuardEntrypointTestAvoidsWebMvcContext() throws IOException {
        String content = Files.readString(
                Path.of("src", "test", "java", "com", "tuowei", "erp", "finance", "period",
                        "AccountPeriodGuardIntegrationTest.java"),
                StandardCharsets.UTF_8);

        assertThat(content)
                .doesNotContain("@AutoConfigureMockMvc")
                .doesNotContain("MockMvc")
                .contains("webEnvironment = SpringBootTest.WebEnvironment.NONE")
                .contains("PaymentService")
                .contains("ReceiptService")
                .contains("SalesDeliveryService")
                .contains("PurchaseReceiptService");
    }

    @Test
    void inventoryFinanceReconciliationServiceTestAvoidsWebMvcContext() throws IOException {
        String content = Files.readString(
                Path.of("src", "test", "java", "com", "tuowei", "erp", "finance", "period",
                        "InventoryFinanceReconciliationServiceTest.java"),
                StandardCharsets.UTF_8);

        assertThat(content)
                .doesNotContain("@SpringBootTest\n")
                .contains("webEnvironment = SpringBootTest.WebEnvironment.NONE")
                .contains("InventoryFinanceReconciliationService");
    }

    private static boolean hasSpringBootTestWithoutActiveProfile(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return content.contains("@SpringBootTest") && !content.contains("@ActiveProfiles");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }
}
