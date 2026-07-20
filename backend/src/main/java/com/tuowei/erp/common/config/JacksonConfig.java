package com.tuowei.erp.common.config;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer identifierLongToStringSerializerCustomizer() {
        return builder -> builder.postConfigurer(objectMapper -> objectMapper.setSerializerFactory(
                objectMapper.getSerializerFactory().withSerializerModifier(new IdentifierLongSerializerModifier())
        ));
    }

    private static class IdentifierLongSerializerModifier extends BeanSerializerModifier {

        @Override
        public List<BeanPropertyWriter> changeProperties(
                SerializationConfig config,
                BeanDescription beanDesc,
                List<BeanPropertyWriter> beanProperties
        ) {
            for (BeanPropertyWriter writer : beanProperties) {
                if (isLong(writer) && isIdentifierName(writer.getName())) {
                    writer.assignSerializer(ToStringSerializer.instance);
                }
            }
            return beanProperties;
        }

        private boolean isLong(BeanPropertyWriter writer) {
            Class<?> rawClass = writer.getType().getRawClass();
            return rawClass == Long.class || rawClass == Long.TYPE;
        }

        private boolean isIdentifierName(String name) {
            return "id".equals(name) || name.endsWith("Id");
        }
    }
}
