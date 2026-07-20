package com.tuowei.erp.masterdata;

import com.tuowei.erp.masterdata.customer.service.CustomerService;
import com.tuowei.erp.masterdata.customer.web.CustomerPageQuery;
import com.tuowei.erp.masterdata.product.service.ProductService;
import com.tuowei.erp.masterdata.product.web.ProductPageQuery;
import com.tuowei.erp.masterdata.supplier.service.SupplierService;
import com.tuowei.erp.masterdata.supplier.web.SupplierPageQuery;
import com.tuowei.erp.masterdata.warehouse.service.WarehouseService;
import com.tuowei.erp.masterdata.warehouse.web.WarehousePageQuery;
import com.tuowei.erp.testsupport.ControlledStreamingResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MasterdataExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private SupplierService supplierService;

    @MockitoBean
    private WarehouseService warehouseService;

    @Test
    @WithErpUser(authorities = "system:user:view")
    void productExportRequiresProductViewPermission() throws Exception {
        mockMvc.perform(get("/api/masterdata/products/export")
                        .param("keyword", "P001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(productService);
    }

    @Test
    @WithErpUser(authorities = "masterdata:product:view")
    void productExportBindsQueryAndStreamsCsv() throws Exception {
        ControlledStreamingResponse response = ControlledStreamingResponse.csv("productCode,productName\r\nP001,螺栓\r\n");
        when(productService.exportProducts(any(ProductPageQuery.class)))
                .thenReturn(response.body());

        MvcResult result = mockMvc.perform(get("/api/masterdata/products/export")
                        .param("keyword", "P001")
                        .param("status", "ACTIVE")
                        .param("categoryName", "标准件"))
                .andExpect(request().asyncStarted())
                .andReturn();

        response.release();
        MvcResult dispatched = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string("productCode,productName\r\nP001,螺栓\r\n"))
                .andReturn();

        assertCsvAttachment(dispatched, "products.csv");

        ArgumentCaptor<ProductPageQuery> captor = ArgumentCaptor.forClass(ProductPageQuery.class);
        verify(productService).exportProducts(captor.capture());
        assertThat(captor.getValue().getKeyword()).isEqualTo("P001");
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().getCategoryName()).isEqualTo("标准件");
    }

    @Test
    @WithErpUser(authorities = "masterdata:customer:view")
    void customerExportBindsQueryAndStreamsCsv() throws Exception {
        ControlledStreamingResponse response = ControlledStreamingResponse.csv("customerCode,customerName\r\nC001,东北客户\r\n");
        when(customerService.exportCustomers(any(CustomerPageQuery.class)))
                .thenReturn(response.body());

        MvcResult result = mockMvc.perform(get("/api/masterdata/customers/export")
                        .param("keyword", "C001")
                        .param("status", "ACTIVE")
                        .param("settlementMethod", "MONTHLY"))
                .andExpect(request().asyncStarted())
                .andReturn();

        response.release();
        MvcResult dispatched = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string("customerCode,customerName\r\nC001,东北客户\r\n"))
                .andReturn();

        assertCsvAttachment(dispatched, "customers.csv");

        ArgumentCaptor<CustomerPageQuery> captor = ArgumentCaptor.forClass(CustomerPageQuery.class);
        verify(customerService).exportCustomers(captor.capture());
        assertThat(captor.getValue().getKeyword()).isEqualTo("C001");
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().getSettlementMethod()).isEqualTo("MONTHLY");
    }

    @Test
    @WithErpUser(authorities = "masterdata:supplier:view")
    void supplierExportBindsQueryAndStreamsCsv() throws Exception {
        ControlledStreamingResponse response = ControlledStreamingResponse.csv("supplierCode,supplierName\r\nS001,钢材供应商\r\n");
        when(supplierService.exportSuppliers(any(SupplierPageQuery.class)))
                .thenReturn(response.body());

        MvcResult result = mockMvc.perform(get("/api/masterdata/suppliers/export")
                        .param("keyword", "S001")
                        .param("status", "ACTIVE")
                        .param("settlementMethod", "MONTHLY"))
                .andExpect(request().asyncStarted())
                .andReturn();

        response.release();
        MvcResult dispatched = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string("supplierCode,supplierName\r\nS001,钢材供应商\r\n"))
                .andReturn();

        assertCsvAttachment(dispatched, "suppliers.csv");

        ArgumentCaptor<SupplierPageQuery> captor = ArgumentCaptor.forClass(SupplierPageQuery.class);
        verify(supplierService).exportSuppliers(captor.capture());
        assertThat(captor.getValue().getKeyword()).isEqualTo("S001");
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().getSettlementMethod()).isEqualTo("MONTHLY");
    }

    @Test
    @WithErpUser(authorities = "masterdata:warehouse:view")
    void warehouseExportBindsQueryAndStreamsCsv() throws Exception {
        ControlledStreamingResponse response = ControlledStreamingResponse.csv("warehouseCode,warehouseName\r\nW001,成品仓\r\n");
        when(warehouseService.exportWarehouses(any(WarehousePageQuery.class)))
                .thenReturn(response.body());

        MvcResult result = mockMvc.perform(get("/api/masterdata/warehouses/export")
                        .param("keyword", "W001")
                        .param("status", "ACTIVE")
                        .param("deptId", "6201")
                        .param("managerUserId", "6301"))
                .andExpect(request().asyncStarted())
                .andReturn();

        response.release();
        MvcResult dispatched = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string("warehouseCode,warehouseName\r\nW001,成品仓\r\n"))
                .andReturn();

        assertCsvAttachment(dispatched, "warehouses.csv");

        ArgumentCaptor<WarehousePageQuery> captor = ArgumentCaptor.forClass(WarehousePageQuery.class);
        verify(warehouseService).exportWarehouses(captor.capture());
        assertThat(captor.getValue().getKeyword()).isEqualTo("W001");
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().getDeptId()).isEqualTo(6201L);
        assertThat(captor.getValue().getManagerUserId()).isEqualTo(6301L);
    }

    private void assertCsvAttachment(MvcResult result, String filename) {
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;")
                .contains("filename*=UTF-8''" + filename)
                .doesNotContain("filename=" + filename);
    }
}
