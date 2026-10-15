package com.tuowei.erp.finance.currency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real HTTP binding, authorization, services, MyBatis and Flyway schema, with rollback per example. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithErpUser(authorities = {"contract:view", "contract:manage", "finance:invoice:view", "finance:invoice:manage"})
class CurrencyDocumentWorkflowTest {
    private static final long CUSTOMER = 98101401L;
    private static final long PRODUCT = 98101402L;
    private static final long RATE = 98101403L;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("""
                insert into md_customer (id, company_id, account_book_id, customer_code, customer_name,
                  settlement_method, credit_limit, status, deleted_flag, created_by, updated_by, version)
                values (?, 1, 1, 'FX-DOCUMENT-CUSTOMER', 'Currency document customer', 'MONTH_END', 0, 'ACTIVE', 0, 1, 1, 0)
                """, CUSTOMER);
        jdbc.update("""
                insert into md_product (id, company_id, account_book_id, product_code, product_name, product_type,
                  category_name, specification, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag, created_by, updated_by, version)
                values (?, 1, 1, 'FX-DOCUMENT-PRODUCT', 'Currency document product', 'GOODS',
                  'Currency test', 'test', 'pcs', 100, 100, 0, 'ACTIVE', 0, 1, 1, 0)
                """, PRODUCT);
        jdbc.update("""
                insert into md_currency (id, currency_code, currency_name, status)
                select 98101404, 'USD', 'US Dollar', 'ENABLED'
                where not exists (select 1 from md_currency where currency_code = 'USD')
                """);
        jdbc.update("""
                insert into md_exchange_rate (id, company_id, account_book_id, from_currency_code,
                  to_currency_code, rate, effective_from) values (?, 1, 1, 'USD', 'CNY', 7.2, '2030-01-01')
                """, RATE);
    }

    @Test
    void contractCurrencySurvivesEditingRateChangesListingAndVersionRestore() throws Exception {
        ObjectNode request = contract();
        JsonNode created = data(post("/api/contracts"), request);
        String id = created.path("id").asText();
        assertCurrency(created, "baseTotalAmount", "USD", "7.2", "720");
        assertThat(created.path("baseCurrencyCode").asText()).isEqualTo("CNY");
        jdbc.update("update md_exchange_rate set rate = 7.5 where id = ?", RATE);
        request.put("remark", "edited without repricing");
        assertCurrency(data(put("/api/contracts/" + id), request), "baseTotalAmount", "USD", "7.2", "720");
        // Old clients that omit new optional fields must not silently convert USD into CNY.
        request.remove(List.of("currencyCode", "exchangeRate"));
        assertCurrency(data(put("/api/contracts/" + id), request), "baseTotalAmount", "USD", "7.2", "720");
        JsonNode page = data(get("/api/contracts").param("keyword", created.path("contractNo").asText()), null);
        assertCurrency(page.path("records").get(0), "baseTotalAmount", "USD", "7.2", "720");

        JsonNode versions = data(get("/api/contracts/" + id + "/versions"), null);
        JsonNode first = versions.get(versions.size() - 1);
        assertCurrency(first.path("header"), "baseTotalAmount", "USD", "7.2", "720");
        JsonNode restored = data(post("/api/contracts/" + id + "/versions/" + first.path("id").asText() + "/restore"), null);
        assertThat(restored.path("id").asText()).isNotEqualTo(id);
        assertCurrency(restored, "baseTotalAmount", "USD", "7.2", "720");
        assertThat(restored.path("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void invoiceSnapshotSurvivesEditingAndRepricesOnlyWhenExplicitlyRequested() throws Exception {
        ObjectNode request = invoice();
        String id = data(post("/api/finance/invoices"), request).path("id").asText();
        jdbc.update("update md_exchange_rate set rate = 7.5 where id = ?", RATE);
        request.put("remark", "retain saved rate");
        assertCurrency(data(put("/api/finance/invoices/" + id), request), "baseAmount", "USD", "7.2", "720");
        request.remove(List.of("currencyCode", "exchangeRate"));
        assertCurrency(data(put("/api/finance/invoices/" + id), request), "baseAmount", "USD", "7.2", "720");
        request.put("currencyCode", "USD").put("exchangeRate", new BigDecimal("7.5"));
        JsonNode repriced = data(put("/api/finance/invoices/" + id), request);
        assertCurrency(repriced, "baseAmount", "USD", "7.5", "750");
        assertThat(repriced.path("baseTaxAmount").decimalValue()).isEqualByComparingTo("75");
    }

    @Test
    void changedDateMustUseAnEffectiveRateAndRejectForgedRates() throws Exception {
        ObjectNode request = contract();
        String id = data(post("/api/contracts"), request).path("id").asText();
        jdbc.update("update md_exchange_rate set rate = 7.5 where id = ?", RATE);
        request.put("signedDate", "2030-01-03");
        mvc.perform(put("/api/contracts/" + id).contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest());
        assertCurrency(data(get("/api/contracts/" + id), null), "baseTotalAmount", "USD", "7.2", "720");
        request.put("exchangeRate", new BigDecimal("7.5"));
        assertCurrency(data(put("/api/contracts/" + id), request), "baseTotalAmount", "USD", "7.5", "750");
        JsonNode versions = data(get("/api/contracts/" + id + "/versions"), null);
        assertThat(versions.get(0).path("changedFields").toString()).contains("EXCHANGE_RATE", "BASE_TOTAL_AMOUNT");
    }

    @Test
    void preCurrencyVersionCanStillBeRestoredWithoutInventingAForeignSnapshot() throws Exception {
        JsonNode created = data(post("/api/contracts"), contract());
        String id = created.path("id").asText();
        JsonNode version = data(get("/api/contracts/" + id + "/versions"), null).get(0);
        ObjectNode legacy = ((ObjectNode) version.path("header")).deepCopy();
        legacy.remove(List.of("currencyCode", "exchangeRate", "baseTotalAmount"));
        jdbc.update("update biz_contract_version set contract_snapshot_json = ? where id = ?",
                legacy.toString(), version.path("id").asText());
        JsonNode restored = data(post("/api/contracts/" + id + "/versions/" + version.path("id").asText() + "/restore"), null);
        assertCurrency(restored, "baseTotalAmount", "CNY", "1", "100");
    }

    private ObjectNode contract() throws Exception {
        return (ObjectNode) json.readTree("""
                {"contractType":"SALES","customerId":98101401,"contractName":"Currency snapshot",
                 "signedDate":"2030-01-02","effectiveFrom":"2030-01-01","currencyCode":"USD","exchangeRate":7.2,
                 "lines":[{"productId":98101402,"quantity":1,"unitPrice":100}]}
                """);
    }

    private ObjectNode invoice() throws Exception {
        return (ObjectNode) json.readTree("""
                {"invoiceType":"OUTPUT","partnerName":"Currency customer","amount":100,"taxAmount":10,
                 "invoiceDate":"2030-01-02","currencyCode":"USD","exchangeRate":7.2}
                """);
    }

    private JsonNode data(MockHttpServletRequestBuilder request, ObjectNode body) throws Exception {
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body.toString());
        String response = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode result = json.readTree(response);
        assertThat(result.path("code").asText()).isEqualTo("0");
        return result.path("data");
    }

    private void assertCurrency(JsonNode data, String amountField, String currency, String rate, String base) {
        assertThat(data.path("currencyCode").asText()).isEqualTo(currency);
        assertThat(data.path("exchangeRate").decimalValue()).isEqualByComparingTo(rate);
        assertThat(data.path(amountField).decimalValue()).isEqualByComparingTo(base);
    }
}
