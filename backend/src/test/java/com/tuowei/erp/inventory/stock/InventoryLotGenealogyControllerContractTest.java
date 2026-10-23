package com.tuowei.erp.inventory.stock;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.inventory.stock.controller.InventoryStockQueryController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryLotGenealogyControllerContractTest {

    @Test
    void exposesDedicatedPermissionAndEndpoint() throws Exception {
        Method method = Arrays.stream(InventoryStockQueryController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("genealogy"))
                .findFirst()
                .orElseThrow();

        assertThat(method.getAnnotation(GetMapping.class).value())
                .containsExactly("/lots/genealogy");
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo(PermissionCodes.HAS_INVENTORY_LOT_GENEALOGY);
        assertThat(PermissionCodes.allPermissions())
                .contains(PermissionCodes.INVENTORY_LOT_GENEALOGY);
    }
}
