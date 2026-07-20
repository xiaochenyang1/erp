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
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestcontainersDependencyConfigurationTest {

    @Test
    void pomDeclaresTestcontainersModulesUsedByIntegrationTests()
            throws IOException, ParserConfigurationException, SAXException {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(Path.of("pom.xml").toFile());
        NodeList dependencies = document.getElementsByTagName("dependency");

        Set<String> coordinates = new HashSet<>();
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            coordinates.add(text(dependency, "groupId")
                    + ":"
                    + text(dependency, "artifactId")
                    + ":"
                    + text(dependency, "scope"));
        }

        assertThat(coordinates)
                .contains("org.testcontainers:testcontainers:test")
                .contains("org.testcontainers:mysql:test");
    }

    private String text(Element element, String tagName) {
        NodeList elements = element.getElementsByTagName(tagName);
        if (elements.getLength() == 0) {
            return "";
        }
        return elements.item(0).getTextContent().trim();
    }
}
