package com.tuowei.erp.common.config;

import com.mysql.cj.jdbc.JdbcPropertySetImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ProductionJdbcEncodingTest {

    @ParameterizedTest
    @ValueSource(strings = {".env.prod.example", "Dockerfile", "Dockerfile.prebuilt", "src/main/resources/application-dev.yml"})
    void connectorAcceptsProductionTemplateEncodingBeforeOpeningConnection(String file) throws Exception {
        var match = Pattern.compile("characterEncoding=([^&]+)&").matcher(Files.readString(Path.of(file)));
        assertThat(match.find()).as("JDBC encoding in %s", file).isTrue();
        Properties properties = new Properties();
        properties.setProperty("characterEncoding", match.group(1));

        // This is the same driver initialization that rejected utf8mb4 before
        // a socket was opened during the real production startup rehearsal.
        assertThatCode(() -> new JdbcPropertySetImpl().initializeProperties(properties))
                .doesNotThrowAnyException();
    }
}
