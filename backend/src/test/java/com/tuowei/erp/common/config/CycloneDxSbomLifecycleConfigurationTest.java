package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CycloneDxSbomLifecycleConfigurationTest {

    @Test
    void pomDisablesInheritedCycloneDxDefaultExecution() throws Exception {
        Element plugin = cycloneDxPlugin();

        Element defaultExecution = execution(plugin, "default");

        assertThat(defaultExecution).isNotNull();
        assertThat(text(defaultExecution, "phase")).isEqualTo("none");
        assertThat(goals(defaultExecution)).doesNotContain("makeAggregateBom");
    }

    @Test
    void pomGeneratesCycloneDxSbomOnlyDuringPackageLifecycle() throws Exception {
        Element plugin = cycloneDxPlugin();

        Element generateSbomExecution = execution(plugin, "generate-sbom");

        assertThat(generateSbomExecution).isNotNull();
        assertThat(text(generateSbomExecution, "phase")).isEqualTo("package");
        assertThat(goals(generateSbomExecution)).containsExactly("makeAggregateBom");
    }

    @Test
    void pomGeneratesEmbeddedCycloneDxSbomBeforeJarPackaging() throws Exception {
        Element plugin = cycloneDxPlugin();

        Element embeddedSbomExecution = execution(plugin, "generate-embedded-sbom");

        assertThat(embeddedSbomExecution).isNotNull();
        assertThat(text(embeddedSbomExecution, "phase")).isEqualTo("prepare-package");
        assertThat(goals(embeddedSbomExecution)).containsExactly("makeAggregateBom");
        assertThat(text(embeddedSbomExecution, "outputDirectory"))
                .isEqualTo("${project.build.outputDirectory}/META-INF/sbom");
        assertThat(text(embeddedSbomExecution, "outputName")).isEqualTo("application.cdx");
        assertThat(text(embeddedSbomExecution, "outputFormat")).isEqualTo("json");
        assertThat(text(embeddedSbomExecution, "skipAttach")).isEqualTo("true");
    }

    private Element cycloneDxPlugin()
            throws IOException, ParserConfigurationException, SAXException {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(Path.of("pom.xml").toFile());
        NodeList plugins = document.getElementsByTagName("plugin");

        for (int i = 0; i < plugins.getLength(); i++) {
            Element plugin = (Element) plugins.item(i);
            if ("org.cyclonedx".equals(text(plugin, "groupId"))
                    && "cyclonedx-maven-plugin".equals(text(plugin, "artifactId"))) {
                return plugin;
            }
        }

        throw new AssertionError("CycloneDX Maven plugin is not declared in pom.xml");
    }

    private Element execution(Element plugin, String id) {
        NodeList executions = plugin.getElementsByTagName("execution");
        for (int i = 0; i < executions.getLength(); i++) {
            Element execution = (Element) executions.item(i);
            if (id.equals(text(execution, "id"))) {
                return execution;
            }
        }
        return null;
    }

    private List<String> goals(Element execution) {
        NodeList goals = execution.getElementsByTagName("goal");
        List<String> values = new ArrayList<>();
        for (int i = 0; i < goals.getLength(); i++) {
            values.add(goals.item(i).getTextContent().trim());
        }
        return values;
    }

    private String text(Element element, String tagName) {
        NodeList elements = element.getElementsByTagName(tagName);
        if (elements.getLength() == 0) {
            return "";
        }
        return elements.item(0).getTextContent().trim();
    }
}
