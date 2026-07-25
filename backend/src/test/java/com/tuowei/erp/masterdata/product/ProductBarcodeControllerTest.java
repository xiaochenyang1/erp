package com.tuowei.erp.masterdata.product;

import com.tuowei.erp.masterdata.product.service.ProductService;
import com.tuowei.erp.masterdata.product.web.ProductResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductBarcodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    @WithErpUser(authorities = "system:user:view")
    void barcodeLookupRequiresProductViewPermission() throws Exception {
        mockMvc.perform(get("/api/masterdata/products/by-barcode")
                        .param("barcode", "6901234567890"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));

        verifyNoInteractions(productService);
    }

    @Test
    @WithErpUser(authorities = "masterdata:product:view")
    void barcodeLookupReturnsExactProductContract() throws Exception {
        when(productService.getByBarcode("6901234567890")).thenReturn(product());

        mockMvc.perform(get("/api/masterdata/products/by-barcode")
                        .param("barcode", "6901234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("904"))
                .andExpect(jsonPath("$.data.productCode").value("BARCODE-P904"))
                .andExpect(jsonPath("$.data.barcode").value("6901234567890"));

        verify(productService).getByBarcode("6901234567890");
    }

    private ProductResponse product() {
        return new ProductResponse(
                904L,
                "BARCODE-P904",
                "条码商品",
                "STANDARD",
                "条码测试",
                "规格",
                "件",
                new BigDecimal("10.00"),
                new BigDecimal("12.00"),
                new BigDecimal("13.0000"),
                "ACTIVE",
                false,
                false,
                false,
                false,
                "barcode test",
                "6901234567890"
        );
    }
}
