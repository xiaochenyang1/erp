package com.tuowei.erp.inventory.stock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.inventory.stock.service.LotGenealogyCounterpartyResolver;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnMapper;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LotGenealogyCounterpartyResolverTest {

    @Mock private PurchaseReceiptMapper receiptMapper;
    @Mock private PurchaseOrderMapper purchaseOrderMapper;
    @Mock private SupplierMapper supplierMapper;
    @Mock private PurchaseReturnMapper purchaseReturnMapper;
    @Mock private SalesDeliveryMapper deliveryMapper;
    @Mock private SalesOrderMapper salesOrderMapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private SalesReturnMapper salesReturnMapper;

    private LotGenealogyCounterpartyResolver resolver;

    @BeforeAll
    static void initTableInfo() {
        init(PurchaseReceiptEntity.class);
        init(PurchaseReturnEntity.class);
        init(PurchaseOrderEntity.class);
        init(SupplierEntity.class);
        init(SalesDeliveryEntity.class);
        init(SalesReturnEntity.class);
        init(SalesOrderEntity.class);
        init(CustomerEntity.class);
    }

    @BeforeEach
    void setUp() {
        resolver = new LotGenealogyCounterpartyResolver(
                receiptMapper, purchaseOrderMapper, supplierMapper, purchaseReturnMapper,
                deliveryMapper, salesOrderMapper, customerMapper, salesReturnMapper);
    }

    @Test
    void resolvesReceiptDeliveryAndTheirReturns() {
        PurchaseReceiptEntity directReceipt = receipt(11L, "REC-1", 21L);
        PurchaseReceiptEntity returnedReceipt = receipt(12L, "REC-2", 22L);
        PurchaseReturnEntity purchaseReturn = new PurchaseReturnEntity();
        purchaseReturn.setReturnNo("PRT-1");
        purchaseReturn.setReceiptId(12L);
        when(receiptMapper.selectList(any()))
                .thenReturn(List.of(directReceipt))
                .thenReturn(List.of(returnedReceipt));
        when(purchaseReturnMapper.selectList(any())).thenReturn(List.of(purchaseReturn));
        when(purchaseOrderMapper.selectList(any())).thenReturn(List.of(
                purchaseOrder(21L, "PO-1", 31L), purchaseOrder(22L, "PO-2", 32L)));
        when(supplierMapper.selectList(any())).thenReturn(List.of(
                supplier(31L, "SUP-1", "供应商一"), supplier(32L, "SUP-2", "供应商二")));

        SalesDeliveryEntity directDelivery = delivery(41L, "DEL-1", 51L);
        SalesDeliveryEntity returnedDelivery = delivery(42L, "DEL-2", 52L);
        SalesReturnEntity salesReturn = new SalesReturnEntity();
        salesReturn.setReturnNo("SRT-1");
        salesReturn.setDeliveryId(42L);
        when(deliveryMapper.selectList(any()))
                .thenReturn(List.of(directDelivery))
                .thenReturn(List.of(returnedDelivery));
        when(salesReturnMapper.selectList(any())).thenReturn(List.of(salesReturn));
        when(salesOrderMapper.selectList(any())).thenReturn(List.of(
                salesOrder(51L, "SO-1", 61L), salesOrder(52L, "SO-2", 62L)));
        when(customerMapper.selectList(any())).thenReturn(List.of(
                customer(61L, "CUS-1", "客户一"), customer(62L, "CUS-2", "客户二")));

        var index = resolver.resolve(
                Set.of("REC-1", "PRT-1"), Set.of("DEL-1", "SRT-1"), 101L, 202L);

        assertThat(index.supplierFor("REC-1")).satisfies(ref -> {
            assertThat(ref.name()).isEqualTo("供应商一");
            assertThat(ref.documentNo()).isEqualTo("PO-1");
        });
        assertThat(index.supplierFor("PRT-1")).satisfies(ref -> {
            assertThat(ref.name()).isEqualTo("供应商二");
            assertThat(ref.documentNo()).isEqualTo("PO-2");
        });
        assertThat(index.customerFor("DEL-1")).satisfies(ref -> {
            assertThat(ref.name()).isEqualTo("客户一");
            assertThat(ref.documentNo()).isEqualTo("SO-1");
        });
        assertThat(index.customerFor("SRT-1")).satisfies(ref -> {
            assertThat(ref.name()).isEqualTo("客户二");
            assertThat(ref.documentNo()).isEqualTo("SO-2");
        });
    }

    private static void init(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName()), entityClass);
        }
    }

    private static PurchaseReceiptEntity receipt(Long id, String no, Long orderId) {
        PurchaseReceiptEntity entity = new PurchaseReceiptEntity();
        entity.setId(id);
        entity.setReceiptNo(no);
        entity.setOrderId(orderId);
        return entity;
    }

    private static PurchaseOrderEntity purchaseOrder(Long id, String no, Long supplierId) {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(id);
        entity.setOrderNo(no);
        entity.setSupplierId(supplierId);
        return entity;
    }

    private static SupplierEntity supplier(Long id, String code, String name) {
        SupplierEntity entity = new SupplierEntity();
        entity.setId(id);
        entity.setSupplierCode(code);
        entity.setSupplierName(name);
        return entity;
    }

    private static SalesDeliveryEntity delivery(Long id, String no, Long orderId) {
        SalesDeliveryEntity entity = new SalesDeliveryEntity();
        entity.setId(id);
        entity.setDeliveryNo(no);
        entity.setOrderId(orderId);
        return entity;
    }

    private static SalesOrderEntity salesOrder(Long id, String no, Long customerId) {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setId(id);
        entity.setOrderNo(no);
        entity.setCustomerId(customerId);
        return entity;
    }

    private static CustomerEntity customer(Long id, String code, String name) {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(id);
        entity.setCustomerCode(code);
        entity.setCustomerName(name);
        return entity;
    }
}
