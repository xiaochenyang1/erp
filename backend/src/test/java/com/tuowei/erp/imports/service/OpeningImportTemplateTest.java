package com.tuowei.erp.imports.service;

import com.tuowei.erp.common.config.ImportProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpeningImportTemplateTest {

    private final CsvImportParser parser = new CsvImportParser(ImportProperties.defaults());
    private final ImportTemplateRegistry registry = new ImportTemplateRegistry();

    @Test
    void openingReceivableAcceptsCurrentTemplateAndLegacyHeader() {
        String current = registry.csvTemplate(ImportConstants.OPENING_RECEIVABLE);
        String legacy = """
                customer_code,receivable_no,biz_date,original_amount,settled_amount,remark
                C001,AR-OPEN-001,2026-01-01,500.00,0,期初应收示例
                """;

        assertThat(parser.parseAccepted(csv(current), registry.acceptedHeaders(ImportConstants.OPENING_RECEIVABLE))
                .rows()).hasSize(1);
        assertThat(parser.parseAccepted(csv(legacy), registry.acceptedHeaders(ImportConstants.OPENING_RECEIVABLE))
                .rows().get(0).values()).doesNotContainKey("currency_code");
        assertThatThrownBy(() -> parser.parseAccepted(
                csv("customer_code,remark\nC001,x\n"),
                registry.acceptedHeaders(ImportConstants.OPENING_RECEIVABLE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CSV表头不匹配，请使用系统提供的模板");
    }

    @Test
    void openingPayableTemplateCarriesCurrencyColumns() {
        assertThat(registry.headers(ImportConstants.OPENING_PAYABLE))
                .contains("currency_code", "exchange_rate");
        assertThat(registry.acceptedHeaders(ImportConstants.PRODUCT)).hasSize(1);
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "opening.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
