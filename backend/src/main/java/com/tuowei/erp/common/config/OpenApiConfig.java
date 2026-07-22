package com.tuowei.erp.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenApiCustomizer identifierLongAsStringCustomizer() {
        return openApi -> {
            if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
                openApi.getComponents().getSchemas().values().forEach(schema -> {
                    if (schema.getProperties() == null) {
                        return;
                    }
                    schema.getProperties().forEach((name, property) ->
                            makeIdentifierString(String.valueOf(name), (Schema<?>) property));
                });
            }
            if (openApi.getPaths() != null) {
                openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                    if (operation.getParameters() != null) {
                        operation.getParameters().forEach(this::makeIdentifierParameterString);
                    }
                }));
            }
        };
    }

    private void makeIdentifierParameterString(Parameter parameter) {
        if (parameter != null) {
            makeIdentifierString(parameter.getName(), parameter.getSchema());
        }
    }

    private void makeIdentifierString(String name, Schema<?> schema) {
        boolean integer = schema != null && ("integer".equals(schema.getType())
                || (schema.getTypes() != null && schema.getTypes().contains("integer")));
        if (integer && isIdentifierName(name) && "int64".equals(schema.getFormat())) {
            schema.setType("string");
            schema.setTypes(Set.of("string"));
            schema.setFormat(null);
        }
    }

    private boolean isIdentifierName(String name) {
        return name != null && ("id".equals(name) || name.endsWith("Id"));
    }
}
