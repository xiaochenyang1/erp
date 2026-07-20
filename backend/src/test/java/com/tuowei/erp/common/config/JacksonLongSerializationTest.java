package com.tuowei.erp.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "erp.security.jwt.secret=0123456789abcdef0123456789abcdef"
        }
)
@ActiveProfiles("test")
class JacksonLongSerializationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serializesLongValuesAsStringsForJavascriptClients() throws Exception {
        String json = objectMapper.writeValueAsString(new LongPayload(
                2_072_561_615_605_100_546L,
                2_072_561_615_605_100_547L,
                12L
        ));

        assertThat(json)
                .contains("\"id\":\"2072561615605100546\"")
                .contains("\"primitiveId\":\"2072561615605100547\"")
                .contains("\"count\":12");
    }

    private record LongPayload(Long id, long primitiveId, Long count) {
    }
}
