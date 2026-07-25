package com.tuowei.erp.finance.payable;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.payable.service.PayableQueryService;
import com.tuowei.erp.finance.payable.web.PayablePageQuery;
import com.tuowei.erp.finance.payable.web.PayableResponse;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayableQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9001L,
            7L,
            9L,
            LocalDateTime.of(2026, 7, 23, 10, 0)
    );
    private static final LocalDateTime CREATED_TIME = LocalDateTime.of(2026, 6, 18, 9, 0);
    private static final LocalDateTime UPDATED_TIME = LocalDateTime.of(2026, 6, 19, 10, 30);

    @Mock
    private PayableMapper payableMapper;

    @Mock
    private SupplierMapper supplierMapper;

    @Mock
    private FinanceSettlementScopeSupport financeSettlementScopeSupport;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    private PayableQueryService service;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PayableEntity.class);
        initTableInfo(SupplierEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new PayableQueryService(
                payableMapper,
                supplierMapper,
                financeSettlementScopeSupport,
                auditMetadataFactory
        );
    }

    @Test
    void listBatchLoadsTenantScopedNamesAndPreservesFinanceContract() {
        PayableEntity first = payable(2001L, 6001L, "AP-2026-001");
        PayableEntity second = payable(2002L, 6002L, "AP-2026-002");
        stubScopedList(List.of(first, second));
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(supplierMapper.selectList(any())).thenReturn(List.of(
                supplier(6001L, AUDIT.companyId(), AUDIT.accountBookId(), "当前账套供应商"),
                supplier(6002L, AUDIT.companyId(), 99L, "其他账套供应商")
        ));

        PayablePageQuery query = new PayablePageQuery();
        query.setPayableNo("  AP-2026  ");
        PageResponse<PayableResponse> response = service.list(query);

        assertThat(response.records()).hasSize(2);
        assertThat(response.records().get(0).supplierName()).isEqualTo("当前账套供应商");
        assertThat(response.records().get(1).supplierName()).isNull();
        assertThat(response.records().get(0).status()).isEqualTo("PARTIALLY_SETTLED");
        assertThat(response.records().get(0).createdTime()).isEqualTo(CREATED_TIME);
        assertThat(response.records().get(0).updatedTime()).isEqualTo(UPDATED_TIME);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PayableEntity>> payableQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(payableMapper).selectPage(any(), payableQueryCaptor.capture());
        assertThat(payableQueryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("payable_no")
                .contains("like");
        assertThat(payableQueryCaptor.getValue().getParamNameValuePairs().values())
                .contains("%AP-2026%")
                .doesNotContain("%  AP-2026  %");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SupplierEntity>> supplierQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(supplierMapper, times(1)).selectList(supplierQueryCaptor.capture());
        assertMasterdataScope(supplierQueryCaptor.getValue());
    }

    @Test
    void detailLoadsSupplierNameOnlyAfterScopeCheck() {
        PayableEntity entity = payable(2003L, 6003L, "AP-2026-003");
        when(payableMapper.selectById(2003L)).thenReturn(entity);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(supplierMapper.selectList(any())).thenReturn(List.of(
                supplier(6003L, AUDIT.companyId(), AUDIT.accountBookId(), "详情供应商")
        ));

        PayableResponse response = service.detail(2003L);

        verify(financeSettlementScopeSupport).assertCanViewPayable(entity);
        assertThat(response.supplierName()).isEqualTo("详情供应商");
        assertThat(response.status()).isEqualTo("PARTIALLY_SETTLED");
        assertThat(response.createdTime()).isEqualTo(CREATED_TIME);
        assertThat(response.updatedTime()).isEqualTo(UPDATED_TIME);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SupplierEntity>> supplierQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(supplierMapper).selectList(supplierQueryCaptor.capture());
        assertMasterdataScope(supplierQueryCaptor.getValue());
    }

    @Test
    void deniedDetailDoesNotLoadSupplierName() {
        PayableEntity entity = payable(2004L, 6004L, "AP-2026-004");
        when(payableMapper.selectById(2004L)).thenReturn(entity);
        doThrow(new AccessDeniedException("denied"))
                .when(financeSettlementScopeSupport).assertCanViewPayable(entity);

        assertThatThrownBy(() -> service.detail(2004L)).isInstanceOf(AccessDeniedException.class);

        verify(supplierMapper, never()).selectList(any());
        verify(auditMetadataFactory, never()).current();
    }

    @Test
    void blankPayableNumberDoesNotAddLikeCondition() {
        stubScopedList(List.of());
        PayablePageQuery query = new PayablePageQuery();
        query.setPayableNo("   ");

        service.list(query);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PayableEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(payableMapper).selectPage(any(), queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .doesNotContain("payable_no");
        verify(supplierMapper, never()).selectList(any());
        verify(auditMetadataFactory, never()).current();
    }

    private void stubScopedList(List<PayableEntity> records) {
        when(financeSettlementScopeSupport.applyPayableScope(any()))
                .thenAnswer(invocation -> invocation.getArgument(0, LambdaQueryWrapper.class));
        when(payableMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<PayableEntity> page = invocation.getArgument(0);
            page.setRecords(records);
            page.setTotal(records.size());
            return page;
        });
    }

    private void assertMasterdataScope(LambdaQueryWrapper<SupplierEntity> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag")
                .contains("id");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), 0);
    }

    private PayableEntity payable(Long id, Long supplierId, String payableNo) {
        PayableEntity entity = new PayableEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setPayableNo(payableNo);
        entity.setSupplierId(supplierId);
        entity.setBizDate(LocalDate.of(2026, 6, 18));
        entity.setSourceType("PURCHASE_RECEIPT");
        entity.setSourceId(8101L);
        entity.setSourceNo("PR-2026-001");
        entity.setDirection("INCREASE");
        entity.setOriginalAmount(new BigDecimal("200.00"));
        entity.setSettledAmount(new BigDecimal("80.00"));
        entity.setStatus("PARTIALLY_SETTLED");
        entity.setDeletedFlag(0);
        entity.setCreatedTime(CREATED_TIME);
        entity.setUpdatedTime(UPDATED_TIME);
        return entity;
    }

    private SupplierEntity supplier(Long id, Long companyId, Long accountBookId, String name) {
        SupplierEntity entity = new SupplierEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setSupplierName(name);
        entity.setDeletedFlag(0);
        return entity;
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
