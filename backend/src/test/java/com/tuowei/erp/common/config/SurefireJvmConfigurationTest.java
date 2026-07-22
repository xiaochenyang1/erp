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

import static org.assertj.core.api.Assertions.assertThat;

class SurefireJvmConfigurationTest {

    @Test
    void surefireDisablesClassDataSharingForDynamicTestAgents()
            throws IOException, ParserConfigurationException, SAXException {
        Element plugin = plugin("org.apache.maven.plugins", "maven-surefire-plugin");

        assertThat(plugin).isNotNull();
        assertThat(text(plugin, "argLine")).contains("-Xshare:off");
    }

    @Test
    void surefireLoadsMockitoAsAnExplicitJavaAgent()
            throws IOException, ParserConfigurationException, SAXException {
        Element plugin = plugin("org.apache.maven.plugins", "maven-surefire-plugin");

        assertThat(plugin).isNotNull();
        assertThat(text(plugin, "argLine"))
                .contains("-javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar");
    }

    @Test
    void surefireDoesNotIgnoreTestFailures()
            throws IOException, ParserConfigurationException, SAXException {
        Element plugin = plugin("org.apache.maven.plugins", "maven-surefire-plugin");

        assertThat(plugin).isNotNull();
        assertThat(text(plugin, "testFailureIgnore"))
                .as("Maven must fail when tests fail; ignored failures create false-green builds.")
                .isNotEqualTo("true");
    }

    private Element plugin(String groupId, String artifactId)
            throws IOException, ParserConfigurationException, SAXException {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(Path.of("pom.xml").toFile());
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

    private String text(Element element, String tagName) {
        NodeList elements = element.getElementsByTagName(tagName);
        if (elements.getLength() == 0) {
            return "";
        }
        return elements.item(0).getTextContent().trim();
    }
}
