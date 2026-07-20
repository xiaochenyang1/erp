package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestcontainersExecutionConfigurationTest {

    private static final String TESTCONTAINERS_TAG = "testcontainers";

    private static final List<Path> TESTCONTAINERS_TESTS = List.of(
            Path.of("src", "test", "java", "com", "tuowei", "erp", "db", "MysqlFlywayMigrationSmokeTest.java"),
            Path.of("src", "test", "java", "com", "tuowei", "erp", "inventory", "stock",
                    "InventoryPostingMysqlIntegrationTest.java"),
            Path.of("src", "test", "java", "com", "tuowei", "erp", "system", "auth",
                    "LoginRateLimiterRedisIntegrationTest.java")
    );

    @Test
    void testcontainersTestsAreTaggedForMavenSelection() throws IOException {
        for (Path testClass : TESTCONTAINERS_TESTS) {
            String content = java.nio.file.Files.readString(testClass, StandardCharsets.UTF_8);

            assertThat(content)
                    .contains("import org.junit.jupiter.api.Tag;")
                    .contains("@Tag(\"" + TESTCONTAINERS_TAG + "\")");
        }
    }

    @Test
    void surefireExcludesTestcontainersGroupByDefault()
            throws IOException, ParserConfigurationException, SAXException {
        Document document = pom();
        Element surefire = plugin(document, "org.apache.maven.plugins", "maven-surefire-plugin");

        assertThat(property(document, "surefire.excludedGroups")).isEqualTo(TESTCONTAINERS_TAG);
        assertThat(surefire).isNotNull();
        assertThat(text(surefire, "excludedGroups")).isEqualTo("${surefire.excludedGroups}");
    }

    @Test
    void testcontainersProfileClearsDefaultGroupExclusion()
            throws IOException, ParserConfigurationException, SAXException {
        Element profile = profile(pom(), "testcontainers");

        assertThat(profile).isNotNull();
        assertThat(text(profile, "surefire.excludedGroups")).isEmpty();
    }

    private Document pom() throws IOException, ParserConfigurationException, SAXException {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(Path.of("pom.xml").toFile());
    }

    private Element plugin(Document document, String groupId, String artifactId) {
        NodeList plugins = document.getElementsByTagName("plugin");
        for (int i = 0; i < plugins.getLength(); i++) {
            Element plugin = (Element) plugins.item(i);
            if (groupId.equals(text(plugin, "groupId"))
                    && artifactId.equals(text(plugin, "artifactId"))) {
                return plugin;
            }
        }
        return null;
    }

    private Element profile(Document document, String id) {
        NodeList profiles = document.getElementsByTagName("profile");
        for (int i = 0; i < profiles.getLength(); i++) {
            Element profile = (Element) profiles.item(i);
            if (id.equals(text(profile, "id"))) {
                return profile;
            }
        }
        return null;
    }

    private String property(Document document, String name) {
        NodeList values = document.getElementsByTagName(name);
        if (values.getLength() == 0) {
            return "";
        }
        return values.item(0).getTextContent().trim();
    }

    private String text(Element element, String tagName) {
        NodeList elements = element.getElementsByTagName(tagName);
        if (elements.getLength() == 0) {
            return "";
        }
        return elements.item(0).getTextContent().trim();
    }
}
