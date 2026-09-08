package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps runtime knobs discoverable: an {@code erp.*} property that only exists
 * as an inline {@code @Value} default is invisible to operators, so every
 * property the code reads must also be declared in {@code application.yml}.
 */
class RuntimePropertyDeclarationConfigurationTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(erp\\.[A-Za-z0-9._-]+)");
    private static final Pattern CONDITIONAL_ON_PROPERTY = Pattern.compile(
            "@ConditionalOnProperty\\(\\s*prefix\\s*=\\s*\"(erp\\.[A-Za-z0-9._-]+)\"\\s*,\\s*name\\s*=\\s*\"([A-Za-z0-9._-]+)\"");

    @Test
    void everyErpPropertyReadByTheCodeIsDeclaredInApplicationYaml() throws IOException {
        Set<String> declared = declaredPropertyNames(Path.of("src", "main", "resources", "application.yml"));
        Set<String> referenced = referencedPropertyNames();

        assertThat(referenced).isNotEmpty();
        assertThat(new TreeSet<>(referenced))
                .withFailMessage("这些 erp.* 配置项只有代码里的内联默认值，application.yml 未声明：%s",
                        undeclared(referenced, declared))
                .allMatch(declared::contains);
    }

    @Test
    void schedulerTogglesAndTuningKnobsStayDeclaredTogether() throws IOException {
        Set<String> schedulerKnobs = new LinkedHashSet<>(List.of(
                "erp.exception-rule.scheduler.enabled",
                "erp.exception-rule.scheduler.fixed-delay-ms",
                "erp.exception-rule.scheduler.initial-delay-ms",
                "erp.exception-rule.scheduler.lease-enabled",
                "erp.exception-rule.scheduler.lease-ttl-seconds",
                "erp.exception-rule.scheduler.system-user-id",
                "erp.exception-rule.scheduler.system-company-id",
                "erp.exception-rule.scheduler.system-account-book-id",
                "erp.contract-alert.scheduler.enabled",
                "erp.contract-alert.scheduler.fixed-delay-ms",
                "erp.contract-alert.scheduler.initial-delay-ms",
                "erp.contract-alert.scheduler.expiration-warning-days",
                "erp.contract-alert.scheduler.low-execution-rate",
                "erp.contract-alert.scheduler.system-user-id",
                "erp.workflow.task-timeout-hours"));

        assertThat(declaredPropertyNames(Path.of("src", "main", "resources", "application.yml")))
                .containsAll(schedulerKnobs)
                .contains("erp.security.principal-cache-ttl-seconds", "erp.security.scoped-user-cache-ttl-seconds");
        assertThat(declaredPropertyNames(Path.of("src", "main", "resources", "application-prod.yml")))
                .withFailMessage("生产档必须能单独调这些后台调度参数")
                .containsAll(schedulerKnobs);
    }

    @Test
    void testProfileDisablesEveryBackgroundScheduler() throws IOException {
        Path testProfile = Path.of("src", "test", "resources", "application-test.yml");
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(testProfile.toString(), new FileSystemResource(testProfile));

        assertThat(sources).isNotEmpty();
        for (String toggle : List.of("erp.exception-rule.scheduler.enabled", "erp.contract-alert.scheduler.enabled")) {
            assertThat(sources.stream().map(source -> source.getProperty(toggle)).filter(java.util.Objects::nonNull))
                    .withFailMessage("测试档必须关掉后台调度：%s", toggle)
                    .containsExactly(Boolean.FALSE);
        }
    }

    private static Set<String> undeclared(Set<String> referenced, Set<String> declared) {
        Set<String> missing = new TreeSet<>(referenced);
        missing.removeAll(declared);
        return missing;
    }

    private static Set<String> referencedPropertyNames() throws IOException {
        Set<String> referenced = new LinkedHashSet<>();
        try (Stream<Path> sources = Files.walk(Path.of("src", "main", "java"))) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String content = Files.readString(source, StandardCharsets.UTF_8);
                Matcher placeholder = PLACEHOLDER.matcher(content);
                while (placeholder.find()) {
                    referenced.add(placeholder.group(1));
                }
                Matcher conditional = CONDITIONAL_ON_PROPERTY.matcher(content);
                while (conditional.find()) {
                    referenced.add(conditional.group(1) + "." + conditional.group(2));
                }
            }
        }
        return referenced;
    }

    private static Set<String> declaredPropertyNames(Path yaml) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(yaml.toString(), new FileSystemResource(yaml));
        Set<String> names = new LinkedHashSet<>();
        for (PropertySource<?> source : sources) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                names.addAll(List.of(enumerable.getPropertyNames()));
            }
        }
        return names;
    }
}
