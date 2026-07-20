package com.tuowei.erp.purchase;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentAllocationMapper;
import com.tuowei.erp.finance.payment.mapper.PaymentMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderNumberService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineRequest;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseOrderServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9932L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 21, 30)
    );
    private static final CurrentUser CURRENT_USER = new CurrentUser(
            AUDIT.userId(),
            AUDIT.companyId(),
            AUDIT.accountBookId(),
            11L,
            12L,
            "purchase_order_scope_user",
            "采购订单用户"
    );
    private static final ErpPrincipal PRINCIPAL = new ErpPrincipal(
            CURRENT_USER.userId(),
            CURRENT_USER.companyId(),
            CURRENT_USER.accountBookId(),
            CURRENT_USER.deptId(),
            CURRENT_USER.postId(),
            CURRENT_USER.username(),
            CURRENT_USER.realName(),
            "N/A",
            Set.of(),
            DataScopeSnapshot.all()
    );
    private static final Long SUPPLIER_ID = 4101L;
    private static final Long PRODUCT_ID = 4201L;

    private final PurchaseOrderMapper purchaseOrderMapper = mock(PurchaseOrderMapper.class);
    private final PurchaseOrderLineMapper purchaseOrderLineMapper = mock(PurchaseOrderLineMapper.class);
    private final SupplierMapper supplierMapper = mock(SupplierMapper.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final ProductValidator productValidator = mock(ProductValidator.class);
    private final PurchaseOrderNumberService purchaseOrderNumberService = mock(PurchaseOrderNumberService.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
    private final DataScopeService dataScopeService = mock(DataScopeService.class);
    private final ScopedUserResolver scopedUserResolver = mock(ScopedUserResolver.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final PurchaseReceiptMapper purchaseReceiptMapper = mock(PurchaseReceiptMapper.class);
    private final PurchaseReturnMapper purchaseReturnMapper = mock(PurchaseReturnMapper.class);
    private final PayableMapper payableMapper = mock(PayableMapper.class);
    private final PaymentAllocationMapper paymentAllocationMapper = mock(PaymentAllocationMapper.class);
    private final PaymentMapper paymentMapper = mock(PaymentMapper.class);
    private final VoucherMapper voucherMapper = mock(VoucherMapper.class);
    private final WorkflowService workflowService = mock(WorkflowService.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseOrderLineEntity.class);
    }

    @Test
    void getByIdScopesLineQueryByCompanyAndAccountBook() {
        stubCurrentUser();
        when(purchaseOrderMapper.selectById(4301L)).thenReturn(order());
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine()));

        service().getById(4301L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseOrderLineEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseOrderLineMapper).selectList(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void createRejectsSupplierFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(supplierMapper.selectById(SUPPLIER_ID))
                .thenReturn(activeSupplier(SUPPLIER_ID, AUDIT.companyId(), 999L));
        when(productMapper.selectById(PRODUCT_ID))
                .thenReturn(activeProduct(PRODUCT_ID, AUDIT.companyId(), AUDIT.accountBookId()));
        stubOrderInsert();

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("供应商不存在或已停用");
    }

    @Test
    void createRejectsProductFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(supplierMapper.selectById(SUPPLIER_ID))
                .thenReturn(activeSupplier(SUPPLIER_ID, AUDIT.companyId(), AUDIT.accountBookId()));
        when(productValidator.requireProducts(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("商品不存在或已停用"));
        stubOrderInsert();

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在或已停用");
    }

    @Test
    void unapproveApprovedUnreceivedOrderReturnsToDraft() {
        stubCurrentUser();
        stubAudit();
        PurchaseOrderEntity approved = order();
        approved.setStatus("APPROVED");
        approved.setApprovalStatus("APPROVED");
        approved.setReceiptStatus("NOT_RECEIVED");
        when(purchaseOrderMapper.selectById(4301L)).thenReturn(approved);
        when(purchaseOrderMapper.updateById(any(PurchaseOrderEntity.class))).thenReturn(1);
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine()));

        service().unapprove(4301L);

        assertThat(approved.getStatus()).isEqualTo("DRAFT");
        assertThat(approved.getApprovalStatus()).isEqualTo("NOT_SUBMITTED");
        verify(purchaseOrderMapper).updateById(approved);
    }

    @Test
    void unapproveRejectsReceivedOrderWithoutUpdating() {
        stubCurrentUser();
        PurchaseOrderEntity received = order();
        received.setStatus("APPROVED");
        received.setApprovalStatus("APPROVED");
        received.setReceiptStatus("PARTIAL_RECEIVED");
        when(purchaseOrderMapper.selectById(4301L)).thenReturn(received);

        assertThatThrownBy(() -> service().unapprove(4301L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("已入库采购订单不允许反审核");
        verify(purchaseOrderMapper, never()).updateById(any(PurchaseOrderEntity.class));
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private void stubCurrentUser() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
    }

    private void stubOrderInsert() {
        when(purchaseOrderMapper.insert(any(PurchaseOrderEntity.class))).thenAnswer(invocation -> {
            PurchaseOrderEntity order = invocation.getArgument(0);
            order.setId(4301L);
            return 1;
        });
    }

    private PurchaseOrderService service() {
        return new PurchaseOrderService(
                purchaseOrderMapper,
                purchaseOrderLineMapper,
                supplierMapper,
                productMapper,
                productValidator,
                purchaseOrderNumberService,
                auditMetadataFactory,
                currentUserContext,
                dataScopeService,
                scopedUserResolver,
                userMapper,
                purchaseReceiptMapper,
                purchaseReturnMapper,
                payableMapper,
                paymentAllocationMapper,
                paymentMapper,
                voucherMapper,
                workflowService
        );
    }

    private PurchaseOrderCreateRequest createRequest() {
        return new PurchaseOrderCreateRequest(
                SUPPLIER_ID,
                LocalDate.of(2026, 6, 8),
                LocalDate.of(2026, 6, 9),
                "tenant boundary",
                List.of(new PurchaseOrderLineRequest(
                        PRODUCT_ID,
                        BigDecimal.ONE,
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        "line"
                ))
        );
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    private PurchaseOrderEntity order() {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(4301L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setOrderNo("PO-4301");
        entity.setSupplierId(SUPPLIER_ID);
        entity.setOrderDate(LocalDate.of(2026, 6, 8));
        entity.setStatus("DRAFT");
        entity.setApprovalStatus("DRAFT");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseOrderLineEntity orderLine() {
        PurchaseOrderLineEntity entity = new PurchaseOrderLineEntity();
        entity.setId(4401L);
        entity.setOrderId(4301L);
        entity.setLineNo(1);
        entity.setProductId(PRODUCT_ID);
        entity.setQty(BigDecimal.ONE);
        entity.setPrice(BigDecimal.TEN);
        entity.setTaxRate(BigDecimal.ZERO);
        entity.setAmount(BigDecimal.TEN);
        entity.setTaxAmount(BigDecimal.ZERO);
        entity.setReceivedQty(BigDecimal.ZERO);
        return entity;
    }

    private SupplierEntity activeSupplier(Long id, Long companyId, Long accountBookId) {
        SupplierEntity supplier = new SupplierEntity();
        supplier.setId(id);
        supplier.setCompanyId(companyId);
        supplier.setAccountBookId(accountBookId);
        supplier.setSupplierName("tenant supplier");
        supplier.setStatus("ACTIVE");
        supplier.setDeletedFlag(0);
        return supplier;
    }

    private ProductEntity activeProduct(Long id, Long companyId, Long accountBookId) {
        ProductEntity product = new ProductEntity();
        product.setId(id);
        product.setCompanyId(companyId);
        product.setAccountBookId(accountBookId);
        product.setStatus("ACTIVE");
        product.setDeletedFlag(0);
        return product;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
