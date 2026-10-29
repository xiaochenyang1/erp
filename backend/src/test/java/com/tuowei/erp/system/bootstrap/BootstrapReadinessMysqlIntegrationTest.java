package com.tuowei.erp.system.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.ErpServerApplication;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("testcontainers")
class BootstrapReadinessMysqlIntegrationTest {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    @EnabledIfSystemProperty(named = "erp.testcontainers.enabled", matches = "true")
    void readinessWaitsForAdminPasswordCommitAndKeepsProtectedEndpointsPrivate() throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
                .withDatabaseName("erp_bootstrap_readiness")
                .withUsername("erp").withPassword("erp")) {
            mysql.start();
            BootstrapGate gate = new BootstrapGate();
            AtomicInteger port = new AtomicInteger();
            SpringApplication app = new SpringApplication(ErpServerApplication.class, BlockingBootstrapConfiguration.class);
            app.setRegisterShutdownHook(false);
            app.addInitializers(context -> context.getBeanFactory().registerSingleton("bootstrapGate", gate));
            app.addListeners((ApplicationListener<WebServerInitializedEvent>) event -> port.set(event.getWebServer().getPort()));
            var executor = Executors.newSingleThreadExecutor();
            var startup = executor.submit(() -> app.run(
                    "--spring.profiles.active=local",
                    "--server.port=0",
                    "--spring.datasource.url=" + mysql.getJdbcUrl(),
                    "--spring.datasource.username=" + mysql.getUsername(),
                    "--spring.datasource.password=" + mysql.getPassword(),
                    "--erp.bootstrap.local-admin-password=BootstrapReady123",
                    "--erp.exception-rule.scheduler.enabled=false",
                    "--erp.contract-alert.scheduler.enabled=false",
                    "--logging.level.root=WARN",
                    "--mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"));
            try {
                assertThat(gate.beforeCommit.await(90, TimeUnit.SECONDS))
                        .as("bootstrap must reach its uncommitted password update").isTrue();
                String base = "http://127.0.0.1:" + port.get();

                // A real HTTP login sees the old seed password while bootstrap's transaction is open.
                assertThat(login(base, "BootstrapReady123").statusCode()).isEqualTo(401);
                HttpResponse<String> notReady = get(base, "/actuator/health/readiness");
                assertThat(notReady.statusCode()).as("startup must not advertise readiness before commit").isEqualTo(503);
                assertThat(json.readTree(notReady.body()).path("status").asText()).isEqualTo("OUT_OF_SERVICE");

                gate.allowCommit.countDown();
                ConfigurableApplicationContext context = startup.get(60, TimeUnit.SECONDS);
                assertThat(context.isActive()).isTrue();
                HttpResponse<String> ready = get(base, "/actuator/health/readiness");
                assertThat(ready.statusCode()).isEqualTo(200);
                assertThat(json.readTree(ready.body()).path("status").asText()).isEqualTo("UP");
                assertThat(json.readTree(ready.body()).has("components")).isFalse();
                HttpResponse<String> login = login(base, "BootstrapReady123");
                assertThat(login.statusCode()).isEqualTo(200);
                assertThat(json.readTree(login.body()).path("data").path("accessToken").asText()).isNotBlank();
                assertThat(login(base, "WrongPassword123").statusCode()).isEqualTo(401);
                assertThat(get(base, "/api/system/profile").statusCode()).isEqualTo(401);
                assertThat(get(base, "/actuator/prometheus").statusCode()).isEqualTo(401);
                assertThat(get(base, "/actuator/health/db").statusCode()).isEqualTo(401);
            } finally {
                gate.allowCommit.countDown();
                try {
                    startup.get(60, TimeUnit.SECONDS).close();
                } finally {
                    executor.shutdownNow();
                }
            }
        }
    }

    private HttpResponse<String> get(String base, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(String base, String password) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                        .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                                java.util.Map.of("username", "admin", "password", password)))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static class BootstrapGate {
        final CountDownLatch beforeCommit = new CountDownLatch(1);
        final CountDownLatch allowCommit = new CountDownLatch(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class BlockingBootstrapConfiguration {
        @Bean
        @Primary
        PasswordEncoder bootstrapTestPasswordEncoder(BootstrapGate gate) {
            PasswordEncoder delegate = new BCryptPasswordEncoder();
            return new PasswordEncoder() {
                @Override
                public String encode(CharSequence rawPassword) {
                    String encoded = delegate.encode(rawPassword);
                    if ("BootstrapReady123".contentEquals(rawPassword)) {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void beforeCommit(boolean readOnly) {
                                gate.beforeCommit.countDown();
                                try {
                                    if (!gate.allowCommit.await(60, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException("Timed out waiting for the bootstrap test to release commit");
                                    }
                                } catch (InterruptedException ex) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException("Bootstrap test interrupted", ex);
                                }
                            }
                        });
                    }
                    return encoded;
                }

                @Override
                public boolean matches(CharSequence rawPassword, String encodedPassword) {
                    return delegate.matches(rawPassword, encodedPassword);
                }
            };
        }
    }
}
