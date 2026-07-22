package com.tuowei.erp.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

class OpenApiConfigTest {

    private final OpenApiCustomizer customizer = new OpenApiConfig().identifierLongAsStringCustomizer();

    @Test
    void documentsLongIdentifiersAsStringsToMatchJacksonSerialization() {
        Schema<?> schema = new IntegerSchema().format("int64");
        OpenAPI openApi = documentWithProperty("productId", schema);

        customizer.customise(openApi);

        assertThat(schema.getType()).isEqualTo("string");
        assertThat(schema.getFormat()).isNull();
    }

    @Test
    void keepsNonIdentifierLongsNumeric() {
        Schema<?> schema = new IntegerSchema().format("int64");
        OpenAPI openApi = documentWithProperty("total", schema);

        customizer.customise(openApi);

        assertThat(schema.getType()).isEqualTo("integer");
        assertThat(schema.getFormat()).isEqualTo("int64");
    }

    @Test
    void documentsIdentifierPathParametersAsStrings() {
        Schema<?> schema = new IntegerSchema().format("int64");
        Parameter parameter = new Parameter().name("id").in("path").schema(schema);
        OpenAPI openApi = new OpenAPI().paths(new Paths().addPathItem(
                "/products/{id}", new PathItem().get(new Operation().addParametersItem(parameter))
        ));

        customizer.customise(openApi);

        assertThat(schema.getType()).isEqualTo("string");
        assertThat(schema.getFormat()).isNull();
    }

    @Test
    void handlesOpenApi31SchemasThatUseTypesSet() {
        Schema<?> schema = new Schema<>().types(Set.of("integer")).format("int64");
        OpenAPI openApi = documentWithProperty("id", schema);

        customizer.customise(openApi);

        assertThat(schema.getTypes()).containsExactly("string");
        assertThat(schema.getFormat()).isNull();
    }

    private OpenAPI documentWithProperty(String name, Schema<?> property) {
        ObjectSchema response = new ObjectSchema();
        response.addProperty(name, property);
        return new OpenAPI().components(new Components().addSchemas("Response", response));
    }
}
